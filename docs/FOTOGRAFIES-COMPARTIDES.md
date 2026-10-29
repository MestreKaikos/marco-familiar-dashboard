# Marc Familiar · fotografies del Drive existent

**Nova branca de proves:** [feature/marc-familiar-unificat](https://github.com/MestreKaikos/marco-familiar-dashboard/tree/feature/marc-familiar-unificat).

## Arquitectura real revisada

El projecte Apps Script del propietari ja té un fitxer **`Fotos.gs`** funcional i una carpeta privada de Drive indicada per `PHOTO_FOLDER_ID`, amb sis fotografies de prova.

- El **fitxer `Fotos.gs` original es conserva**. No cal crear cap carpeta nova ni cap pestanya `Fotos` al Google Sheets.
- La clau familiar (validada amb `KEY_HASH`) ja permet pujar fotos mitjançant `uploadFamilyPhoto`, i la branca unificada també l'accepta per a les noves accions `photo_list`, `photo_get`, `photo_upload` i `photo_delete`.
- La clau antiga **de només lectura** (validada amb `PHOTO_READ_KEY_HASH`) continua habilitada per al marc antic: `photos_list`, `photos_get`. Les claus són dues i **no cal canviar-les ni generar-ne cap altra**; la nova APK només configura la clau familiar.
- Les fotografies **JPG, PNG i WebP** de fins a **5 MB** són consultables directament; no es mouen ni reindexen. La nova pàgina mòbil conserva el **mateix sistema original de pujada amb `uploadFamilyPhoto`**: fins a 5 MB per fotografia i 20 fotografies per tanda.
- El codi nou reutilitza `photoFolder_()` de `Fotos.gs`, sense redefinir-la ni causar conflicte entre fitxers globals.
- El marc mostra **com a màxim les 120 fotos més recents en memòria cau** i baixa 12 de noves en cada refresc, per protegir el dispositiu Android. El servidor continua acceptant fins a 300 fitxers, igual que el `Fotos.gs` original.

## Abans de desplegar

1. Ja s'han comparat els quatre fitxers reals compartits (`Code.gs`, `Fotos.gs`, `Tasques.gs` i `Mobile.html`). No toquis encara el servei actual: la validació integrada en una còpia del projecte és el següent pas.
2. Desa còpies privades de tots els fitxers actuals. La còpia independent del Google Sheets ja existeix.
3. Conserva els dos hashes i la propietat `PHOTO_FOLDER_ID`; no executis `rotateSecret_` ni `setupPhotos_` per generar claus noves.
4. Verifica en una còpia d'Apps Script que `prepararFotosFamiliars` només consulta la carpeta existent i mostra el recompte al registre.
5. Quan la versió sencera estigui validada, publica una nova versió de la **mateixa** implementació web /exec i instal·la una APK amb la signatura correcta.

## Privadesa

La carpeta continua privada. La clau familiar permet llegir les fotografies i pujar-ne de noves, però **la galeria mòbil no ofereix cap botó d'esborrar**. La nova API `photo_delete` rebutja explícitament els fitxers originals i els procedents de la pujada antiga, i només permet eliminar els fitxers creats pel nou `photo_upload`. No hi ha permisos diferents per membre en aquesta primera versió. No es publiquen claus, IDs privats ni fotos personals al repositori.

Consulta també [la guia unificada](MARC-FAMILIAR-UNIFICAT.md) i [la compatibilitat amb l'Apps Script actual](COMPATIBILITAT-APPS-SCRIPT-ACTUAL.md).
