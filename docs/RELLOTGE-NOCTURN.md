# Rellotge nocturn configurable

Android: Opcions → Mode nocturn. L'horari de pantalla existent es conserva. Amb l'horari activat, «Rellotge nocturn» tria entre rellotge i apagada real (aquesta continua requerint el mode quiosc).

- Digital, apilat o analògic; sense segons.
- Tipografies moderna (Geist), clàssica (serif), digital mono (Geist Mono) i compacta del sistema. Fi, normal o negreta; a l'analògic s'ajusta el gruix de les busques.
- Colors predeterminats o RGB hexadecimal, mida i intensitat del color independents de la brillantor de pantalla.
- Preferències locals `marc_night_clock`, conservades en actualitzar. Per defecte: digital fi, gris al 28%, mida 65%, brillantor 10/255. Límit nocturn 1–30%; el mínim físic depèn del panell.
- Previsualització a Opcions i prova a pantalla completa amb la brillantor nocturna real, sense canviar l'horari ni la brillantor diürna. Un toc o enrere tanca la prova.
- Un toc al rellotge real conserva la pausa temporal de l'horari existent. L'horari nocturn té prioritat també sobre la galeria manual. Fotos automàtiques s'aturen de nit i es reprenen després de la inactivitat configurada.
- Desplaçament de fins a 8 píxels cada cinc minuts, sense animació contínua ni segons. Dibuix analògic amb Canvas, fonts ja incloses o del sistema; cap nova xarxa, backend o credencial.

Validació: proves de preferències invàlides, límits, RGB i estils; compilació signada estable; captures en emulador Android 10 amb preferències fictícies.
