# Comparació amb el Code.gs real de producció · 28/10/2026

## Origen i advertència
Aquest document compara la **còpia de `Code.gs` facilitada pel propietari** amb la branca d'integració. Encara **no hem llegit `Fotos.gs` ni el fitxer que defineix `familyTasksAction`, ni `Mobile.html` de producció**. No substituir ni desplegar els fitxers fins que s'hagi verificat la compatibilitat de tot el projecte.

## Punt d'entrada real
El servei actual té tres vies de `doPost`:
1. `request.action === "family_tasks"` → `familyTasksAction(request.key, payload.action, payload.data)`.
2. `request.action === "photos_list"` o `"photos_get"` → `photoApiAction_(request.key, request.action, payload)`.
3. La resta, incloent `sync` → `apiAction(request.key, request.action, payload)`.

L'`apiAction` antic valida `KEY_HASH`, retorna únicament `groceries` i `dinners`, i accepta `grocery_set`, `grocery_delete` i `dinner_set`. `doGet` serveix `Mobile.html`.

## Adaptació de la branca unificada
- **Conservades a la nova implementació** les tres rutes HTTP existents; el codi comprova que les funcions antigues estiguin disponibles abans de delegar.
- Les accions noves `photo_list`, `photo_get`, `photo_upload` i `photo_delete` són **diferents** de les accions antigues `photos_list` i `photos_get`. La compatibilitat HTTP no equival a compatibilitat de dades; les dues implementacions poden utilitzar carpetes, pestanyes, formats o claus diferents.
- El nou `sync` exposa a més `tasks`, `taskDone`, `rewards`, `stars` i `pendingReward`. Aquest format és per a l'Android nou i el Mobile.html proposat.
- L'original `Code.gs` **no demostra que hi hagi dues claus ni que totes les vies comparteixin clau**: la funció `familyTasksAction` i el fitxer `Fotos.gs` poden verificar secrets addicionals. Cal inspeccionar-los sense copiar valors secrets.
- No tocar `KEY_HASH`, `rotateSecret_`, la implementació /exec ni la carpeta de Drive real fins que la integració estigui revisada.

## Problemes que requereixen veure els altres fitxers
1. **`Fotos.gs`**: noms de constants i funcions globals (possibles col·lisions), ID i formats de la carpeta real, autorització de fotos, pujada i llistat ja existents, convenció de `photos_get`, i gestió de les sis imatges.
2. **Fitxer que conté `familyTasksAction`**: autorització de tasques, esquema complet de `Tasques`, `TasquesFetes`, `Recompenses`, `CanvisPunts` i `PeticionsPremis`; normes de repetició diària, saldo, peticions i aprovació de premis. **No s'ha de migrar ni recalcular punts sense validar aquest codi.**
3. **`Mobile.html` de producció**: comprovar la pàgina de fotos, la de compres i les feines abans de reemplaçar cap interfície. Les accions poden fer servir `google.script.run` i no passar per `doPost`.
4. Qualsevol altre `.gs`: escriptori Apps Script tracta els fitxers de codi del mateix projecte com un àmbit compartit; duplicar un `const` o una funció amb nom ja existent pot trencar la publicació.

## Pla d'activació segura
- Full `Marc Familiar - Dades`: existeix una còpia de seguretat creada en Google Drive el 28/10/2026.
- Fer còpia de `Code.gs`, `Fotos.gs`, fitxer de tasques i `Mobile.html` en un lloc privat.
- Validar que el nou servei, amb els fitxers llegats presents, no produeix col·lisions; idealment provar primer en una **còpia independent d'Apps Script vinculada a la còpia del Sheets**, amb una clau de prova, sense afectar dispositius existents.
- Després publicar una **nova versió de la implementació existent** (mateixa URL /exec) i validar compres, sopars, fotos i punts amb els dispositius connectats.
- Una única clau és l'objectiu de l'estat final, però una migració real de claus pot requerir comprovar la clau actual dels diferents serveis. **No suposar que ja s'ha unificat l'autenticació.**
