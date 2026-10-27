# Colors de Google Calendar · Marc Familiar

Aquesta fase afegeix **colors personalitzables i permanents per calendari**.

## Com funcionen
- A **Opcions → Calendaris de Google**, a cada calendari afegit hi ha un botó **Color**, al costat d'**Eliminar**.
- Toca **Color** i tria entre vuit opcions amb nom: blau, lila, verd, taronja, vermell, turquesa, porpra i gris.
- Els colors es desen al mateix dispositiu i es tornen a aplicar als esdeveniments de l'agenda sense haver de canviar-ne l'enllaç iCal.
- Els calendaris que ja tenies configurats reben automàticament **el color que mostraven abans**; només és una migració inicial. Després **ni eliminar ni afegir calendaris canvia els colors de la resta**.
- Un calendari nou rep el primer color lliure de la paleta, però es pot canviar quan vulguis.
- A **Avui** i **Calendari**, una llegenda discreta mostra un punt del color i el nom de cada calendari.

Els colors es trien dins del Marc Familiar. **No modifiquen els colors dels calendaris originals a Google Calendar**. Tampoc afecten les dades de compra ni sopars.

## Seguretat i proves
- No apareixen URL iCal privades a les captures ni a la llegenda.
- La migració no modifica els enllaços, noms ni identificadors dels calendaris existents.
- Hi ha tests per a la paleta, canvis de color, conservació després d'eliminar/reordenar i migració dels calendaris guardats.
- Les captures es generen amb calendaris ficticis en un emulador Android 10, i no amb dades familiars reals.

## Com provar-ho quan instal·lem l'APK nova
1. Obre **Opcions → Calendaris de Google**.
2. El teu calendari antic ha de continuar afegit, sense haver d'enganxar de nou l'enllaç.
3. Prem **Color**, tria un altre to i torna a **Avui**.
4. La llegenda i els esdeveniments existents han d'utilitzar el color triat.
5. Si tens un segon calendari, comprova que canviar el primer no canvia el segon.

**Important:** no cal reinstal·lar l'APK fins que les captures i tests estiguin validats.
