// Service worker: الشبكة أولاً، ولو مفيش نت يرجّع آخر نسخة محفوظة (تشغيل أوفلاين)
const CACHE_NAME = 'egypt-drama-v2';
const PRECACHE = ['/', '/index.html', '/app.html', '/manifest.json', '/icon.svg'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then((c) => c.addAll(PRECACHE)).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  // متخزنش لوحة الأدمن ولا الـ API ولا أي حاجة من دومين تاني
  if (req.method !== 'GET' || url.origin !== self.location.origin ||
      url.pathname.startsWith('/admin') || url.pathname.startsWith('/api')) return;
  e.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE_NAME).then((c) => c.put(req, copy)); }
        return res;
      })
      .catch(() => caches.match(req).then((r) => r || caches.match('/')))
  );
});
