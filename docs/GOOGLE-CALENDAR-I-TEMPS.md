# Google Calendar i meteorologia · Marc Familiar

## Meteorologia inicial

- L'app arrenca amb **Malgrat de Mar (Maresme, Catalunya)** com a ubicació del temps.
- Coordenades del centre municipal segons Diputació de Barcelona: `41.6459118, 2.7414054`.
- Open-Meteo proporciona la previsió sense compte ni servidor propi.
- Es pot canviar de municipi des de **Opcions → Ubicació del temps**; l'elecció es conserva al marc.

## Google Calendar: lectura privada i sense contrasenya

L'app permet afegir fins a cinc fonts d'esdeveniments iCal de Google amb nom i color diferenciat. És **només lectura**: no es poden crear, canviar ni esborrar esdeveniments de Google des del marc.

1. En un ordinador, obre [Google Calendar](https://calendar.google.com/).
2. A la part superior dreta, obre **Configuració**.
3. A **Configuració dels meus calendaris**, tria el calendari que vols veure al marc.
4. Obre **Integrar calendari**.
5. A **Adreça secreta en format iCal**, copia l'enllaç `https://calendar.google.com/calendar/ical/.../basic.ics`.
6. Obre el **Marc Familiar → Opcions → Calendaris de Google**.
7. Indica el nom visible («Família», «Rai», «Personal»...) i enganxa l'adreça secreta directament a l'APK.

Per afegir calendaris addicionals, repeteix els passos. El marc descarrega periòdicament les cites, n'expandeix les recurrències i manté una còpia temporal a la memòria local per mostrar-la sense connexió.

### Seguretat

- **No copiïs mai l'enllaç iCal al GitHub, a ChatGPT ni en captures de pantalla.** Funciona com una clau de lectura.
- Els enllaços es guarden en preferències **privades del dispositiu Android** i s'envien directament a Google via HTTPS. No es pugen al nostre repositori.
- Aquesta versió **no xifra per separat els enllaços** dins les preferències Android: cal fer servir un dispositiu de confiança i controlar l'accés físic al marc.
- Les captures automàtiques s'obtenen en un emulador net, sense calendaris reals ni credencials.
- Si sospites que has compartit una adreça secreta, a Google Calendar pots restablir-la.
- Alguns comptes de feina o centres educatius poden tenir l'opció d'iCal secret restringida per l'administrador.
- Si es desinstal·la l'APK, es perden els calendaris configurats localment.

**Referència oficial:** https://support.google.com/calendar/answer/37648

## Proves

- Confirmar que la portada mostra Malgrat de Mar i, amb Internet, la previsió.
- Obrir **Opcions → Calendaris de Google** i comprovar el formulari i llista buida, sense cap URL privada en pantalla.
- Afegir una font iCal de prova no personal al dispositiu, comprovar recurrències i eliminació.
- Verificar compilació automàtica i captures a GitHub abans de fusionar el PR.

*No utilitzem OAuth de Google en aquesta fase. La sincronització és unidireccional amb iCal i pot tenir una demora en els canvis.*


## Colors personals per calendari

A **Opcions → Calendaris de Google**, sota el nom de cada calendari es mostra una paleta tàctil de vuit colors. Pots triar-ne un per a «Família», un altre per a «Rai», etc.

- El color es desa **al mateix Android**, juntament amb la configuració del calendari, i es manté després de reiniciar l'app.
- Els calendaris antics reben el color que tenien assignat anteriorment; quan se'n desa o modifica un, es persisteix el color amb el seu identificador.
- Si elimines un calendari, els restants **no canvien de color**. Si n'afegeixes un de nou, s'intenta triar un color que encara no estigui utilitzat.
- A la pestanya **Calendari** hi ha una llegenda amb el nom i el color de cada font.
- Els esdeveniments de la portada i la setmana fan servir el mateix color.
- **No** es llegeix ni es modifica el color de Google Calendar al servidor; aquest és un color local de visualització.
- Per a una prova visual sense dades privades, obre la galeria de captures a `docs/captures/README.md`. El simulador comença sense calendaris de la família i, per tant, no pot mostrar una llegenda amb dades personals.
