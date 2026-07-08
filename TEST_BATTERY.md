# Batería de tests — ChestSeparators (via MC-Claude MCP)

Batería completa de tests del mod, ejecutable con la extensión MC-Claude MCP
(`http://127.0.0.1:8722/mcp`). Ver `GUIA_AGENTES.md` del proyecto de la extensión.

**Marcadores de verificación:**
- `[JSON]` — determinista: actuar y leer el estado resultante en JSON.
- `[CLIC]` — clic simple (`click_widget`).
- `[DRAG]` — arrastre en GUI (`drag_gui`).
- `[KEY]` — keybind/atajo/modificador (`key_action`).
- `[SHOT]` — juicio visual (`take_screenshot`).

**Truco clave para verificar sin captura:** contenido de un cofre con
`execute_command` → `/data get block X Y Z Items`; inventario con `get_inventory`.

**Keybinds por defecto del mod:** `O` panel editor · `Alt` (mantener) mostrar panel ·
`H` toggle botones edición · `G` toggle botón depósito · `L` lupa · `S` deposit-filtro ·
`D` deposit-all · `C`+Shift deposit-junk · `V`+Shift grab · doble-tap-sneak auto-deposit.
Dentro del editor: `Z` undo · `Y` redo · `C` copy · `V` paste · `Esc` salir.

---

## 0. Harness / escenario reproducible `[JSON/KEY]`
- **T1** — `list_worlds` → `open_world <id>` de un mundo de pruebas fijo; esperar `player_present:true`.
- **T2** — Aislar escenario: `set_game_mode creative` + `clear_inventory` + `teleport_player` a zona plana; `get_player_info` de referencia.
- **T3** — Plantar banco de cofres de test: `set_block` de cofre/barril/shulker en coords conocidas.
- **T4** — Helper de lectura de cofre: `/data get block X Y Z Items` devuelve el contenido.
- **T5** — Reset entre tests: `/kill @e[type=item]` + `clear_inventory`.

## A. Arranque, registro y estabilidad `[JSON]`
- **T6** — `read_log_tail 300`: sin `Mixin apply failed`, `InvalidInjectionException`, ni stacktrace de `chestseparators`.
- **T7** — Mixins clave cargados: `InGameHudHotbarLinesMixin`, `PlayerScreenHandlerFallbackMixin`, `ArmorEquipClientMixin`, `GenericContainerScreenMixin`, `ShulkerAnimationAccessor`.
- **T8** — Access widener sin warnings (insertItem/selectedSlot accesibles).
- **T9** — `get_registered` para confirmar contenido/keybinds registrados.
- **T10** — Sin spam por frame en idle (`read_log_tail` tras esperar).
- **T11** — Ciclo `quit_game` → relanzar → `open_world`: recarga sin crash ni reset de config.

## B. Keybinds, contexto y modificadores `[KEY/JSON]`
- **T12** — `key_action key:"o"` en mundo → `context:"world"` y abre editor; repetir cierra.
- **T13** — `key_action` atajo en GUI abierta → `context:"gui"` (`escape` cierra editor).
- **T14** — Modificador real: `key_action key:"c" modifiers:["shift"]` → `modifiers_down` incluye shift.
- **T15** — `H` toggle botones edición (action bar on/off).
- **T16** — `G` toggle botón depósito (on/off, persiste).
- **T17** — `L` toggle lupa (on/off).
- **T18** — `Alt` mantener (`down`) muestra panel; (`up`) lo oculta.
- **T19** — Doble-tap sneak: `count:2 interval_ms:150` dispara auto-deposit.
- **T20** — Umbral: `interval_ms:600` NO dispara el gesto.
- **T21** — Feedback G/H/O visible con cofre abierto (regresión).
- **T22** — Rebind: cambiar `O` a otra tecla y confirmar respuesta a la nueva.

## C. Editor: apertura, panel y navegación `[KEY/CLIC/SHOT]`
- **T23** — Abrir editor con inventario propio (`E`) y con cofre; panel visible.
- **T24** — Cambiar pestañas Líneas/Fondo/Combo; tab activa resaltada.
- **T25** — Seleccionar cada herramienta (lápiz-área, lápiz-trace, goma-área, goma-trace, cuentagotas).
- **T26** — Paleta: clic en cada color → mensaje "color copiado en su propio color".
- **T27** — Sin flechas de navegación en menú de config del editor (regresión).
- **T28** — `Esc` cierra el editor sin cerrar el contenedor.

## D. Líneas — Área `[DRAG→SHOT]`
- **T29** — Rectángulo 3×3 en cofre: solo contorno exterior.
- **T30** — Línea 1D horizontal (una fila).
- **T31** — Línea 1D vertical (una columna).
- **T32** — Intención "outer": drag hacia fuera expande al borde externo.
- **T33** — Orden de sobreescritura en esquinas: mayor seq gana, sin doble-pintado (regresión).
- **T34** — Bordes del grid (fila/col 0 y última) sin desborde.
- **T35** — Doble cofre (54): rectángulo de 6 filas.
- **T36** — Preview == commit (captura a mitad de drag vs tras soltar).

