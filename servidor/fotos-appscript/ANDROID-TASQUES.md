# Connexió de les tasques d'en Rai amb Android

L'Android envia un POST JSON al mateix Apps Script `/exec` de les fotografies, amb `action: "family_tasks"`.
A la funció **doPost(e)** del teu `Code.gs` actual, afegeix aquesta branca **abans** de les accions de compra/sopars i fotos:

```javascript
if (request.action === "family_tasks") {
  const inner = request.payload || {};
  response = familyTasksAction(request.key, inner.action, inner.data || {});
} else if (request.action === "photos_list" || request.action === "photos_get") {
  response = photoApiAction_(request.key, request.action, request.payload || {});
} else {
  response = apiAction(request.key, request.action, request.payload || {});
}
```

Mantén intactes la resta de `doPost`, `doGet` i les propietats de script. No copiïs aquest fragment com una funció separada: substitueix el bloc de distribució actual al lloc on es construeix `response`.

Desa i publica una **Versió nova** del desplegament existent. No comparteixis ni publiquis les claus al repositori.

A la nova pestanya **Família** del marc de proves, prem «Configurar», introdueix l'URL `/exec` de la teva web i la **clau familiar** (la mateixa de compra/sopars; no la clau privada de lectura de fotos). Prem «Desar connexió». Els canvis es consulten cada 60 segons. La clau es desa a preferències locals de l'Android, sense xifratge específic; utilitza-ho només en un dispositiu familiar de confiança.

El bescanvi s'ha d'aprovar amb PIN des de la web mòbil. La pestanya Android només demana la recompensa. La compra i les fotos no es modifiquen per aquesta fase.

### Precaució
La pestanya anterior `LocalFamilyScreen` encara és al projecte, però la ruta Família mostra ara `RaiFamilyScreen`; la compra local anterior no s'ha migrat a Sheets. No instal·lar com a substitució de la versió en ús fins provar la nova APK en paral·lel.
