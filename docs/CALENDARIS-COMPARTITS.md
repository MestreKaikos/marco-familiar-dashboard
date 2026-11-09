# Calendaris compartits amb el marc

A la web: Ajustos de sincronització → Calendaris del marc. Es poden afegir fins a deu calendaris iCal de Google, editar-ne el nom/enllaç/color, activar-los, pausar-los i eliminar-los. Es conserven les cinc seccions de la PWA.

## Activació

1. Substituir `Code.gs` i `Mobile.html` a l'Apps Script existent per les versions d'aquesta branca. Conservar `Fotos.gs`, `Tasques.gs`, les propietats i les claus existents.
2. Publicar una versió nova de la mateixa implementació `/exec`. No crear una URL nova.
3. Instal·lar l'APK nova com a actualització, sense desinstal·lar l'anterior.
4. Obrir l'app i prémer Actualitzar. La primera sincronització fusiona els calendaris locals amb els compartits; després apareixen als ajustos de la web.

El servei crea la pestanya privada `Calendaris` al mateix Sheets quan rep una consulta autenticada. Les cites no es copien al Sheets: el marc continua consultant els iCal originals. No cal Firebase, cap clau nova ni canviar la variable d'entorn de Vercel.

## Compatibilitat i conservació

- Amb el backend anterior, els calendaris continuen locals i la nova secció web informa de l'actualització pendent.
- La migració només incorpora calendaris que falten. No sobreescriu els compartits; les eliminacions es conserven perquè un dispositiu antic no les desfaci en importar.
- Desactivar un calendari conserva el seu nom, URL i color. Una llista compartida buida explícita també es conserva en reiniciar Android.
- Els canvis utilitzen la cua persistent i els intervals familiars existents. Actualitzar ara força una consulta.
- Els enllaços privats no es mostren a les llistes ni es registren en logs. El formulari els oculta fins que es prem Mostrar l'enllaç; la clau familiar autoritza l'accés al servei.

## Validació

Proves Node del servei i de les dues interfícies: autenticació, URL i color vàlids, compatibilitat antiga, cua sense connexió, imports repetits, colors, activació i eliminacions. Proves Android d'iCal: validació d'enllaços, calendaris desactivats i límit de deu. La compilació signada s'executa a GitHub Actions amb els secrets estables ja existents.