## E. Líneas — Trace `[DRAG→SHOT]`
- **T37** — Trace en L: conecta en la esquina.
- **T38** — Rail lock: drag recto pinta un solo eje.
- **T39** — Transición de eje al desviar >7.5px.
- **T40** — Rollback por retroceso del cursor.
- **T41** — Preview trace == commit.

## F. Fondo `[DRAG→SHOT]`
- **T42** — Relleno de área rectangular; color/transparencia correctos.
- **T43** — Fondo bajo los items del inventario.
- **T44** — Fondo en trace (celda a celda).
- **T45** — Respeta `bgTransparency`.

## G. Combo `[DRAG→SHOT]`
- **T46** — Combo área: fondo + caja completa del rectángulo.
- **T47** — Combo trace: fondo + aristas, suprimiendo compartidas.
- **T48** — Fix doble-pintado de esquina en combo/borrado (regresión).

## H. Goma y previews `[DRAG→SHOT]`
- **T49** — Goma líneas borra solo líneas.
- **T50** — Goma fondo borra solo fondo.
- **T51** — Goma combo borra líneas+fondo.
- **T52** — Preview goma (wash) solo donde hay algo que borrar.
- **T53** — Atenuación del preview Clear igual que la goma (regresión).
- **T54** — Restaurar color previo tras usar goma (regresión).
- **T55** — Preview botón Clear con hover (lines/bg/combo) (regresión).

## I. Cuentagotas + Lupa `[CLIC/KEY/SHOT]`
- **T56** — Cuentagotas coge color de slot pintado.
- **T57** — Lupa (`L`) aparece y sigue al cursor.
- **T58** — Lupa del eyedropper: zoom de píxeles real (regresión).
- **T59** — Lupa de lápices/slots mejorada (regresión).

## J. Copy / Paste / Undo / Redo `[KEY→SHOT]`
- **T60** — Copy (`C`) copia diseño del cofre.
- **T61** — Paste (`V`) a otro cofre distinto.
- **T62** — Undo (`Z`) revierte; Redo (`Y`) reaplica.
- **T63** — Feedback undo/redo: parpadeo por slot según tipo (regresión).
- **T64** — Pila: 10 acciones → 10 undo → 10 redo.
- **T65** — Paste en cofre de tamaño distinto (sin crash).
- **T66** — Ctrl+Z / Ctrl+Y si aplica.

## K. Presets `[CLIC/KEY/SHOT]`
- **T67** — Guardar preset inv. slot 1 → indicador vacío→tick.
- **T68** — Cargar preset inv. → aplica y oculta editor.
- **T69** — Guardar/cargar preset de cofre.
- **T70** — Preview al hover en Load: alterna layout↔filtros (~3s).
- **T71** — Ghost tick al hover en Save: vacío→semitransparente; guardado→atenuado.
- **T72** — Líneas del inventario de fondo en menú presets (no durante preview Load).
- **T73** — Sin doble-dibujado de separadores en menú presets (regresión).
- **T74** — `inventoryPresetCount` cambia filas (1–12).
- **T75** — Persistencia de presets tras reinicio.
- **T76** — Sobrescribir preset guardado.

## L. Filtros / Whitelist `[JSON/KEY]`
- **T77** — Crear filtro en slot de cofre para `minecraft:diamond`.
- **T78** — Reset de pestaña de regla por defecto al crear filtro (regresión).
- **T79** — Bloqueo: `minecraft:dirt` no entra en slot filtrado.
- **T80** — Permitido: `minecraft:diamond` entra.
- **T81** — Shift-click filtrado: item no permitido no aterriza en slot filtrado.
- **T82** — Sin player head en "Allow All" (regresión).
- **T83** — Grupos multi-slot con la misma whitelist.
- **T84** — Scroll de lista de filtros hasta el final (regresión).
- **T85** — Auto-focus de search box al teclear (regresión).
- **T86** — Sin recorte del último item del panel de filtros (regresión).
- **T87** — Persistencia de filtros por cofre tras reinicio.

## M. Armadura y offhand `[JSON/DRAG/SHOT]`
- **T88** — Equipar armadura filtrada por clic-derecho bloqueado sin flicker.
- **T89** — Shift-click de armadura filtrada → fallback a inventario/hotbar.
- **T90** — Líneas-área sobre armadura: caja completa; nada en medio del inventario (regresión).
- **T91** — Preview líneas-área en armadura == commit (regresión).
- **T92** — Líneas-trace en armadura respeta rail lock (regresión).
- **T93** — Fondo y combo en armadura preview==commit.
- **T94** — Offhand como celda aislada igual que armadura.

## N. Depósito / Grab `[JSON/KEY]`
- **T95** — `S` deposit-filtro: deposita solo lo que el cofre "quiere".
- **T96** — `D` deposit-all.
- **T97** — Doble-tap sneak auto-deposit.
- **T98** — `C`+Shift deposit-junk.
- **T99** — `V`+Shift grab.
- **T100** — Botón depósito en GUI (con `G` on).
- **T101** — Stacks parciales: solo lo que cabe.
- **T102** — Sin cofre cercano: no-op sin crash.
- **T103** — Grab desde múltiples cofres.
- **T104** — Deposit-junk sin destino válido no borra items.

