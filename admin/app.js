// لوحة تحكم Egypt Short Drama — Firebase (Auth + Firestore) بدون سيرفر.
// الصلاحيات: لازم المستخدم يكون له مستند في admins/{uid}. الأمان الحقيقي في firebase/firestore.rules
import { initializeApp } from "https://www.gstatic.com/firebasejs/12.4.0/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithPopup, GoogleAuthProvider,
  signInWithEmailAndPassword, signOut,
} from "https://www.gstatic.com/firebasejs/12.4.0/firebase-auth.js";
import {
  getFirestore, collection, doc, getDoc, onSnapshot, query, where, orderBy, limit,
  runTransaction, updateDoc, addDoc, deleteDoc, setDoc, serverTimestamp,
  getCountFromServer, getAggregateFromServer, sum,
} from "https://www.gstatic.com/firebasejs/12.4.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const PLANS = {
  basic: { name: "المبتدئ", price: 50, coins: 100, bonus: 0 },
  pro:   { name: "المحترف", price: 100, coins: 250, bonus: 50 },
  star:  { name: "النجم",   price: 200, coins: 600, bonus: 0 },
};
const METHODS = { instapay: "InstaPay", vodafone: "فودافون كاش", paymob: "Paymob" };

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmtDate = (ts) => ts?.toDate ? ts.toDate().toLocaleString("ar-EG", { dateStyle: "short", timeStyle: "short" }) : "—";
function toast(msg) { const t = $("toast"); t.textContent = msg; t.style.display = "block"; clearTimeout(toast._t); toast._t = setTimeout(() => (t.style.display = "none"), 2600); }
function show(view) { ["loginView", "deniedView", "appView"].forEach((v) => $(v).classList.toggle("hidden", v !== view)); }

let unsubs = [];
let usersCache = [];
let editingVideo = null;

// ---------------- Auth ----------------
$("googleBtn").onclick = () => signInWithPopup(auth, new GoogleAuthProvider()).catch((e) => ($("loginMsg").textContent = e.message));
$("emailBtn").onclick = () => signInWithEmailAndPassword(auth, $("email").value.trim(), $("password").value).catch((e) => ($("loginMsg").textContent = e.message));
$("logoutBtn").onclick = () => signOut(auth);
$("copyUid").onclick = () => navigator.clipboard.writeText($("myUid").textContent).then(() => toast("اتنسخ"));

onAuthStateChanged(auth, async (user) => {
  unsubs.forEach((u) => u()); unsubs = [];
  $("logoutBtn").classList.toggle("hidden", !user);
  $("who").textContent = user ? (user.email || user.phoneNumber || user.uid) : "";
  if (!user) return show("loginView");
  const adminDoc = await getDoc(doc(db, "admins", user.uid)).catch(() => null);
  if (!adminDoc?.exists()) { $("myUid").textContent = user.uid; return show("deniedView"); }
  show("appView");
  start();
});

// ---------------- Tabs ----------------
document.querySelectorAll("nav.tabs button").forEach((b) => (b.onclick = () => {
  document.querySelectorAll("nav.tabs button").forEach((x) => x.classList.toggle("on", x === b));
  document.querySelectorAll("[id^=tab-]").forEach((s) => s.classList.toggle("hidden", s.id !== "tab-" + b.dataset.tab));
}));

function start() {
  listenPending();
  listenUsers();
  listenHistory();
  listenCoins();
  listenVideos();
  refreshStats();
}

async function refreshStats() {
  // كل رقم لوحده: لو واحد فشل الباقي يظهر، والخطأ يبان لما تقف بالماوس على "!"
  const approvedQ = query(collection(db, "subscriptions"), where("status", "==", "approved"));
  const stat = (id, promise, fmt) => promise
    .then((snap) => ($(id).textContent = fmt(snap.data())))
    .catch((e) => { console.warn(id, e); $(id).textContent = "!"; $(id).title = e.message; });
  stat("sUsers", getCountFromServer(collection(db, "users")), (d) => d.count);
  stat("sApproved", getCountFromServer(approvedQ), (d) => d.count);
  stat("sRevenue", getAggregateFromServer(approvedQ, { total: sum("priceEgp") }), (d) => (d.total || 0).toLocaleString("ar-EG"));
}

