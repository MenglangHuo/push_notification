// OneSignal-like Web Push Service Worker
/* eslint-disable no-restricted-globals */

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  if (!event.data) {
    console.warn('[ServiceWorker] Push event received but no payload data found.');
    return;
  }

  let payload = {};
  try {
    payload = event.data.json();
  } catch (err) {
    payload = {
      title: 'New Notification',
      body: event.data.text()
    };
  }

  const title = payload.title || 'Push Notification';
  const options = {
    body: payload.body || '',
    icon: payload.icon || '/vite.svg',
    badge: payload.badge || '/vite.svg',
    image: payload.image,
    tag: payload.tag || ('notif_' + Date.now()),
    requireInteraction: payload.requireInteraction || false,
    data: {
      url: payload.url || payload.deepLink || payload.deep_link || '/',
      customData: payload.data || {}
    }
  };

  event.waitUntil(
    self.registration.showNotification(title, options)
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const targetUrl = event.notification.data?.url || '/';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      // If a tab is already open with the target URL, focus it
      for (const client of windowClients) {
        if (client.url === targetUrl && 'focus' in client) {
          return client.focus();
        }
      }
      // Otherwise open a new window/tab
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
