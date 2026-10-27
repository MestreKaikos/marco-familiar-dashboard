# Integració amb el Code.gs real (conserva Compra i Sopars)

## 1. Fotos.gs
Copia el contingut de [FotosIntegracio.gs](FotosIntegracio.gs) a un **nou fitxer Apps Script anomenat Fotos.gs** del projecte existent. No copiïs Code.gs/Index.html de l'altre prototip, que tenen un doGet/doPost conflictiu.

## 2. Canvi mínim a Code.gs
Dins del **doPost(e)** existent, substitueix exactament:

\`\`\`js
response = apiAction(request.key, request.action, request.payload || {});
\`\`\`

per:

\`\`\`js
if (request.action === "photos_list" || request.action === "photos_get") {
  response = photoApiAction_(request.key, request.action, request.payload || {});
} else {
  response = apiAction(request.key, request.action, request.payload || {});
}
\`\`\`

**No modifiquis** \`apiAction\`, \`authenticated_\`, \`setup_\`, \`doGet\`, ni \`KEY_HASH\`.

## 3. Mobile.html
Afegeix la secció HTML del fitxer [MobileFotos-fragment.html](MobileFotos-fragment.html) dins de \`#application\`. Mou el JavaScript final del fragment **a l'interior de l'IIFE existent**, just abans de \`})();\`; si el deixes com un segon script no veurà les variables \`key\` ni \`$\`.

## 4. Drive i secrets
La carpeta personal ja creada és:
**Marc Familiar · Fotos compartides**.

A **Configuració del projecte → Propietats de script**, crea \`PHOTO_FOLDER_ID\` amb l'ID de la carpeta personal creada prèviament (no cal posar-lo al repositori). Després executa **setupPhotos_()** des de l'editor i desa la nova clau de lectura privada (separada de la familiar). No la comparteixis en xats ni a GitHub. Comprova que la carpeta no tingui compartició pública.

## 5. Desplegament i comprovació
Actualitza el desplegament Apps Script existent amb una nova versió; no creïs un altre projecte ni modifiquis la URL actual. Abans de provar amb dades personals, revisa que Compra i Sopars segueixin funcionant i puja una foto de prova no personal.

## 6. Android
El client Android del repositori utilitza ara \`photos_list\` i \`photos_get\` a l'API actual del projecte. A Fotografies → Fotos del mòbil es configura la URL /exec i la clau de lectura generada per setupPhotos_(). Encara cal provar la versió compilada i la connexió real. **No reinstal·lar encara l'APK en ús sense preservar les dades locals.**

## Limitacions
El desplegament Apps Script que executa com a propietari no identifica individualment les persones que coneguin la clau familiar. Per tant, només compartir la clau amb membres de confiança. Una credencial guardada al marc sense xifrat específic és una limitació coneguda. Apps Script també imposa límits de mida i execució.
