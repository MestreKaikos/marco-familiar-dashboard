/* Marc Familiar: la interfície es pot obrir sense connexió, les dades NO es desen al cache. */
const CACHE='marc-familiar-shell-v1';
const SHELL=['/','/manifest.webmanifest','/icon.svg'];
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil(caches.keys().then(names=>Promise.all(names.filter(n=>n!==CACHE).map(n=>caches.delete(n)))).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',event=>{
  const request=event.request,url=new URL(request.url);
  if(request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
  if(request.mode==='navigate'){
    event.respondWith(fetch(request).then(response=>{
      if(response.ok){const copy=response.clone();caches.open(CACHE).then(c=>c.put('/',copy));}
      return response;
    }).catch(()=>caches.match('/')));
    return;
  }
  event.respondWith(caches.match(request).then(cached=>cached||fetch(request)));
});
