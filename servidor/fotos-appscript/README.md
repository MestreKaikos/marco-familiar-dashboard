# Pujada de fotos des del mòbil · MVP

Codi: `Code.gs` + `Index.html`. **Encara no desplegat ni validat de punta a punta.** El client Android ja s'ha implementat a la branca de desenvolupament, però falta comprovar la compilació, configurar un desplegament real i fer la prova amb imatges de mostra.

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
- S'ha afegit la sincronització Android a la branca, però encara no s'ha verificat al dispositiu. No hi ha autenticació individual per membre, quotes per usuari ni edició/esborrat des de mòbil.
- El servei també ofereix `doPost` amb JSON per al futur client Android: `{"action":"list","key":"CLAU_LECTURA"}` i `{"action":"get","key":"CLAU_LECTURA","id":"ID_FOTO"}`. Encara falta implementar el client Android i provar redireccions/limitacions de ContentService. No posar mai la clau de lectura al codi font o dins d’un APK públic.

## Configuració al marc després del desplegament
1. Amb una APK **validada**, obre **Fotografies → Fotos del mòbil**.
2. Enganxa l'URL `https://script.google.com/macros/s/.../exec` i la **clau de lectura** (no la clau de pujada).
3. Prem **Desar i sincronitzar**. El marc comprovarà fotos noves aproximadament cada minut i descarregarà fins a quatre imatges per passada.
4. Les fotografies locals es mantenen; les descarregades es conserven a l'emmagatzematge privat de l'aplicació.

**Limitacions de seguretat i recuperació:** la clau del marc es desa en preferències privades Android, sense xifrat específic. La desconnexió treu les credencials, però no esborra automàticament els fitxers ja descarregats. Cal protegir físicament el dispositiu. No desinstal·lis la versió anterior sense còpia de les dades locals.
