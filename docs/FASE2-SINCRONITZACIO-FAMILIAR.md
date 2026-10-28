# Fase posterior · sincronització familiar entre mòbils i marc

**Nota de prioritats (27/10/2026): la propera millora acordada és pujar fotografies des del mòbil a la galeria del marc.** Aquest document queda com a planificació de la sincronització de tasques i recompenses, que s'abordarà després de les fotos i la millora de la web mòbil. La compra i els sopars ja s'han provat amb Google Sheets en el flux mòbil existent; no reconstruir-los de zero.

Actualitzat: 27/10/2026. Document de treball de la branca `feature/marc-familiar-cat-fotos`. **No està implementat encara**.

## Punt de partida verificat
- La PR #1 continua oberta i `main` no conté aquests canvis.
- `LocalFamilyStore.kt` desa compra, tasques, estrelles i recompenses a `SharedPreferences`; els sopars es guarden localment. Dades independents a cada Android.
- L'usuari ha provat un flux app → Google Sheets per registrar sopars i compres. Abans d'integrar-lo amb Android, cal inspeccionar el contracte i l'Apps Script existents; **no crear un segon backend incompatible**.
- Google Calendar és actualment només de lectura per iCal, amb fins a cinc fonts diferenciades i els seus colors. La compra no s'ha de barrejar amb esdeveniments.

## Objectiu funcional
Un membre afegeix «Llet» des del seu mòbil; ho veuen tots els altres mòbils autoritzats i el marc. Quan algú ho compra, es marca comprat a tots els dispositius. Els sopars es comparteixen també. Les tasques i estrelles d'en Rai s'actualitzen sense duplicar crèdit i amb aprovació d'adults.

## Ordre de treball
1. Identificar el full Google Sheets i l'Apps Script que ja funcionen i documentar les seves columnes i operacions, sense copiar URL secretes, tokens o dades personals aquí.
2. Definir esquemes amb ID estable per registre, tipus, autoria, marca temporal de modificació i marca d'esborrat. Separar compra, sopars, feines, validacions, recompenses i saldo.
3. Establir un mecanisme de permisos real: membres autoritzats per consultar/escriure i rol adult per validar punts/canvis sensibles. **Un PIN només local no serveix per autenticar accions al servidor.**
4. Afegir a l'APK un client de sincronització amb operacions idempotents, cua local per a funcionament sense xarxa, reintents i estat visible (sincronitzat/pendent/error).
5. Adaptar la interfície mòbil perquè tots els membres puguin afegir, editar i completar compra i editar sopars, reutilitzant el backend existent.
6. Migrar dades locals amb previsualització i sense sobreescriure registres ja existents ni concedir estrelles dues vegades.
7. Conservar color i procedència de cada calendari iCal a la vista combinada.
8. Proves: dos mòbils i marc; canvis simultanis, duplicats, talls de connexió, reintents, rellotge incorrecte, revocació d'accés i restauració.

## Criteris d'acceptació
- Productes i sopars creats o modificats al mòbil A visibles al mòbil B i al marc, sense duplicats.
- Marcatge de comprat i esborrat coherent a tots els dispositius.
- Una validació de tasca acredita estrelles una única vegada, fins i tot amb reintents o doble pulsació.
- L'adult aprova amb autenticació validada al backend; un menor no pot augmentar-se el saldo.
- Fotos, temps i calendaris segueixen funcionant si Sheets cau.
- Cap secret ni dada familiar real no entra al repositori o als logs de compilació.
- No es fusiona amb `main` ni es desinstal·la/reinstal·la l'APK sense validar migració i còpia de les dades.
