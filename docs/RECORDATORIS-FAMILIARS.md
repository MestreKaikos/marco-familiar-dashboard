# Marc Familiar · Recordatoris en lloc de Sopars

## Què canvia
La pestanya **Sopars** del mòbil es substitueix per **📌 Recordatoris**. La portada i el calendari del marc Android també mostren els recordatoris per data límit. Hi ha una pantalla dedicada al marc per marcar-los com a fets, consultar-los i editar-los.

Cada recordatori té:
- Títol (p. ex., «Pagar quota» o «Vacunació d'en Llamp»), obligatori.
- **Data límit opcional**, amb selector natiu al navegador; el marc permet AAAA-MM-DD.
- Nota opcional.
- **Casella fet/pendent reversible**, amb els vençuts destacats i els completats al final.
- Edició i eliminació.

El servidor crea automàticament una pestanya **`Recordatoris`** al mateix Sheets privat, amb les columnes `id,titol,data,fet,notes`, en el primer `sync`. És una pestanya independent.

**No s'esborren els sopars existents:** la pestanya `Sopars` i les operacions `dinner_set` es conserven per compatibilitat amb versions anteriors. Deixen de mostrar-se a les pantalles noves. Aquesta és una substitució d'interfície, no una migració destructiva.

## Fitxers per actualitzar el teu Apps Script existent
Només cal substituir:
1. [`sync/google-apps-script/Code.gs`](../sync/google-apps-script/Code.gs).
2. [`sync/google-apps-script/Mobile.html`](../sync/google-apps-script/Mobile.html).

**`Fotos.gs` i `Tasques.gs` continuen tal com estan.** Cal editar **la implementació existent**, escollir **Versió nova** i tornar a implementar. Es conserva la mateixa URL `/exec`, `KEY_HASH`, `PHOTO_READ_KEY_HASH` i `PHOTO_FOLDER_ID`. No s'executen `rotateSecret_` ni funcions d'inicialització.

En el navegador mòbil, comprovar que aparegui la pestanya **Recordatoris**, crear un recordatori amb data límit, marcar-lo fet i comprovar la nova pestanya de Sheets. Després, instal·lar l'APK actualitzada amb la signatura compatible, **sense desinstal·lar l'app antiga**.

## Consideracions
- És una llista compartida visual; **no envia notificacions push, correus o avisos programats** per si sola. El marc indica «Vençut» o «Avui» a la pantalla.
- Sense data límit no hi ha venciment.
- Les operacions noves són `reminder_set`, `reminder_done` i `reminder_delete`, acceptades per `sync` amb la mateixa clau familiar. La resposta inclou `data.reminders`.
- Els dispositius Android connectats desen les dades rebudes en memòria local i les edicions s'envien per la cua de sincronització; els sopars històrics es continuen sincronitzant de manera interna per compatibilitat.