// ---------------- Pending payments ----------------
function listenPending() {
  const q = query(collection(db, "subscriptions"), where("status", "==", "pending"), orderBy("createdAt", "desc"), limit(100));
  unsubs.push(onSnapshot(q, (snap) => {
    $("sPending").textContent = snap.size;
    if (snap.empty) { $("paymentsList").innerHTML = `<div class="card muted">مفيش طلبات جديدة 🎉</div>`; return; }
    $("paymentsList").innerHTML = snap.docs.map((d) => {
      const s = d.data();
      return `<div class="card"><div class="row">
        ${s.screenshot ? `<img class="shot" src="${esc(s.screenshot)}" data-full="${d.id}" alt="screenshot">` : `<div class="shot muted" style="display:grid;place-items:center">بدون صورة</div>`}
        <div class="grow">
          <div style="font-size:16px;font-weight:800">${esc(s.planName)} — <span style="color:var(--gold)">${esc(s.priceEgp)} ج</span> → ${Number(s.coins) + Number(s.bonus || 0)} عملة</div>
          <div class="muted" style="margin:4px 0 8px">${fmtDate(s.createdAt)} · <span class="pill pending">${esc(METHODS[s.method] || s.method)}</span></div>
          <div>المستخدم: <b>${esc(s.userName || "—")}</b> <span class="ltr muted">${esc(s.userEmail || s.userPhone || "")}</span></div>
          <div>حوّل من: <b class="ltr">${esc(s.senderInfo || "—")}</b></div>
          <div>رقم العملية: <b class="ltr">${esc(s.reference || "—")}</b></div>
          <div class="muted ltr" style="font-size:11px;margin-top:4px">uid: ${esc(s.uid)}</div>
          <div class="actions" style="margin-top:10px">
            <button class="ok" data-approve="${d.id}">✅ موافقة وإضافة العملات</button>
            <button class="danger" data-reject="${d.id}">رفض</button>
          </div>
        </div></div></div>`;
    }).join("");
    $("paymentsList").querySelectorAll("[data-approve]").forEach((b) => (b.onclick = () => approve(b.dataset.approve, b)));
    $("paymentsList").querySelectorAll("[data-reject]").forEach((b) => (b.onclick = () => reject(b.dataset.reject)));
    $("paymentsList").querySelectorAll("img[data-full]").forEach((img) => (img.onclick = () => { $("imgFull").src = img.src; $("imgDialog").showModal(); }));
  }, (e) => ($("paymentsList").innerHTML = `<div class="card" style="color:var(--red)">${esc(e.message)}</div>`)));
}

async function approve(id, btn) {
  if (!confirm("متأكد إن التحويل وصل؟ هتتضاف العملات للمستخدم.")) return;
  btn.disabled = true;
  try {
    await runTransaction(db, async (tx) => {
      const sRef = doc(db, "subscriptions", id);
      const s = await tx.get(sRef);
      if (!s.exists() || s.data().status !== "pending") throw new Error("الطلب اتراجع قبل كده");
      const data = s.data();
      const add = Number(data.coins) + Number(data.bonus || 0);
      const uRef = doc(db, "users", data.uid);
      const u = await tx.get(uRef);
      const before = u.exists() ? Number(u.data().coins || 0) : 0;
      tx.set(uRef, { coins: before + add, plan: data.planId, planSince: serverTimestamp() }, { merge: true });
      tx.update(sRef, { status: "approved", reviewedAt: serverTimestamp(), reviewedBy: auth.currentUser.email || auth.currentUser.uid });
      tx.set(doc(collection(db, "coins")), {
        uid: data.uid, delta: add, balanceAfter: before + add, reason: "purchase",
        note: `${data.planName} - ${METHODS[data.method] || data.method}`, subscriptionId: id,
        by: auth.currentUser.email || auth.currentUser.uid, createdAt: serverTimestamp(),
      });
    });
    toast("تمت الموافقة وإضافة العملات ✅");
    refreshStats();
  } catch (e) { btn.disabled = false; alert(e.message); }
}

async function reject(id) {
  const note = prompt("سبب الرفض (هيظهر للمستخدم):", "التحويل غير موجود");
  if (note === null) return;
  await updateDoc(doc(db, "subscriptions", id), { status: "rejected", note, reviewedAt: serverTimestamp(), reviewedBy: auth.currentUser.email || auth.currentUser.uid })
    .then(() => toast("اترفض")).catch((e) => alert(e.message));
}

// ---------------- Users ----------------
function listenUsers() {
  const q = query(collection(db, "users"), orderBy("createdAt", "desc"), limit(500));
  unsubs.push(onSnapshot(q, (snap) => { usersCache = snap.docs.map((d) => ({ id: d.id, ...d.data() })); renderUsers(); }));
}
$("userSearch").oninput = () => renderUsers();

