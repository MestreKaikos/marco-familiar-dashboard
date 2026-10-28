# Sincronització familiar · Compra i sopars (fase inicial)

**Nota:** Aquesta guia descriu la fase inicial. Per a la versió que integra tasques, punts, recompenses i fotos amb una sola connexió, consulta [Marc Familiar unificat](MARC-FAMILIAR-UNIFICAT.md).

**Estat:** implementació experimental. L'app Android continua funcionant sense Internet i, fins que es configura el núvol, totes les dades romanen locals. **No connecteu dades personals fins a haver verificat les proves del PR.**

## Què sincronitza aquesta fase?

| Secció | Compartida entre dispositius | Sense Internet |
|---|---|---|
| Llista de la compra | Sí (afegir, editar, marcar comprat, eliminar) | Sí, canvis en cua |
| Sopars per data | Sí (afegir, editar, esborrar) | Sí, canvis en cua |
| Feines familiars | Encara no | Sí, local |
| Recompenses, estrelles i PIN parental | **No** | Sí, local |
| Fotografies i calendaris Google | Sense canvis | Segons funció |

**No tenim un servidor propi:** fem servir un full privat de Google Sheets com a base de dades i un Google Apps Script que respon a l'app Android i proporciona una pàgina mòbil. Google pot aplicar quotes i polítiques d'ús; no prometem disponibilitat ni gratuïtat perpètues.

## 1. Crear el full de càlcul (compte Google personal de confiança)

1. A <https://sheets.google.com>, crea un full de càlcul nou titulat, per exemple, **Marc Familiar · dades**. Mantén-lo **privat**, sense compartir-lo públicament.
2. A **Extensions → Apps Script**, obre el projecte de scripts vinculat al full.
3. Copia el contingut de [Code.gs](../sync/google-apps-script/Code.gs) al fitxer de script predeterminat.
4. Afegeix un fitxer HTML amb nom **Mobile** i enganxa [Mobile.html](../sync/google-apps-script/Mobile.html).
5. Desa tots els fitxers.

## 2. Crear la clau secreta

1. A l'editor, selecciona la funció `setup_` i prem **Executar**. Autoritza el script a accedir al **teu full de càlcul**. El primer ús crea les pestanyes **Compra** i **Sopars**.
2. A **Registre d'execució**, copia la **clau privada de 64 caràcters** que s'ha generat (només surt en la primera execució).
3. Guarda-la en un gestor de contrasenyes. **No** la comparteixis al xat, en correus ni al repositori GitHub.

Si perds la clau, executa `rotateSecret_` a l'editor per crear-ne una de nova; hauràs de reconnectar tots els dispositius. La clau es desa al servidor **només com a hash SHA-256**; qui té permisos per editar el script o el full té control de les dades.

## 3. Publicar el servei

1. A Apps Script, prem **Implementa → Implementació nova → Aplicació web**.
2. A **Executa com**, selecciona **Jo** (propietari del full).
3. A **Qui té accés**, selecciona **Qualsevol persona** (si el teu tipus de compte ho permet).
4. Prem **Implementa** i copia l'adreça de tipus:
   `https://script.google.com/macros/s/XXXXXXXX/exec`

**Avís de seguretat:** la pàgina és accessible a qualsevol persona que conegui l'enllaç, però el servidor exigeix **la clau privada de 64 caràcters per llegir o modificar dades**. No publiques el full de càlcul: només el propietari del script pot accedir-hi directament. Si la clau es filtra, una altra persona podria modificar la compra i els sopars. Per això *no* hi guardis informació sensible i no comparteixis mai URL i clau juntes.

