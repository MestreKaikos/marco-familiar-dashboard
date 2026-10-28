# Compatibilitat amb el projecte real d'Apps Script · revisió 28/10/2026

## Fitxers reals examinats
- **`Code.gs`**: autentica compra i sopars mitjançant `KEY_HASH`, amb la ruta HTTP `sync`. Envia `family_tasks` a la funció externa `familyTasksAction` i `photos_list/photos_get` a `photoApiAction_`.
- **`Fotos.gs`**: verificat a partir del codi facilitat pel propietari. Gestiona la carpeta configurada a `PHOTO_FOLDER_ID`, puja fotos des del mòbil amb `uploadFamilyPhoto` (clau familiar) i ofereix lectura al marc antic amb **`PHOTO_READ_KEY_HASH`**.
- **Encara pendents d'examinar**: el fitxer que defineix `familyTasksAction`, el `Mobile.html` de producció i qualsevol altre `.gs` existent.

## Les dues claus llargues, identificades
1. La **clau familiar** és una cadena de 64 caràcters validada contra el hash `KEY_HASH`. Serveix per a compres, sopars i `uploadFamilyPhoto`; també és l'única clau que necessita el **marc unificat nou**.
2. La **clau de lectura de fotografies** és una altra cadena de 64 caràcters, validada contra `PHOTO_READ_KEY_HASH` per `photoReaderAuthenticated_`. Continua servint al marc antic amb les rutes `photos_list` i `photos_get`.

**Totes dues es conserven en producció.** No canviar-les, imprimir-les, publicar-les ni executar `rotateSecret_`. El codi nou permet que el dispositiu actualitzat faci la lectura de fotos amb la clau familiar sense invalidar la clau de només lectura antiga.

## Fotos i carpeta existent
`Fotos.gs` defineix `photoFolder_()`; per tant, la versió unificada de `Code.gs` **ja no la redefineix**. Aquest conflicte era crític: el projecte real executa tots els fitxers `.gs` dins d'un espai global compartit.

- `PHOTO_FOLDER_ID` continua apuntant a la carpeta de les **sis fotografies de prova**. No es crea una carpeta nova.
- Les fotografies són fitxers de Drive, no registres d'una pestanya `Fotos`. No s'ha de crear ni indexar cap foto en un Sheets.
- L'API antiga `photoApiAction_` accepta JPEG, PNG i WebP, fins a **5 MiB** i un màxim de 300 fitxers. La nova API respecta aquests formats i llegeix les fotos directament de la carpeta; la nova càrrega de navegador comprimeix JPEG fins a 1,1 MB.
- L'Android unificat mostra com a màxim 120 fotos compartides per no omplir el dispositiu i desa les descarregades en memòria privada.
- La nova API `photo_get` valida que l'ID sol·licitat és realment dins de la carpeta; no pot llegir fitxers arbitraris de Drive.
- `prepararFotosFamiliars()` només consulta i compta les fotos compatibles: **no crea carpetes, no toca Sheets, no modifica les claus**.

## Tasques: encara cal una revisió
La ruta antiga `family_tasks` s'ha conservat, però **encara no sabem quines globals, validacions i convencions de punts utilitza `familyTasksAction`**. El projecte real té històric de tasques i recompenses; no n'hem de substituir el model sense comprovar el fitxer corresponent. La funció nova `readFamily_` ja llegeix els fulls existents, però cal assegurar-ne la concordança amb l'API antiga.

## Condicions abans de publicar
1. Desar còpies privades de `Code.gs`, `Fotos.gs`, `Mobile.html` i el fitxer de tasques. La còpia de seguretat del Sheets del 28/10/2026 ja existeix.
2. Revisar les col·lisions de constants i funcions globals, i les convencions d'autenticació de tasques.
3. Fer proves en una **còpia d'Apps Script i del Sheets** sense tocar producció.
4. Si tot passa, publicar una nova versió de l'Apps Script existent i verificar la mateixa URL /exec i la mateixa clau familiar.
5. Instal·lar l'APK unificat amb una signatura compatible, sense desinstal·lar l'app actual.

**Estat:** l'API de fotos del repositori està adaptada al `Fotos.gs` conegut i passa proves simulades. Això **no equival encara a una prova real del desplegament d'Apps Script**.
