// مشترك بين intention.js و webhook.js — Vercel Serverless (Node 18+)
// متغيرات البيئة المطلوبة في Vercel > Project > Settings > Environment Variables:
//   PAYMOB_SECRET_KEY        (Paymob dashboard > Settings > Account info > Secret key)
//   PAYMOB_PUBLIC_KEY        (Public key)
//   PAYMOB_HMAC_SECRET       (HMAC)
//   PAYMOB_CARD_INTEGRATION_ID     (Integration ID للكارت)
//   PAYMOB_WALLET_INTEGRATION_ID   (Integration ID للمحافظ - Mobile Wallets)
//   FIREBASE_SERVICE_ACCOUNT  (محتوى ملف JSON الخاص بـ service account كسطر واحد)
import admin from "firebase-admin";

export const PLANS = {
  basic: { name: "المبتدئ", price: 50, coins: 100, bonus: 0 },
  pro:   { name: "المحترف", price: 100, coins: 250, bonus: 50 },
  star:  { name: "النجم",   price: 200, coins: 600, bonus: 0 },
};

export function firebase() {
  if (!admin.apps.length) {
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) });
  }
  return { auth: admin.auth(), db: admin.firestore(), FieldValue: admin.firestore.FieldValue };
}