El proveïdor és Google Apps Script, i el servei pot requerir autoritzacions i estar subjecte a límits de quota. Referències: [Aplicacions web](https://developers.google.com/apps-script/guides/web) · [Content Service i redireccions](https://developers.google.com/apps-script/guides/content).

## 4. Connectar el Marc Familiar

Des d'una APK creada a partir de la branca `feature/sincronitzacio-familiar`:

1. Entra a **Opcions → Sincronització familiar**.
2. Enganxa l'**adreça /exec** i la **clau de 64 caràcters**.
3. Decideix una opció d'importació:
   - **Connectar i fusionar dades** (*recomanada*): manté els productes i sopars que ja hi ha al núvol; hi afegeix els productes locals que hi faltin pel nom i els sopars locals de dates buides.
   - **Utilitzar només les dades del núvol**: substitueix la compra i els sopars locals. **No es pot desfer automàticament.**
4. L'app envia i consulta canvis per HTTPS aproximadament cada **45 segons**, i també poc després de les modificacions locals. Amb Internet tallat, conserva una cua de canvis i els torna a enviar en recuperar la connexió.

## 5. Accedir des dels mòbils

- Obre **la mateixa adreça web /exec** al navegador del mòbil.
- Escriu la clau privada. **No** es col·loca mai a l'URL: el navegador només la recorda a la seva sessió actual.
- Afegeix productes, marca'ls com a comprats o escriu els sopars dels pròxims set dies.
- La pàgina es refresca periòdicament si continua oberta i té Internet.

La pàgina mòbil és una interfície **en línia**. És l'aplicació Android del marc la que manté els canvis offline.

## Com es resolen els canvis simultanis?

- Cada producte té un ID únic; els canvis es fan com a operacions idempotents de *desar/eliminar*, mai incrementals.
- Les operacions passen pel bloqueig `LockService` de Google per evitar escriure al full al mateix temps.
- Si dues persones editen el mateix producte o el mateix sopar alhora, **preval l'últim canvi processat pel servidor**.
- No es fan servir substitucions massives de totes les dades per pujar un sol canvi, cosa que evita esborrar altres productes afegits en paral·lel.
- En el primer enllaç entre el marc i el núvol, l'opció de fusió elimina possibles duplicats *per nom*, però no assegura la detecció de tots els duplicats si els noms difereixen.

## Còpia de seguretat i privadesa

- El full privat és fàcil d'exportar a Excel/CSV, però **els enllaços secrets i clau no s'han de compartir**.
- **Mai no posis les dades del full, la clau o les URL personalitzades al repositori públic o a les captures.**
- Desconnectar el marc elimina la clau local i conserva les dades ja carregades. Una desinstal·lació esborra les dades locals no sincronitzades.
- Les peticions només s'admeten cap a `https://script.google.com/macros/s/.../exec`; el backend només accepta POST autenticat.
- La clau de l'APK es desa a preferències **privades d'Android**, sense xifrat addicional: evitar instal·lar la versió de prova en un dispositiu compartit o compromès.
- Els permisos de Google Apps Script són un compromís de confiança: el script executa com el propietari i **pot modificar el full**. Inspecciona el codi i no hi afegeixis scripts de tercers.

## Millores de connexió i verificació (fase de proves)

- La configuració es troba a **Opcions → Sincronització familiar**, en una **pantalla completa**, no dins de Família ni en una finestra flotant.
- Es poden enganxar l'URL i la clau en moments diferents. L'Android desa un esborrany privat i conserva els camps si canvies de pestanya o d'aplicació. L'esborrany s'esborra en connectar o desconnectar el dispositiu.
- Si prems Connectar amb un format invàlid, veuràs quin camp has de corregir. Si el servei no respon, queda visible l'error i no es perd el que s'ha introduït.
- Un cop connectat, la pantalla indica **quants productes i sopars ha retornat Google Sheets**, quants productes es mostren al marc, quants canvis hi ha pendents i l'hora de l'última sincronització.
- **Avui** i **Família** llegeixen la mateixa `LocalFamilyStore` en memòria; no hi ha dues llistes separades. El text de Família distingeix mode local, mode compartit i canvis pendents.

**Comprovació si apareixen tasques però no compra:** les tasques i premis són locals i poden continuar visibles encara que la compra estigui buida. Entra a Opcions → Sincronització familiar, prem **Sincronitzar ara** i compara «Productes al núvol» amb «Productes visibles al marc». Si el núvol diu 0 però Google Sheets té productes, verifica que el desplegament **/exec** apuntin al mateix Apps Script i que el codi publicat sigui l'últim. No facis «Utilitzar només les dades del núvol» fins a confirmar-ho, perquè substitueix la compra local.

## Proves recomanades

- [ ] Crear un full i script de **prova**, sense dades familiars.
- [ ] Comprovar que sense clau i amb clau incorrecta no retorna dades.
- [ ] Obrir la pàgina des d'un mòbil i afegir un producte de prova.
- [ ] Connectar l'APK i comprovar que apareix el producte.
- [ ] Apagar el Wi‑Fi del marc, afegir un segon producte, tornar-lo a activar i comprovar que apareix al mòbil.
- [ ] Modificar un sopar des del mòbil, comprovar que apareix a la portada i al calendari.
- [ ] Comprovar que les tasques, estrelles i PIN d'en Rai **no canvien** quan sincronitzem la compra.
- [ ] Comprovar la compilació i les captures amb l'emulador abans d'instal·lar-ho al dispositiu real.

**Limitació:** aquesta fase no permet controlar SmartThings ni sincronitzar les recompenses d'en Rai. Són desenvolupaments separats.

