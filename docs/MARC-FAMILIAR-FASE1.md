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
