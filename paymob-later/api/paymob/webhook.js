// POST /api/paymob/webhook  — Paymob "Transaction processed callback"
// بيتأكد من HMAC، ولو الدفع نجح بيضيف العملات أوتوماتيك (نفس منطق موافقة الأدمن)
import crypto from "node:crypto";
import { firebase } from "./_lib.js";

const HMAC_FIELDS = [
  "amount_cents", "created_at", "currency", "error_occured", "has_parent_transaction", "id",
  "integration_id", "is_3d_secure", "is_auth", "is_capture", "is_refunded", "is_standalone_payment",
  "is_voided", "order.id", "owner", "pending", "source_data.pan", "source_data.sub_type",
  "source_data.type", "success",
];
const pick = (obj, path) => path.split(".").reduce((o, k) => (o == null ? o : o[k]), obj);

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(200).send("ok");
  const obj = req.body?.obj;
  if (!obj) return res.status(400).send("no obj");

  const concat = HMAC_FIELDS.map((f) => String(pick(obj, f) ?? "")).join("");
  const calc = crypto.createHmac("sha512", process.env.PAYMOB_HMAC_SECRET).update(concat).digest("hex");
  if (calc !== String(req.query.hmac || "")) return res.status(401).send("bad hmac");

  const { db, FieldValue } = firebase();
  const subId = obj.order?.merchant_order_id || obj.payment_key_claims?.extra?.special_reference || obj.special_reference;
  if (!subId) return res.status(200).send("no reference");
  const subRef = db.collection("subscriptions").doc(String(subId));

  await db.runTransaction(async (tx) => {
    const s = await tx.get(subRef);
    if (!s.exists || s.data().status !== "pending") return;
    const d = s.data();
    if (obj.success !== true || obj.pending === true) {
      tx.update(subRef, { status: "rejected", note: "Paymob: payment failed", paymobTxId: obj.id, reviewedAt: FieldValue.serverTimestamp() });
      return;
    }
    if (Number(obj.amount_cents) !== d.priceEgp * 100) {
      tx.update(subRef, { note: "Paymob amount mismatch — راجع يدوي", paymobTxId: obj.id });
      return;
    }
    const uRef = db.collection("users").doc(d.uid);
    const u = await tx.get(uRef);
    const add = d.coins + (d.bonus || 0);
    const before = Number(u.data()?.coins || 0);
    tx.set(uRef, { coins: before + add, plan: d.planId, planSince: FieldValue.serverTimestamp() }, { merge: true });
    tx.update(subRef, { status: "approved", paymobTxId: obj.id, reviewedAt: FieldValue.serverTimestamp(), reviewedBy: "paymob" });
    tx.set(db.collection("coins").doc(), {
      uid: d.uid, delta: add, balanceAfter: before + add, reason: "purchase", note: `${d.planName} - Paymob`,
      subscriptionId: subRef.id, by: "paymob", createdAt: FieldValue.serverTimestamp(),
    });
  });
  return res.status(200).send("ok");
}
