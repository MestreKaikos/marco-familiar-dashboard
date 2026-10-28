# Marc Familiar · Integració unificada (branca de proves)

## Objectiu
Una **única URL /exec i una única clau familiar** per a compres, sopars, tasques, punts, recompenses i fotografies del Drive. No modificar la branca principal ni substituir el servei existent fins haver fet còpia de seguretat i validat la prova.

## Serveis existents
- Google Sheets «Marc Familiar - Dades»: es mantenen les pestanyes `Compra`, `Sopars`, `Tasques`, `TasquesFetes`, `Recompenses`, `CanvisPunts` i `PeticionsPremis`. El nou codi afegeix només la pestanya `Fotos` quan cal.
- No es genera una clau nova: el codi utilitza la propietat privada `KEY_HASH` del projecte d'Apps Script. **No executis `rotateSecret_` ni eliminis `KEY_HASH`**.
- Les dues claus llargues de l'usuari poden provenir d'altres scripts o d'una rotació. **No es poden identificar mirant les seves formes ni la URL**. L'única clau vàlida és la que autentica el desplegament /exec, sense exposar-la en captures ni xats.
- Les tasques del full són la font de veritat a partir de la primera sincronització amb el backend nou. L'Android conserva una còpia privada de les dades locals anteriors a aquesta primera importació.

## Activació del servei (quan el PR i les proves siguin correctes)
1. Fes una **còpia del Google Sheets** i desa una còpia del `Code.gs` i `Mobile.html` *actualment publicats*. **No executis cap canvi directament sobre el servei de producció abans de provar-lo**.
2. En el mateix projecte d'Apps Script, conserva les **propietats del projecte** i la implementació /exec existents. Cal comparar el `Code.gs` actual amb el nou per assegurar que no desapareix cap funcionalitat pròpia.
3. Instal·la el codi de `sync/google-apps-script/Code.gs` i `Mobile.html` de la branca, en el mateix projecte, només després d'aquesta comparació.
4. A Apps Script executa **`prepararFotosFamiliars`** per autoritzar Drive i indexar fotografies ja existents. Per defecte recupera la carpeta **«Marc Familiar · Fotos compartides»** si la propietat `PHOTO_FOLDER_ID` no existia. Si un codi anterior ja havia fixat una carpeta diferent, executa `vincularCarpetaFotosExistents` abans de `prepararFotosFamiliars` per adoptar expressament la carpeta de prova (conserva l'índex antic).
5. Revisa que el full `Fotos` s'ha omplert i que les fotografies existents s'han conservat. El nou codi llegeix **JPG i PNG** (fins a 1,1 MB per fotografia). Si alguna supera aquest límit, no s'importa i no s'esborra.
6. **Implementa → Gestiona les implementacions → Edita → Nova versió**; no generis una URL nova. Verifica la compra/sopars i, després, la secció de tasques i fotos a la mateixa adreça /exec.
7. Instal·la l'APK **només si les proves Android han passat i la signatura coincideix amb l'APK instal·lada**. No desinstal·lis l'app: les dades locals es poden perdre.

## Operacions i controls
- `sync`: estat complet de compra, sopars, tasques actives, tasques fetes, recompenses i punts. Les tasques antigues es llegeixen sense recrear ni eliminar cap registre.
- `task_set`, `task_delete`, `task_complete`: crear/desactivar/validar feines. La validació s'escriu a `TasquesFetes` i no suma dues vegades la mateixa feina única o la mateixa feina diària el mateix dia.
- `reward_set`, `reward_delete`, `reward_redeem`: premis i bescanvi, amb ID idempotent; el servidor calcula el saldo a partir del registre històric `TasquesFetes` menys `CanvisPunts`. No es poden gastar punts insuficients.
- La pàgina web del mòbil permet afegir feines i recompenses i veure el seu estat. **La validació amb PIN es manté al marc**. La clau familiar és una clau compartida amb permisos complets de servei, no hi ha comptes per persona.
- `photo_list`, `photo_get`, `photo_upload`, `photo_delete`: fotos amb la mateixa clau, guardades a Drive privat. Les sis imatges de prova existents s'indexen durant la preparació si compleixen el límit de mida.

## Validació abans de desplegar
- [ ] Node: proves de compra, sopars, tasques, punts, premis i fotos (Drive simulat).
- [ ] Android: compilació i proves unitàries.
- [ ] Prova en Android 10 (pantalla horitzontal) i captures.
- [ ] En un entorn de prova: mateix `/exec` i clau per a totes les seccions.
- [ ] Les feines fetes i els punts d'en Rai coincideixen amb les pestanyes existents del Sheets.
- [ ] Les sis fotografies de prova apareixen sense tornar-les a enviar.
- [ ] Compra i sopars continuen editables; desconnexió no elimina dades de Sheets.

**Estat:** implementació en PR, no és una migració executada al compte personal. No es pot verificar el codi privat actual d'Apps Script només amb l'enllaç del seu editor.