## O. HUD hotbar `[SHOT/JSON]`
- **T105** — Líneas/fondo pintadas en hotbar aparecen en HUD.
- **T106** — Backgrounds bajo los items en HUD.
- **T107** — Slot seleccionado: recuadro vanilla intacto.
- **T108** — Todos los slots iguales (regresión).
- **T109** — Cambiar slot: highlight limpio se mueve.
- **T110** — Transparencias del HUD respetan config.
- **T111** — Editar inventario → HUD en tiempo real.
- **T112** — HUD sin datos no dibuja nada.

## P. Config / ModMenu `[CLIC/DRAG/JSON]`
- **T113** — Abrir config ModMenu: widgets listados.
- **T114** — Toggle Dark Mode.
- **T115** — Slider `lineTransparency` 0/50/100.
- **T116** — Slider `bgTransparency` 0/50/100.
- **T117** — Colores custom compartidos vs por-tab (regresión).
- **T118** — `inventoryPresetCount` 1 y 12 (límites).
- **T119** — Toggle botón depósito desde config == hotkey `G` (regresión).
- **T120** — Rebind persiste tras reinicio.
- **T121** — Valores fuera de rango se clampan.

## Q. Persistencia y ficheros `[JSON]`
- **T122** — Config global persiste tras reinicio.
- **T123** — Diseño por-cofre persiste por posición/tipo.
- **T124** — Perfil de inventario persiste (hotbar incluida).
- **T125** — Ficheros de preset se escriben/leen.
- **T126** — Borrar config a mano → regenera sin crash.
- **T127** — Config corrupta → manejo elegante, sin crash.

## R. Compatibilidad de contenedores `[SHOT/JSON]`
- **T128** — Cofre simple (27). **T129** — Cofre doble (54). **T130** — Trapped chest.
- **T131** — Barril. **T132** — Shulker box. **T133** — Ender chest.
- **T134** — Hopper (5 slots). **T135** — Dispenser/Dropper (3×3).
- **T136** — Contenedor modded genérico: degradación elegante.

## S. Estrés y rendimiento `[DRAG/JSON/SHOT]`
- **T137** — Pintar TODO un doble cofre: sin caída de FPS ni artefactos.
- **T138** — Cambio rápido de pestañas repetido.
- **T139** — Undo/redo 50 veces.
- **T140** — Abrir/cerrar editor 30 veces.
- **T141** — Depósito masivo (inventario lleno).
- **T142** — Muchos cofres con filtros + grab.
- **T143** — Preset 12 repetido con inventario complejo.
- **T144** — `drag_gui` con `steps` muy altos.

## T. Multiplayer / servidor dedicado `[JSON/KEY]`
- **T145** — Servidor + cliente: filtros y fallback armadura en red.
- **T146** — Deposit/grab con latencia.
- **T147** — Dos clientes editando cofres distintos.
- **T148** — Cliente sin mod ↔ servidor con mod: sin kick.
- **T149** — Separadores client-side: diseño local.
- **T150** — Reconexión conserva config local.
- **T151** — Sesión larga: log sin excepciones acumuladas.

## U. Robustez de capacidades MCP aplicadas al mod `[JSON/KEY/DRAG]`
- **T152** — `key_action` cambia de contexto al abrir/cerrar GUI a mitad de secuencia.
- **T153** — Modificador no se queda "pegado" tras shift down/up.
- **T154** — `drag_gui` con `button:right`.
- **T155** — Interrumpir `drag_gui` cerrando la GUI: sin arrastre colgado.
- **T156** — `open_world` tras `quit_game` con config intacta.
- **T157** — Combos rápidos copy→paste→undo sin perder pasos.

## V. Casos límite / adversariales `[JSON/DRAG/SHOT]`
- **T158** — Pintar y borrar el mismo slot en el mismo drag.
- **T159** — Filtro con item con NBT/componentes vs sin NBT.
- **T160** — Filtro con item no apilable y stacks grandes.
- **T161** — Preset con 0 líneas carga sin residuos.
- **T162** — Cofre destruido con config existente: sin crash.
- **T163** — Mover cofre con líneas a otra posición.
- **T164** — Inventario lleno al hacer grab: sin pérdida de items.
- **T165** — Deposit con 1 hueco en el cofre.
- **T166** — Editor abierto y recibir daño: sin crash.
- **T167** — Dos grupos de filtros en slots adyacentes: sin fuga de aristas.
- **T168** — Reducir `inventoryPresetCount` con presets altos guardados: sin pérdida.
- **T169** — Transparencia 0%: invisible pero datos intactos.
- **T170** — Transparencia 100%: opacidad total sin artefactos.
- **T171** — Traducciones: action bars y tooltips con claves `gui.chestseparators.*`.
- **T172** — Smoke de regresión: T6, T21, T33, T48, T73, T89, T90, T92, T95, T108.