function renderUsers() {
  const term = $("userSearch").value.trim().toLowerCase();
  const list = usersCache.filter((u) => !term || [u.name, u.email, u.phone, u.id].some((x) => String(x || "").toLowerCase().includes(term)));
  $("usersBody").innerHTML = list.map((u) => `<tr>
      <td><b>${esc(u.name || "—")}</b><div class="muted" style="font-size:11px">${fmtDate(u.createdAt)}</div></td>
      <td class="ltr">${esc(u.email || "")}<div class="muted">${esc(u.phone || "")}</div></td>
      <td><b style="color:var(--gold)">${esc(u.coins ?? 0)}</b></td>
      <td>${u.plan ? `<span class="pill approved">${esc(PLANS[u.plan]?.name || u.plan)}</span>` : "—"}</td>
      <td>${u.banned ? `<span class="pill banned">موقوف</span>` : `<span class="muted">نشط</span>`}</td>
      <td><div class="actions">
        <button data-coins="${u.id}">± عملات</button>
        <button data-plan="${u.id}">باقة</button>
        <button class="${u.banned ? "ok" : "danger"}" data-ban="${u.id}" data-state="${u.banned ? 1 : 0}">${u.banned ? "إلغاء الحظر" : "حظر"}</button>
        ${u.fcmToken ? `<button data-token="${esc(u.fcmToken)}" title="FCM token">🔔</button>` : ""}
      </div></td></tr>`).join("") || `<tr><td colspan="6" class="muted">مفيش نتائج</td></tr>`;
  $("usersBody").querySelectorAll("[data-coins]").forEach((b) => (b.onclick = () => adjustCoins(b.dataset.coins)));
  $("usersBody").querySelectorAll("[data-plan]").forEach((b) => (b.onclick = () => setPlan(b.dataset.plan)));
  $("usersBody").querySelectorAll("[data-ban]").forEach((b) => (b.onclick = () => toggleBan(b.dataset.ban, b.dataset.state === "1")));
  $("usersBody").querySelectorAll("[data-token]").forEach((b) => (b.onclick = () => navigator.clipboard.writeText(b.dataset.token).then(() => toast("FCM token اتنسخ"))));
}

async function adjustCoins(uid) {
  const val = prompt("كام عملة؟ (رقم موجب للإضافة، سالب للخصم)", "50");
  if (val === null) return;
  const delta = parseInt(val, 10);
  if (!delta) return alert("رقم غير صحيح");
  const reason = prompt("السبب:", delta > 0 ? "هدية من الإدارة" : "تصحيح رصيد") || "admin";
  try {
    await runTransaction(db, async (tx) => {
      const uRef = doc(db, "users", uid);
      const u = await tx.get(uRef);
      const before = Number(u.data()?.coins || 0);
      const after = Math.max(0, before + delta);
      tx.update(uRef, { coins: after });
      tx.set(doc(collection(db, "coins")), { uid, delta: after - before, balanceAfter: after, reason: "admin", note: reason, by: auth.currentUser.email || auth.currentUser.uid, createdAt: serverTimestamp() });
    });
    toast("الرصيد اتحدث");
  } catch (e) { alert(e.message); }
}

async function setPlan(uid) {
  const v = prompt("اكتب الباقة: basic / pro / star  (أو فاضي لإلغاء الباقة)", "pro");
  if (v === null) return;
  const plan = v.trim() || null;
  if (plan && !PLANS[plan]) return alert("باقة غير معروفة");
  await updateDoc(doc(db, "users", uid), { plan, planSince: plan ? serverTimestamp() : null }).then(() => toast("الباقة اتحدثت")).catch((e) => alert(e.message));
}

async function toggleBan(uid, banned) {
  if (!confirm(banned ? "إلغاء الحظر؟" : "حظر المستخدم ده؟ مش هيقدر يستخدم التطبيق.")) return;
  await updateDoc(doc(db, "users", uid), { banned: !banned, bannedAt: !banned ? serverTimestamp() : null }).then(() => toast("تم")).catch((e) => alert(e.message));
}

// ---------------- History & coins ----------------
const userLabel = (uid) => { const u = usersCache.find((x) => x.id === uid); return esc(u?.name || u?.email || u?.phone || uid); };

function listenHistory() {
  const q = query(collection(db, "subscriptions"), orderBy("createdAt", "desc"), limit(200));
  unsubs.push(onSnapshot(q, (snap) => {
    $("historyBody").innerHTML = snap.docs.map((d) => { const s = d.data(); return `<tr>
      <td>${fmtDate(s.createdAt)}</td><td>${esc(s.userName || s.userEmail || s.uid)}</td><td>${esc(s.planName)}</td>
      <td>${esc(s.priceEgp)} ج</td><td>${esc(METHODS[s.method] || s.method)}</td>
      <td><span class="pill ${esc(s.status)}">${{ pending: "قيد المراجعة", approved: "مفعل", rejected: "مرفوض" }[s.status] || esc(s.status)}</span></td></tr>`; }).join("")
      || `<tr><td colspan="6" class="muted">مفيش</td></tr>`;
  }));
}

