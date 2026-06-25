const CACHE = 'lull-v12';
const ASSETS = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './brown.webm', './pink.webm', './white.webm', './snoo.webm', './fan.webm', './ocean.webm', './rain.webm', './heart.webm'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const isDoc = e.request.mode === 'navigate' || e.request.destination === 'document';
  if (isDoc) {
    // network-first: always try for the latest app, fall back to cache when offline
    e.respondWith(
      fetch(e.request).then(resp => {
        const copy = resp.clone();
        caches.open(CACHE).then(c => c.put('./index.html', copy)).catch(() => {});
        return resp;
      }).catch(() => caches.match('./index.html').then(r => r || caches.match('./')))
    );
  } else {
    // cache-first for static assets
    e.respondWith(
      caches.match(e.request, { ignoreSearch: true }).then(hit =>
        hit || fetch(e.request).then(resp => {
          const copy = resp.clone();
          caches.open(CACHE).then(c => { try { c.put(e.request, copy); } catch (_) {} });
          return resp;
        })
      )
    );
  }
});
