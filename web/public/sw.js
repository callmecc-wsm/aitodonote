// No authenticated responses or API records are cached across accounts.
self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(self.clients.matchAll({type:'window'}).then(list=>{const c=list.find(c=>new URL(c.url).origin===self.location.origin);return c?c.focus():self.clients.openWindow('/note/index.html')}))});
