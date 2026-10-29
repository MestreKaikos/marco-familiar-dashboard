# Marc Familiar · Integració unificada (branca de proves)

## Objectiu
Una **única URL /exec i una única clau familiar** per a compres, sopars, tasques, punts, recompenses i fotografies del Drive. No modificar la branca principal ni substituir el servei existent fins haver fet còpia de seguretat i validat la prova.

## Serveis existents i claus
- Google Sheets «Marc Familiar - Dades»: es mantenen les pestanyes `Compra`, `Sopars`, `Tasques`, `TasquesFetes`, `Recompenses`, `CanvisPunts` i `PeticionsPremis`. **No cal crear cap pestanya Fotos.**
- Drive: `Fotos.gs` real utilitza la propietat `PHOTO_FOLDER_ID` i **ja conté sis imatges de prova**. La galeria nova consulta la mateixa carpeta. No la mou, no la duplica ni la fa pública.
- **Dues claus confirmades:** `KEY_HASH` valida la clau familiar (compra, sopars, pujada de fotos), mentre `PHOTO_READ_KEY_HASH` valida la clau de lectura anterior del marc. Es mantenen tots dos hashes per compatibilitat. El **marc nou només haurà de configurar URL /exec + clau familiar**, perquè les noves accions `photo_list` i `photo_get` comproven `KEY_HASH`. No s'ha de rotar ni revelar cap clau.
- Les rutes anteriors `photos_list` i `photos_get`, amb la clau de lectura, continuen delegant a `photoApiAction_` de `Fotos.gs`. L'editor web de producció pot continuar pujant imatges amb `uploadFamilyPhoto`.
- `Tasques.gs` i el `Mobile.html` reals ja s'han comparat. La versió adaptada conserva `family_tasks`, les recurrències, les franges horàries, l'historial i l'aprovació de premis amb PIN.
- L'Android conserva una còpia privada de les dades locals abans de la primera importació de tasques del núvol.

## Activació del servei (només després de verificar **tots** els fitxers reals)
1. Ja existeix una **còpia independent del Sheets** creada el 28/10/2026. Conserva també una còpia privada dels fitxers actuals `Code.gs`, `Fotos.gs`, `Mobile.html` i `Tasques.gs`.
2. Revisa que no hi hagi col·lisions de funcions entre els diferents fitxers del mateix projecte. **Mai no esborris `Fotos.gs` ni les propietats `KEY_HASH`, `PHOTO_READ_KEY_HASH` o `PHOTO_FOLDER_ID`.**
3. Prova la implementació en una còpia separada del projecte i del Sheets, abans de modificar la versió publicada.
4. Després d'aprovar les proves, actualitza `Code.gs`, `Mobile.html` i `Tasques.gs` (aquest últim inclou la correcció del validador de franges), conservant `Fotos.gs` original. `prepararFotosFamiliars` només comprova l'accés a la carpeta i mostra un recompte: **no crea cap carpeta ni indexa res**.
5. Verifica que la mateixa carpeta mostra els JPG, PNG i WebP existents de fins a 5 MB. El nou panell web **conserva l'enviament original de fins a 5 MB i 20 imatges per tanda**, utilitzant `uploadFamilyPhoto` de `Fotos.gs`.
6. **Implementa → Gestiona les implementacions → Edita → Nova versió**. Manté la URL /exec. Comprova compra, sopars, tasques, permisos i la galeria abans de migrar el marc.
7. Instal·la l'APK **només quan la compilació sigui correcta i la signatura coincideixi amb la instal·lada**. No desinstal·lis l'app: podria perdre dades locals.

## Operacions i controls
- `sync`: estat complet de compra, sopars, tasques actives, tasques fetes, recompenses i punts. Les tasques antigues es llegeixen sense recrear ni eliminar cap registre.
- `task_set`, `task_delete`, `task_complete`: crear/desactivar/validar feines. La validació s'escriu a `TasquesFetes` i no suma dues vegades la mateixa feina única o la mateixa feina diària el mateix dia.
- `reward_set`, `reward_delete`, `reward_request`: premis i petició idempotent. **No s'admet `reward_redeem` sense PIN**. El saldo real es calcula amb `familyTasksSnapshot_`; els punts només es descompten després de `familyTasksAction(..., 'rewards_decide', {id,pin,approve})`.
- La pàgina mòbil manté les quatre pestanyes inferiors (Compra, Sopars, Fotos, Rai); permet configurar dies, freqüències, franges, marcar o desfer tasques, sol·licitar recompenses i aprovar-les amb el **PIN del servidor**, sense guardar-lo. La clau familiar és compartida: no hi ha comptes diferenciats per persona.
- `photo_list` i `photo_get`: lectura de la carpeta privada de `Fotos.gs` amb la clau familiar. `photo_upload` és una via alternativa de JPEG comprimit, mentre que el **mòbil conserva `uploadFamilyPhoto` original** per a JPG/PNG/WebP de 5 MB. `photo_delete` només pot eliminar imatges creades per la via nova (`marc_<id>_`); **mai les fotografies originals**. La galeria mòbil no mostra cap opció d'esborrat.

## Validació abans de desplegar
- [x] Node: proves simulades de compra, sopars, tasques, punts, PIN de premis, quatre pestanyes mòbils i fotos del Drive.
- [x] Android: compilació automatitzada prèvia amb èxit (pendent de confirmar la darrera compilació d'aquesta branca).
- [x] Android 10 (emulador horitzontal): captures automatitzades prèvies publicades (pendent de validar la darrera execució).
- [ ] En un entorn de prova: mateix `/exec` i clau per a totes les seccions.
- [ ] Les feines fetes i els punts d'en Rai coincideixen amb les pestanyes existents del Sheets.
- [ ] Les sis fotografies de prova apareixen sense tornar-les a enviar ni crear una pestanya `Fotos`.
- [ ] Compra i sopars continuen editables; desconnexió no elimina dades de Sheets.

**Estat:** implementació revisada comparant tots quatre fitxers aportats al xat (`Code.gs`, `Fotos.gs`, `Tasques.gs`, `Mobile.html`). Les proves de JavaScript són simulades i no substitueixen l'execució en un projecte de proves separat d'Apps Script. **Encara no s'ha desplegat al compte personal.**
