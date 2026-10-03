/**
 * Offline support.
 *
 * A tuner is exactly the sort of thing you reach for in a rehearsal room with
 * no signal, so once the app has been opened it must keep working with the
 * network gone entirely.
 *
 * The shell is precached by name at install. The compiled Kotlin core is cached
 * on first use instead, because its file names come out of the build and
 * listing them here would be a second place to keep in step — the page imports
 * all of it on startup, so one successful load fills the cache either way.
 *
 * CACHE_VERSION is stamped at deploy time, so every release starts from a clean
 * cache and no stale module can outlive the build it belongs to.
 */

const CACHE_VERSION = '1.0.0-dev';
const CACHE = `nobstuner-${CACHE_VERSION}`;

const SHELL = [
  './',
  'index.html',
  'styles.css',
  'manifest.webmanifest',
  'src/main.js',
  'src/ui.js',
  'src/store.js',
  'src/audio.js',
  'src/meters.js',
  'src/hop-worklet.js',
  'src/instrument-icons.js',
  'src/version.js',
  'src/views/tuner.js',
  'src/views/library.js',
  'src/views/editor.js',
  'src/views/settings.js',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/maskable-512.png',
  'icons/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      // One missing file must not fail the whole install, or a renamed asset
      // would leave the app with no worker at all.
      Promise.allSettled(SHELL.map((path) => cache.add(new Request(path, { cache: 'reload' })))),
    ),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(names.filter((name) => name !== CACHE).map((name) => caches.delete(name)));
      await self.clients.claim();
    })(),
  );
});

self.addEventListener('message', (event) => {
  if (event.data === 'skip-waiting') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // A deep link or a reload in a standalone window is a navigation to a path
  // the server may not know; the app is a single page, so answer with it.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(async () => {
        const cache = await caches.open(CACHE);
        return (await cache.match('index.html')) || (await cache.match('./'));
      }),
    );
    return;
  }

  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const cached = await cache.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok && response.type === 'basic') cache.put(request, response.clone());
      return response;
    })(),
  );
});
