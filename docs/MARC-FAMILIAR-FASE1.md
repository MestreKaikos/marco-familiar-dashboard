# Marc Familiar · primera fase

Adaptació del projecte [leontdi/marco-familiar-dashboard](https://github.com/leontdi/marco-familiar-dashboard), que incorpora el treball de [eusonlito/Planify-Dashboard](https://github.com/eusonlito/Planify-Dashboard).

## Implementat en aquesta branca

- Pestanya **Fotos** amb tria de carpeta local mitjançant el selector del sistema Android (Storage Access Framework).
- Permís de lectura limitat a la carpeta triada; es conserva entre reinicis, sense accés global a l'emmagatzematge.
- Presentació automàtica cada **20 segons**, navegació manual, una sola miniatura en memòria i contingut ajustat a pantalla.
- Accés a la galeria sense iniciar sessió al servidor de Planify.
- Primera traducció al **català** (menús, diverses pantalles i dates).
- Suport al selector DocumentsUI quan el marc funcioni en mode quiosc.

## Proves pendents

Aquest codi **encara no s'ha compilat ni comprovat en un emulador Android ni al XORO**. Abans de fusionar aquesta branca:
1. Executar la compilació debug amb JDK 17 i Android SDK compatible amb el projecte.
2. Comprovar la barra lateral, l'accés a Fotos abans d'iniciar sessió i la navegació.
3. Seleccionar una carpeta d'imatges a l'emulador d'Android 10, provar la persistència del permís, el pas automàtic de fotos i el canvi manual.
4. Comprovar el funcionament en mode quiosc; en algunes variants d'Android el selector pot dependre d'un paquet diferent de `com.android.documentsui`.
5. Revisar els textos restants que encara apareguin en castellà.

## Propera fase

- Acabar la traducció, especialment les pantalles de domòtica i meteorologia.
- Preparar el calendari familiar en iCal i el menú escolar, sempre amb configuració privada.
- Estudiar un salvapantalles fotogràfic amb control d'activitat i horaris.
- Deixar una pestanya futura per SmartThings, però no guardar tokens ni credencials al codi.

## Seguretat del dispositiu

**No executeu `scripts/install.sh` fins que tinguem el marc físic.** Cal verificar el model i firmware exactes, fer una còpia de seguretat de la memòria interna i seguir les fases de la guia original. No pengeu claus, calendaris iCal privats, fotos familiars ni còpies del firmware al repositori públic.

La llicència MIT i els crèdits dels autors originals s'han de conservar.


## Fase següent · inici sense usuari ni contrasenya

- L'aplicació obre directament la portada **Avui** sense haver de crear un compte de Planify.
- Sense calendaris configurats, es mostra una setmana buida amb un avís clar, no es fan servir dades inventades.
- A la portada hi ha accessos a les **Fotos**, a la configuració d'**El temps** i al **Calendari**.
- La galeria continua funcionant sense xarxa. La meteorologia s'obté directament amb Open-Meteo quan es tria una ciutat a **Opcions**.
- Les dades del calendari real encara requereixen configurar fonts iCal; no es demana contrasenya de Google.
- El codi antic de Planify encara existeix per compatibilitat, però no es mostra la pantalla d'autenticació ni s'utilitza el backend sense una sessió configurada.
- La funció de rellotge nocturn també pot funcionar en mode local.

### Prova en Android real

1. A l'apartat **Actions** de GitHub, obre la compilació més recent que hagi acabat amb èxit.
2. Descarrega l'artefacte `marc-familiar-android-debug` i extreu-ne l'APK.
3. **Si ja tens una APK de prova anterior**, és possible que Android no permeti instal·lar la nova al damunt: els certificats debug dels runners de GitHub poden ser diferents. En aquest cas, desinstal·la la versió anterior. Això elimina la carpeta triada i els ajustos locals de l'app; torna a seleccionar les fotos després de reinstal·lar.
4. Obre Marc Familiar: la pantalla principal ha d'aparèixer **sense contrasenya**. Prem **Fotos** per triar la carpeta local.
5. Prem **Opcions** per buscar la ubicació meteorològica. El calendari familiar encara es mostrarà com a pendent.
6. **No introdueixis cap credencial de Google, Samsung ni SmartThings** dins l'APK de prova.

El projecte està preparat per a 1920 × 1200 en horitzontal. La interfície pot quedar retallada en un telèfon; la prova del mòbil confirma la funcionalitat bàsica, no la maquetació final del XORO.


## Planificació del sopar familiar (fase següent)

- **No mostrem el menú escolar.** La part inferior de cada dia del calendari mostra el sopar familiar.
- A la portada, la targeta discreta «Què sopem avui?» permet editar el sopar del dia.
- Tocant la pastilla «🍽️ SOPAR» de qualsevol dia (portada o calendari), s'obre un editor de text; «Desar» guarda el plat i deixar el camp buit l'elimina.
- Les dades es guarden a les preferències **locals del dispositiu**, indexades per data real; sobreviuen als reinicis, però no es sincronitzen entre dispositius i es perden en desinstal·lar l'APK.
- No cal iniciar sessió a Planify ni connectar Google Calendar per fer servir els sopars.
- Les captures automàtiques de GitHub continuen sense dades familiars; el nou editor es mostra a la galeria de captures.
