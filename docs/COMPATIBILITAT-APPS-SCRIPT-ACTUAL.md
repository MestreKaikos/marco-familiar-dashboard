# Compatibilitat amb el projecte real d'Apps Script · revisió 28/10/2026

## Fitxers reals examinats
- **`Code.gs`**: autentica compra i sopars mitjançant `KEY_HASH`, amb la ruta HTTP `sync`. Envia `family_tasks` a la funció externa `familyTasksAction` i `photos_list/photos_get` a `photoApiAction_`.
- **`Fotos.gs`**: verificat a partir del codi facilitat pel propietari. Gestiona la carpeta configurada a `PHOTO_FOLDER_ID`, puja fotos des del mòbil amb `uploadFamilyPhoto` (clau familiar) i ofereix lectura al marc antic amb **`PHOTO_READ_KEY_HASH`**.
- **`Tasques.gs` i `Mobile.html`**: també comparats amb el codi que ha aportat el propietari. No es coneixen altres fitxers `.gs` diferents dels quatre revisats.

## Les dues claus llargues, identificades
1. La **clau familiar** és una cadena de 64 caràcters validada contra el hash `KEY_HASH`. Serveix per a compres, sopars i `uploadFamilyPhoto`; també és l'única clau que necessita el **marc unificat nou**.
2. La **clau de lectura de fotografies** és una altra cadena de 64 caràcters, validada contra `PHOTO_READ_KEY_HASH` per `photoReaderAuthenticated_`. Continua servint al marc antic amb les rutes `photos_list` i `photos_get`.

**Totes dues es conserven en producció.** No canviar-les, imprimir-les, publicar-les ni executar `rotateSecret_`. El codi nou permet que el dispositiu actualitzat faci la lectura de fotos amb la clau familiar sense invalidar la clau de només lectura antiga.

## Fotos i carpeta existent
`Fotos.gs` defineix `photoFolder_()`; per tant, la versió unificada de `Code.gs` **ja no la redefineix**. Aquest conflicte era crític: el projecte real executa tots els fitxers `.gs` dins d'un espai global compartit.

- `PHOTO_FOLDER_ID` continua apuntant a la carpeta de les **sis fotografies de prova**. No es crea una carpeta nova.
- Les fotografies són fitxers de Drive, no registres d'una pestanya `Fotos`. No s'ha de crear ni indexar cap foto en un Sheets.
- L'API antiga `photoApiAction_` accepta JPEG, PNG i WebP, fins a **5 MiB** i un màxim de 300 fitxers. La nova API respecta aquests formats i llegeix les fotos directament de la carpeta; la nova pàgina mòbil manté la pujada d'originals JPG, PNG i WebP fins a 5 MB i 20 imatges per tanda amb `uploadFamilyPhoto`.
- L'Android unificat mostra com a màxim 120 fotos compartides per no omplir el dispositiu i desa les descarregades en memòria privada.
- La nova API `photo_get` valida que l'ID sol·licitat és realment dins de la carpeta; no pot llegir fitxers arbitraris de Drive.
- `prepararFotosFamiliars()` només consulta i compta les fotos compatibles: **no crea carpetes, no toca Sheets, no modifica les claus**.

## Tasques.gs revisat · esquema real i seguretat
El propietari ha facilitat **`Tasques.gs`**. Ja sabem que la pestanya `Tasques` té exactament les columnes `id,titol,punts,activa,frequencia,dies,franges`; `TasquesFetes` té `data,tascaId,persona,punts,franja`. La versió inicial unificada assumia erròniament que el sisè camp era la persona, cosa que podia **perdre programes setmanals i atribuir punts de manera incorrecta**.

- Freqüències admeses: `daily`, `weekdays`, `weekly`, `once`, `slots`. Les feines de diversos horaris només poden validar-se amb una franja seleccionada al gestor complet; l'Android les mostra però no les aprova incorrectament.
- El servidor només adjudica tasques a Rai segons el model existent. Per compatibilitat, l'Android connectat només permet crear tasques de Rai; les tasques locals de la família romanen a la còpia de seguretat del dispositiu.
- Les recompenses **no es descompten quan se sol·liciten**: creen una petició `pendent` a `PeticionsPremis`, amb ID idempotent. Només `familyTasksAction(..., 'rewards_decide', {id,pin,approve})` pot aprovar o denegar-les amb el hash del PIN `FAMILY_ADULT_PIN_HASH`.
- El nou `Mobile.html` de la branca incorpora l'entrada de PIN temporal per aprovar el premi. El PIN no s'emmagatzema ni s'afegeix a la cua de sincronització. El PIN **local** de l'Android i el PIN **del servidor** poden ser diferents i no s'han de confondre.
- `readFamily_` es basa en `familyTasksSnapshot_` del fitxer original, sense inventar un model paral·lel.
- **Defecte detectat al Tasques.gs aportat:** `taskSchedule_` tractava `['única']` com a franja invàlida per a tasques sense horaris múltiples, i podia impedir crear `daily`, `weekly` o `once`. La còpia proposada a `sync/google-apps-script/Tasques.gs` **corregeix només aquest validador** i ha estat inclosa en les proves. La versió publicada encara no s'ha canviat.
- Les accions antigues `family_tasks` i les noves `sync` continuen disponibles. Les proves simulades executen `Tasques.gs` amb les taules de proves, incloent franges, setmanals, PIN erroni i aprovat, historial i reintents.

## Mobile.html revisat · les funcionalitats originals es mantenen
L'usuari ha aportat el `Mobile.html` de producció amb navegació inferior entre quatre pestanyes: **Compra**, **Sopars**, **Fotos** i **Rai**. La branca ha adaptat la seva pàgina per conservar aquesta disposició i les interaccions reals: tasques `daily`, `weekdays`, `weekly`, `once`, `slots`; selector de dies i franges; `tasks_done` i `tasks_undo`; `rewards_claim` i `rewards_decide`; actualització automàtica; i pujada de 20 originals JPG/PNG/WebP de fins a 5 MB amb `uploadFamilyPhoto`. També hi afegeix la galeria visualitzable de les fotos compartides.

El sistema de premis és del servidor: els punts només es descompten quan l'adult aprova amb `FAMILY_ADULT_PIN_HASH`. El PIN s'introdueix temporalment a la pàgina mòbil i **no es desa**. El marc Android mostra una petició pendent i no l'aprova localment quan està connectat a Sheets.

## Condicions abans de publicar
1. Desar còpies privades de `Code.gs`, `Fotos.gs`, `Mobile.html` i `Tasques.gs`. La còpia de seguretat del Sheets del 28/10/2026 ja existeix.
2. Revisar les col·lisions de constants i funcions globals, i les convencions d'autenticació de tasques.
3. Fer proves en una **còpia d'Apps Script i del Sheets** sense tocar producció.
4. Si tot passa, publicar una nova versió de l'Apps Script existent i verificar la mateixa URL /exec i la mateixa clau familiar.
5. Instal·lar l'APK unificat amb una signatura compatible, sense desinstal·lar l'app actual.

**Estat:** els quatre fitxers aportats han estat revisats i la branca disposa de proves simulades de JavaScript i Android CI. Això **no equival encara a una prova real de l'Apps Script desplegat**, ni implica que s'hagi modificat la implementació en producció.
