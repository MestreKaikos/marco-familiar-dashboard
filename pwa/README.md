# Marc Familiar · PWA instal·lable

Aquesta PWA és una interfície alternativa, no una altra base de dades. La compra, els recordatoris, les tasques, les recompenses i les fotos segueixen gestionades pel mateix Apps Script i Google Sheets.

## Estructura

- index.html: mateix disseny i controls que l'HTML d'Apps Script, amb transport PWA.
- manifest.webmanifest: mode standalone, nom i icones.
- sw.js: només desa la interfície pública, mai les dades, fotos, clau o API.
- api/relay.js: transport HTTPS amb accions permeses, límit de mida i sense registrar la clau.
- api/icon.js: PNG de 192 i 512 píxels.
- tests/pwa.test.mjs: tests de manifest, HTML, icones i proxy.

## Desplegament

1. Nou projecte Vercel de GitHub MestreKaikos/marco-familiar-dashboard, amb Root Directory pwa.
2. Variable d'entorn MARC_APPS_SCRIPT_URL, amb l'URL completa de la mateixa implementació /exec que ja funciona.
3. Comprova la URL HTTPS, introdueix la mateixa clau familiar de 64 caràcters. No es desa a Vercel ni al GitHub.
4. Al Chrome Android: menú ⋮ > Afegeix a la pantalla d'inici > Instal·la.
5. Prova compra, recordatoris i tasques contra el mateix Google Sheets.

No cal canviar ni executar Code.gs, Tasques.gs o Fotos.gs; les claus i el full es mantenen.

## Diferències

Apps Script serveix HTML dins d'un iframe restringit. La PWA necessita un origen web HTTPS propi. El proxy tradueix les crides del navegador en peticions al mateix Apps Script i no desa mai la clau.

Sense Internet es pot obrir la interfície, però no consultar ni modificar les dades compartides. No hi ha notificacions push. A la PWA, les imatges es comprimeixen a JPEG de menys d'1,1 MB abans de pujar-les (el formulari original d'Apps Script continua admetent originals de fins a 5 MB). Les fotos de Drive més grans de 3 MB poden excedir el límit de resposta del proxy de Vercel.

La pàgina PWA es deriva de la versió HTML mòbil. Quan es revisi el disseny cal actualitzar-ne totes dues còpies fins que es mecanitzi la generació.
