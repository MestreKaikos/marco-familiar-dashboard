# Marc Familiar · Tasques i recompenses (fase de proves)

Aquesta fase reutilitza **el mateix projecte Google Apps Script i el mateix Google Sheets**. No publicar fins haver comprovat les operacions amb dades de prova.

## Fitxers
1. Afegeix `Tasques.gs` al projecte Apps Script existent copiant [Tasques.gs](Tasques.gs).
2. Substitueix `Mobile.html` per la nova versió de [Mobile-complet.html](Mobile-complet.html) (inclou compra, sopars, fotografies, tasques i premis).
3. No modifiquis `Code.gs`, `Fotos.gs`, `KEY_HASH` ni les propietats de fotos. La web mòbil invoca directament `familyTasksAction` amb `google.script.run`.
4. A l'editor executa `setupFamilyTasks` un cop: crea les pestanyes Tasques, TasquesFetes, Recompenses, CanvisPunts, PeticionsPremis.

## PIN d'adult
Per configurar el PIN, obre `Tasques.gs` i afegeix temporalment al final:

```javascript
function inicialitzarPinAdult() {
  configurarPinAdult("SUBSTITUEIX_AQUI_PER_UN_PIN_PRIVAT_DE_4_A_8_XIFRES");
}
```

**Tria un PIN privat**, executa manualment `inicialitzarPinAdult` i elimina tot seguit aquesta funció temporal del codi. El servidor desa només SHA-256 del PIN a propietats de script, però un PIN curt té entropia limitada; aquesta és protecció bàsica familiar, no autenticació forta. No publiquis el PIN ni el mostris en captures.

## Funcionament
- El saldo mostra punts de tasques completades menys punts de recompenses aprovades.
- Una tasca només dona punts un cop per dia, i es pot desfer aquell dia.
- El bescanvi crea una petició **pendent** i reserva punts disponibles; no els descompta.
- Només l'aprovació amb PIN carrega els punts; el rebuig allibera la reserva.
- La clau familiar dona accés a la gestió de tasques i a l'enviament de peticions. Actualment no separa perfils adult/nen; **no compartir aquesta clau amb l'infant** com si fos un accés de menor restringit.
- La pantalla Android de tasques encara fa servir el servei antic; no queda integrada amb aquest backend fins a la fase següent.

## Proves abans de publicar
1. Afegir una tasca de 5 punts.
2. Marcar-la dues vegades: només sumar 5.
3. Crear una recompensa de 5 punts i sol·licitar-la: saldo igual, petició pendent.
4. Rebutjar-la: saldo igual i petició tancada.
5. Tornar-la a sol·licitar i aprovar-la amb PIN: saldo baixa 5.
6. Revisar compra, sopars i pujada múltiple de fotos abans d'actualitzar el desplegament existent.

## Recurrències (nova versió)
- `daily`: cada dia, 1 execució/dia.
- `weekdays`: només els dies triats (1=dilluns, 7=diumenge).
- `weekly`: els dies seleccionats de cada setmana (normalment un dia).
- `once`: una sola execució en total, que desapareix de la llista d'avui en completar-la.
- `slots`: fins a quatre franges (matí, migdia, tarda, vespre), cadascuna atorga punts un cop al dia.

Les tasques antigues sense freqüència es continuen interpretant com a diàries. Les taules existents de Sheets reben les columnes addicionals en escriure/consultar, sense eliminar files ni punts històrics. Per activar les novetats, cal actualitzar **Tasques.gs i Mobile.html** al projecte Apps Script, executar `setupFamilyTasks` de nou (és idempotent) i editar la **implementació existent** amb Versió nova. **La pantalla Android encara no representa aquest nou sistema de tasques**; aquesta fase és de web mòbil.
