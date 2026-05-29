javascript
const CACHE_NAME = 'rehawalk-cache-v2';

// Diese Dateien werden für die Offline-Nutzung gespeichert
const urlsToCache = [
  './',
  './index.html',
  './manifest.json',
  './icon.jpg'
];

// Install-Event: Cachen der App-Dateien
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        return cache.addAll(urlsToCache);
      })
  );
});

// Fetch-Event: Daten aus dem Cache laden, falls offline
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        // Falls gefunden, lade aus dem Cache
        if (response) {
          return response;
        }
        // Ansonsten lade normal aus dem Netzwerk
        return fetch(event.request);
      })
  );
});