function listenCoins() {
  const q = query(collection(db, "coins"), orderBy("createdAt", "desc"), limit(200));
  unsubs.push(onSnapshot(q, (snap) => {
    $("coinsBody").innerHTML = snap.docs.map((d) => { const c = d.data(); return `<tr>
      <td>${fmtDate(c.createdAt)}</td><td>${userLabel(c.uid)}</td>
      <td style="color:${c.delta >= 0 ? "var(--green)" : "var(--red)"};font-weight:800" class="ltr">${c.delta >= 0 ? "+" : ""}${esc(c.delta)}</td>
      <td>${esc(c.note || c.reason)}</td><td class="muted">${esc(c.by || "")}</td></tr>`; }).join("")
      || `<tr><td colspan="5" class="muted">مفيش</td></tr>`;
  }));
}

// ---------------- Videos ----------------
function listenVideos() {
  const q = query(collection(db, "videos"), orderBy("order", "asc"));
  unsubs.push(onSnapshot(q, (snap) => {
    $("videosBody").innerHTML = snap.docs.map((d) => { const v = d.data(); return `<tr>
      <td>${esc(v.order)}</td><td><b>${esc(v.title)}</b><div class="muted" style="font-size:12px">${esc(v.creator || "")}</div></td>
      <td class="ltr"><a style="color:var(--gold)" target="_blank" rel="noopener" href="https://youtu.be/${esc(v.youtubeId)}">${esc(v.youtubeId)}</a></td>
      <td>${v.free ? "مجانية" : esc(v.coinsPrice) + " 🪙"}</td>
      <td>${v.published ? `<span class="pill approved">منشورة</span>` : `<span class="pill">مسودة</span>`}</td>
      <td><div class="actions"><button data-edit="${d.id}">تعديل</button><button class="danger" data-del="${d.id}">حذف</button></div></td></tr>`; }).join("")
      || `<tr><td colspan="6" class="muted">مفيش فيديوهات لسه</td></tr>`;
    const byId = Object.fromEntries(snap.docs.map((d) => [d.id, d.data()]));
    $("videosBody").querySelectorAll("[data-edit]").forEach((b) => (b.onclick = () => editVideo(b.dataset.edit, byId[b.dataset.edit])));
    $("videosBody").querySelectorAll("[data-del]").forEach((b) => (b.onclick = () => confirm("حذف الحلقة؟") && deleteDoc(doc(db, "videos", b.dataset.del)).then(() => toast("اتحذفت"))));
  }));
}

function editVideo(id, v) {
  editingVideo = id;
  $("videoFormTitle").textContent = "تعديل حلقة";
  $("vTitle").value = v.title || ""; $("vYoutube").value = v.youtubeId || ""; $("vPrice").value = v.coinsPrice ?? 10;
  $("vOrder").value = v.order ?? 1; $("vCreator").value = v.creator || ""; $("vFree").value = String(!!v.free);
  $("vPublished").value = String(v.published !== false); $("vDesc").value = v.description || "";
  $("vCancel").classList.remove("hidden");
  window.scrollTo({ top: 0, behavior: "smooth" });
}
function resetVideoForm() {
  editingVideo = null; $("videoFormTitle").textContent = "إضافة حلقة";
  ["vTitle", "vYoutube", "vDesc"].forEach((k) => ($(k).value = "")); $("vCancel").classList.add("hidden");
}
$("vCancel").onclick = resetVideoForm;
$("vSave").onclick = async () => {
  const yt = $("vYoutube").value.trim().replace(/.*(?:v=|youtu\.be\/|embed\/)([\w-]{11}).*/, "$1");
  const data = {
    title: $("vTitle").value.trim(), youtubeId: yt, coinsPrice: Number($("vPrice").value) || 0,
    order: Number($("vOrder").value) || 0, creator: $("vCreator").value.trim(), free: $("vFree").value === "true",
    published: $("vPublished").value === "true", description: $("vDesc").value.trim(), updatedAt: serverTimestamp(),
  };
  if (!data.title || !data.youtubeId) return alert("العنوان و YouTube ID مطلوبين");
  try {
    if (editingVideo) await setDoc(doc(db, "videos", editingVideo), data, { merge: true });
    else await addDoc(collection(db, "videos"), { ...data, createdAt: serverTimestamp() });
    toast("اتحفظت ✅"); resetVideoForm();
  } catch (e) { alert(e.message); }
};
