// POST /api/paymob/intention   body: {planId, method: "card"|"wallet"}   header: Authorization: Bearer <Firebase ID token>
// بيعمل طلب subscriptions بحالة pending (method=paymob) وبيرجع checkoutUrl لصفحة Paymob Unified Checkout
import { PLANS, firebase } from "./_lib.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  try {
    const { auth, db, FieldValue } = firebase();
    const token = (req.headers.authorization || "").replace("Bearer ", "");
    const user = await auth.verifyIdToken(token);
    const { planId, method = "card" } = req.body || {};
    const plan = PLANS[planId];
    if (!plan) return res.status(400).json({ error: "bad plan" });

    const userDoc = await db.collection("users").doc(user.uid).get();
    if (userDoc.data()?.banned) return res.status(403).json({ error: "banned" });

    const subRef = await db.collection("subscriptions").add({
      uid: user.uid, userName: user.name || "", userEmail: user.email || "", userPhone: user.phone_number || "",
      planId, planName: plan.name, priceEgp: plan.price, coins: plan.coins, bonus: plan.bonus,
      method: "paymob", paymobMethod: method, status: "pending", createdAt: FieldValue.serverTimestamp(),
    });

    const integration = Number(method === "wallet" ? process.env.PAYMOB_WALLET_INTEGRATION_ID : process.env.PAYMOB_CARD_INTEGRATION_ID);
    const base = `https://${req.headers.host}`;
    const r = await fetch("https://accept.paymob.com/v1/intention/", {
      method: "POST",
      headers: { Authorization: `Token ${process.env.PAYMOB_SECRET_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        amount: plan.price * 100,
        currency: "EGP",
        payment_methods: [integration],
        items: [{ name: `باقة ${plan.name}`, amount: plan.price * 100, quantity: 1 }],
        billing_data: {
          first_name: (user.name || "Customer").split(" ")[0], last_name: (user.name || "User").split(" ").slice(1).join(" ") || "User",
          email: user.email || "customer@egypt-shortdrama.top", phone_number: user.phone_number || "+201000000000",
        },
        special_reference: subRef.id,                    // بيرجع في الـ webhook علشان نعرف الطلب
        notification_url: `${base}/api/paymob/webhook`,
        redirection_url: `${base}/?payment=done`,
      }),
    });
    const data = await r.json();
    if (!r.ok) return res.status(502).json({ error: "paymob", details: data });
    await subRef.update({ paymobIntentionId: data.id || null });
    const checkoutUrl = `https://accept.paymob.com/unifiedcheckout/?publicKey=${process.env.PAYMOB_PUBLIC_KEY}&clientSecret=${data.client_secret}`;
    return res.status(200).json({ checkoutUrl, subscriptionId: subRef.id });
  } catch (e) {
    return res.status(500).json({ error: e.message });
  }
}
