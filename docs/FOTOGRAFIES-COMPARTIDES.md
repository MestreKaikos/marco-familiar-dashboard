# Marc Familiar · Fotografies compartides des dels mòbils

**Fase experimental integrada a la branca [feature/marc-familiar-unificat](https://github.com/MestreKaikos/marco-familiar-dashboard/tree/feature/marc-familiar-unificat).** Aquesta versió afegeix una galeria de fotografies enviades des del telèfon. Les imatges es guarden en **una carpeta privada del Google Drive del propietari** i es descarreguen al marc per mostrar-les també sense Internet.

## Què farà cada persona?

- Obre la mateixa URL `/exec` que ja fa servir per a la compra i els sopars.
- Introdueix la mateixa clau familiar (no s'inclou mai a l'URL).
- A «Fotografies familiars», tria fins a **cinc fotos per enviament** i prem **Enviar fotografies**.
- La pàgina redimensiona al navegador i converteix a **JPEG**, habitualment fins a 1600 píxels de costat llarg i com a màxim 1,1 MB per imatge. Aquesta conversió no transmet les metadades EXIF/GPS originals.
- Pot tocar **Veure** per previsualitzar una foto i **Eliminar** per suprimir-la del Drive familiar.
- A **Marc Familiar → Fotografies**, el marc mostra les fotos locals i les familiars barrejades en una presentació. Les imatges noves s'actualitzen amb l'app oberta a la pantalla de fotos, aproximadament cada 90 segons. Descarrega **fins a 12** fotos noves per ronda per no saturar el dispositiu.

## Activar-ho a la instal·lació que JA tens

**No creïs un altre Google Sheets, no generis una nova clau i no eliminis el projecte d'Apps Script.** S'amplia el servei que ja funciona.

1. Obre el teu Google Sheets personal «Marc Familiar - Dades» → **Extensions → Apps Script**.
2. Al fitxer `Code.gs`, substitueix el codi per la versió actualitzada de [Code.gs](../sync/google-apps-script/Code.gs).
3. Al fitxer `Mobile.html`, substitueix el codi per la versió actualitzada de [Mobile.html](../sync/google-apps-script/Mobile.html). Desa tots dos.
4. Al selector de funcions d'Apps Script, executa **`prepararFotosFamiliars`** una sola vegada. Google demanarà **nous permisos per a Google Drive** perquè el codi pugui crear la carpeta de fotos, escriure-hi fitxers i llegir-los. Revisa aquests permisos: el servei s'executa amb el teu compte i els permisos de Drive poden ser amplis. No cal tornar a executar `setup_` ni girar la clau.
5. Comprova que s'ha aprofitat la carpeta privada existent **«Marc Familiar · Fotos compartides»** i s'han indexat els JPG/PNG de prova. Si ja existia una configuració `PHOTO_FOLDER_ID` que apuntava a una altra carpeta, executa abans **`vincularCarpetaFotosExistents`** i després torna a executar `prepararFotosFamiliars`. No comparteixis públicament la carpeta ni el full.
6. A Apps Script, prem **Implementa → Gestiona les implementacions**, tria la implementació web que ja tens, **Edita** (llapis), a **Versió** tria **Nova versió** i prem **Implementa**. **És essencial publicar una versió nova:** desar el codi no actualitza automàticament l'URL `/exec`.
7. Obre al mòbil **la mateixa URL /exec** que ja tenies. La nova secció «Fotografies familiars» apareixerà a sota de compra i sopars. Introdueix la mateixa clau familiar si te la demana.
8. Envia una foto de prova i comprova que apareix al Drive, a la carpeta privada. Després instal·la l'APK validada de la branca [feature/marc-familiar-unificat](https://github.com/MestreKaikos/marco-familiar-dashboard/tree/feature/marc-familiar-unificat) **sobre la versió anterior**, sense desinstal·lar i comprovant-ne la signatura.
9. Al Marc Familiar, entra a **Fotografies → Actualitzar fotos**. Hauria d'aparèixer la foto de prova. Comprova també que surt després de tancar i obrir l'app o en quedar-te sense Wi-Fi.

## Com funciona la seguretat?

- **No hi ha cap URL de Drive pública.** Google Apps Script treballa com a propietari; la carpeta creada és privada.
- Els mòbils i el marc demanen les fotos amb la clau de sincronització al **cos de la petició**, mai a l'URL. El servidor verifica la clau abans de llegir o modificar fitxers.
- Al full **Fotos** es desa l'índex (identificador, nom, identificador privat de Drive i data). La pestanya és privada, com la de compra.
- Les fotos es copien a l'emmagatzematge privat del marc i es mantenen en desconnexió; **eliminar-les des del mòbil també n'elimina la còpia del marc quan es torni a sincronitzar**.
- Les persones que coneixen la clau familiar poden veure i **eliminar totes les fotos** compartides: actualment no hi ha permisos individuals. No comparteixis la clau fora del grup familiar.
- Es permeten fins a **120 fotos** compartides en aquesta primera fase. No hi pugis imatges o documents sensibles.
- Amb més permisos de Google Drive, el codi d'Apps Script ha de ser de confiança: no afegeixis scripts que no hagis revisat.

## Limitacions conegudes de la primera versió

- Els mòbils necessiten Internet per enviar imatges; el marc pot reproduir les que ja ha descarregat sense connexió.
- Les fotografies **locals** que ja tinguis al marc no es pugen automàticament a Drive ni es modifiquen.
- Aquesta fase no afegeix àlbums, edició ni comptes per usuari; és una galeria familiar compartida.
- Si canvia la clau familiar, tots els dispositius s'han de reconnectar com fins ara.
- Apps Script i Drive tenen quotes i límits de servei. És important provar primer amb una foto de prova.
- Si una foto no es pot comprimir en JPEG per sota del límit, la web n'informa i no l'envia.
- **No s'ha provat amb el Google Sheets personal de ningú des del codi de GitHub**; cal autoritzar-ho i validar la prova real al compte del propietari.

## Validació automàtica

La branca disposa de proves amb Drive simulat que verifiquen l'autenticació, la càrrega idempotent, la llista, la descàrrega, l'eliminació i els JPEG incorrectes, a més de la compilació Android i captures sense dades familiars. La prova real del Drive privat requereix la configuració manual descrita més amunt. Consulta també la [guia d'integració unificada](MARC-FAMILIAR-UNIFICAT.md).
