# Pujada de fotos des del mòbil · MVP

Codi: `Code.gs` + `Index.html`. **Encara no desplegat**. L'Android encara no consulta aquesta bústia, per tant pujar la foto a Drive no la mostrarà al marc fins a implementar el client de sincronització.

## Configuració (sense secrets a GitHub)
1. Crea una carpeta privada a Google Drive, per exemple `Marc Familiar · Fotos`; no la facis pública.
2. Crea un projecte nou de Google Apps Script i afegeix els continguts de `Code.gs` i `Index.html`.
3. A **Configuració del projecte → Propietats de script**, afegeix `PHOTO_FOLDER_ID`, `PHOTO_UPLOAD_KEY` i `PHOTO_READ_KEY`. Les dues claus han de ser **diferents**, aleatòries i d'almenys 32 caràcters.
4. Desplega l'aplicació web amb **executa com a mi**. Per compartir amb persones sense iniciar sessió cal l'accés `Qualsevol persona`; aquesta modalitat només està protegida per la clau familiar, no per identitat individual. Si no és acceptable, no la despleguis així: substitueix-la per un sistema amb usuaris autenticats.
5. Prova pujant **una foto no personal** (JPG/PNG/WebP fins a 5 MB) i confirma que es crea dins la carpeta privada.

## Advertiments
- No comparteixis les claus, l'ID privat o l'URL personal del desplegament en captures o GitHub.
- Qui conegui la clau de pujada pot afegir imatges; revoca-la canviant la propietat del projecte si se'n fa un ús indegut.
- Google Apps Script té límits de temps, memòria i mida de missatge; no és adequat per arxius enormes.
- La pàgina de moment puja una foto per vegada i no en desa còpia al navegador.
- No s'han implementat encara la sincronització Android, l'autenticació per membre, les quotes per usuari ni l'edició/esborrat des de mòbil.
- El servei també ofereix `doPost` amb JSON per al futur client Android: `{"action":"list","key":"CLAU_LECTURA"}` i `{"action":"get","key":"CLAU_LECTURA","id":"ID_FOTO"}`. Encara falta implementar el client Android i provar redireccions/limitacions de ContentService. No posar mai la clau de lectura al codi font o dins d’un APK públic.
