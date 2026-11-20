const C="gastos-v1";
self.addEventListener("install",e=>{self.skipWaiting();e.waitUntil(caches.open(C).then(c=>c.addAll(["./","manifest.json","icon-192.png","icon-512.png"])).catch(()=>{}))});
self.addEventListener("activate",e=>self.clients.claim());
self.addEventListener("fetch",e=>{if(e.request.method!=="GET")return;e.respondWith(fetch(e.request).then(r=>{const k=r.clone();caches.open(C).then(c=>c.put(e.request,k)).catch(()=>{});return r}).catch(()=>caches.match(e.request)))});
