# Marc Familiar · Integració unificada (branca de proves)

## Objectiu
Una **única URL /exec i una única clau familiar** per a compres, sopars, tasques, punts, recompenses i fotografies del Drive. No modificar la branca principal ni substituir el servei existent fins haver fet còpia de seguretat i validat la prova.

## Serveis existents i claus
- Google Sheets «Marc Familiar - Dades»: es mantenen les pestanyes `Compra`, `Sopars`, `Tasques`, `TasquesFetes`, `Recompenses`, `CanvisPunts` i `PeticionsPremis`. **No cal crear cap pestanya Fotos.**
- Drive: `Fotos.gs` real utilitza la propietat `PHOTO_FOLDER_ID` i **ja conté sis imatges de prova**. La galeria nova consulta la mateixa carpeta. No la mou, no la duplica ni la fa pública.
- **Dues claus confirmades:** `KEY_HASH` valida la clau familiar (compra, sopars, pujada de fotos), mentre `PHOTO_READ_KEY_HASH` valida la clau de lectura anterior del marc. Es mantenen tots dos hashes per compatibilitat. El **marc nou només haurà de configurar URL /exec + clau familiar**, perquè les noves accions `photo_list` i `photo_get` comproven `KEY_HASH`. No s'ha de rotar ni revelar cap clau.
- Les rutes anteriors `photos_list` i `photos_get`, amb la clau de lectura, continuen delegant a `photoApiAction_` de `Fotos.gs`. L'editor web de producció pot continuar pujant imatges amb `uploadFamilyPhoto`.
- El servei antic de tasques també manté la ruta `family_tasks` i `familyTasksAction`. Cal verificar el fitxer que defineix aquesta funció abans de publicar la versió unificada.
- L'Android conserva una còpia privada de les dades locals abans de la primera importació de tasques del núvol.

## Activació del servei (només després de verificar **tots** els fitxers reals)
1. Ja existeix una **còpia independent del Sheets** creada el 28/10/2026. Conserva també una còpia privada dels fitxers actuals `Code.gs`, `Fotos.gs`, `Mobile.html` i el que defineix `familyTasksAction`.
2. Revisa que no hi hagi col·lisions de funcions entre els diferents fitxers del mateix projecte. **Mai no esborris `Fotos.gs` ni les propietats `KEY_HASH`, `PHOTO_READ_KEY_HASH` o `PHOTO_FOLDER_ID`.**
3. Prova la implementació en una còpia separada del projecte i del Sheets, abans de modificar la versió publicada.
4. Després d'aprovar les proves, actualitza `Code.gs` i `Mobile.html` conservant els fitxers complementaris originals; `prepararFotosFamiliars` només comprova l'accés a la carpeta i mostra un recompte: **no crea cap carpeta ni indexa res**.
5. Verifica que la mateixa carpeta mostra els JPG, PNG i WebP existents de fins a 5 MB. L'enviament del nou panell web comprimeix les fotos a JPEG de fins a 1,1 MB; l'editor antic conserva la seva pujada fins a 5 MB.
6. **Implementa → Gestiona les implementacions → Edita → Nova versió**. Manté la URL /exec. Comprova compra, sopars, tasques, permisos i la galeria abans de migrar el marc.
7. Instal·la l'APK **només quan la compilació sigui correcta i la signatura coincideixi amb la instal·lada**. No desinstal·lis l'app: podria perdre dades locals.

## Operacions i controls
- `sync`: estat complet de compra, sopars, tasques actives, tasques fetes, recompenses i punts. Les tasques antigues es llegeixen sense recrear ni eliminar cap registre.
- `task_set`, `task_delete`, `task_complete`: crear/desactivar/validar feines. La validació s'escriu a `TasquesFetes` i no suma dues vegades la mateixa feina única o la mateixa feina diària el mateix dia.
- `reward_set`, `reward_delete`, `reward_redeem`: premis i bescanvi, amb ID idempotent; el servidor calcula el saldo a partir del registre històric `TasquesFetes` menys `CanvisPunts`. No es poden gastar punts insuficients.
- La pàgina web del mòbil permet afegir feines i recompenses i veure el seu estat. **La validació amb PIN es manté al marc**. La clau familiar és una clau compartida amb permisos complets de servei, no hi ha comptes per persona.
- `photo_list`, `photo_get`, `photo_upload`, `photo_delete`: accés directe a la carpeta privada preexistent de `Fotos.gs` amb la clau familiar. Les sis imatges existents apareixen sense cap importació, sempre que compleixin el límit de 5 MB.

## Validació abans de desplegar
- [ ] Node: proves de compra, sopars, tasques, punts, premis i fotos (Drive simulat).
- [ ] Android: compilació i proves unitàries.
- [ ] Prova en Android 10 (pantalla horitzontal) i captures.
- [ ] En un entorn de prova: mateix `/exec` i clau per a totes les seccions.
- [ ] Les feines fetes i els punts d'en Rai coincideixen amb les pestanyes existents del Sheets.
- [ ] Les sis fotografies de prova apareixen sense tornar-les a enviar ni crear una pestanya `Fotos`.
- [ ] Compra i sopars continuen editables; desconnexió no elimina dades de Sheets.

**Estat:** implementació en PR, no és una migració executada al compte personal. No es pot verificar el codi privat actual d'Apps Script només amb l'enllaç del seu editor.
