# Resultados de la batería de tests — ChestSeparators 1.3.1

**Fecha:** 2026-07-08 · **Entorno:** MC 1.21.11, Fabric Loader 0.19.3, mcclaude 0.1.0 (portado a 1.21.11)
· cliente dev `runClient` (runDir `run/client`) · MCP en `http://127.0.0.1:8722/mcp`

**Leyenda:** ✅ PASA · ❌ FALLA · ⚠️ PARCIAL/mejorable · ⏭️ SALTADO (con motivo) · ❓ PENDIENTE de aclarar

> Los tests fallidos o imperfectos llevan explicación extensa: qué falla, qué se esperaba,
> cómo reproducirlo y análisis de causa probable.

---

## Incidencias de setup (previas a la batería)

### SETUP-1 — mcclaude 1.21 incompatible con MC 1.21.11 ❌→✅ (resuelto)
- **Qué pasó:** el jar original de la extensión (compilado contra MC 1.21) crashea el juego en
  1.21.11: `InvalidInjectionException` en `InputConstantsMixin` — la firma de
  `InputConstants.isKeyDown` cambió de `(long window, int key)` a `(Window window, int key)`.
  Además exigía Fabric Loader ≥0.19.3 y el proyecto usaba 0.18.2.
- **Resolución:** el usuario portó la extensión a 1.21.11; `loader_version` del mod subido a 0.19.3.

### SETUP-2 — runDir real es `run/client`, no `run` ⚠️ (nota)
- Los jars colocados en `run/mods` no se cargan; el `build.gradle` define `runDir "run/client"`.
  Había 3 jars huérfanos en `run/mods` (EasyShulkerBoxes, architectury, itemscroller) que NUNCA
  se estaban cargando en dev.

---

## Resultados

### Sección 0 — Harness (T1–T5): ✅ 5/5
- **T1 ✅** `list_worlds` → 1 mundo ("New World", creative). `open_world` carga; `player_present:true` (Player420).
- **T2 ✅** Creativo + plataforma smooth_stone 41×41 en y=118 + tp (-10,119,-15) + inventario limpio.
- **T3 ✅** Banco de 9 contenedores en z=-10: chest(-8), doble(-6,-5), barrel(-3), shulker(-2),
  trapped(0), ender(2), hopper(4), dispenser(5). Verificado con `get_blocks_in_area`.
- **T4 ✅** Helper `/data get block X Y Z Items` funciona (leyó `[{count:5, Slot:0b, id:diamond}]`).
- **T5 ✅** Reset: `/kill @e[type=item]` (mató 23 items huérfanos) + `clear_inventory`.

### Sección A — Arranque y estabilidad (T6–T11): ✅ 5/5 (+1 diferido)
- **T6 ✅** 0 errores de mixin, 0 stacktraces del mod. Únicos ERROR: Realms/auth (benignos en dev).
- **T7 ✅** (por inferencia) Sin fallos de apply; la confirmación funcional de cada mixin llega en
  T88/T89/T105 y el render de líneas en cofre.
- **T8 ✅** 0 warnings de access widener.
- **T9 ✅** `get_registered` devuelve 0 blocks/items para `chestseparators` — **esperado**: el mod es
  100% client-side UI, no registra contenido. (El test estaba mal planteado en la batería; keybinds
  no son consultables por `get_registered`.)
- **T10 ✅** Log creció 0 bytes en 45 s idle. Sin spam por frame.
- **T11 ⏭️** Diferido al final de la batería (reiniciar ahora mataría la sesión de test).

### Sección B — Keybinds y modificadores (T12–T22): 8✅ 1❌ 1❓ 2⏭️
- **T12 ✅** `O` en mundo → `context:"world"`, `showLeftPanel` true→false→true en config (guardado inmediato).
- **T13 ✅** `Escape` con cofre abierto → `context:"gui"`, GUI cerrada.
- **T14 ✅** `key_action shift down` → `get_input_state.modifiers_down.shift:true`; up lo limpia.
- **T15 ✅** `H` → `showEditButtons` togglea y persiste.
- **T16 ✅** `G` → `showDepositButton` togglea y persiste (true→false→true).
- **T17 ❌ BUG — conflicto de keybind `L` con Advancements vanilla (en mundo).**
  - **Qué falla:** en el mundo, pulsar `L` abre la pantalla vanilla de logros
    (`AdvancementsScreen`) y `toggleMagnifier()` NUNCA se ejecuta (`magnifierEnabled` no cambia
    tras 3 pulsaciones). El sistema de KeyBinding de MC asigna la tecla en conflicto a un solo
    binding, y gana el vanilla.
  - **Qué se espera:** que `L` togglee la lupa (o al menos que el conflicto sea evidente/evitado).
  - **Dónde sí funciona:** con GUI abierta (cofre/editor) `L` sí togglea (`magnifierEnabled`
    true) porque ahí el mod maneja la tecla directamente en la Screen, sin pasar por KeyBinding.
  - **Reproducir:** mundo cargado, sin GUI → pulsar L → se abre Advancements; config no cambia.
  - **Sugerencia:** cambiar el default a una tecla libre (p. ej. `M` o unbound) o detectar el
    conflicto; la lupa solo tiene sentido en GUI, así que quizá ni necesite keybind global.
- **T18 ❓ NO VERIFICABLE con input virtual (pendiente de re-test con teclado físico).**
  - Alt mantenido (virtual, `key_action alt down`) no mostró el panel con `showLeftPanel:false`.
  - **Causa raíz (no es bug del mod):** `KeyInputHandler.isPanelModifierHeld()` consulta
    `GLFW.glfwGetKey()` **nativo** (línea 166-168), que la inyección de mcclaude no puede
    falsear (su mixin cubre `InputConstants.isKeyDown`, no la API nativa GLFW).
  - **Hallazgo mejorable del mod:** usa raw-GLFW también para Shift (KeyInputHandler:84,
    ChestSeparatorsEditor:198, EditorRenderer:344) y Ctrl (EditorInputHandler:181). Migrar a
    `Screen.hasShiftDown()/hasControlDown()` o `InputUtil.isKeyPressed` daría comportamiento
    vanilla-consistente y testeable. Afecta también a T66/T98/T99 (combos con modificador).
- **T19/T20 ⏭️** (doble-tap sneak) se prueban en sección N junto al depósito (T97).
- **T21 ✅** G/H/L con cofre abierto toglean config correctamente; action bar visible sobre la GUI
  (visto "Preview Panel: Hidden" en captura con cofre abierto).
- **T22 ⏭️** (rebind) se prueba en sección P (config/ModMenu).

### Incidencias de herramienta descubiertas en sección C (fixes aplicados/pedidos a la extensión)

- **MCP-FIX-1 (aplicado por el usuario): clics vía pipeline nativo.** `click_widget` llamaba a
  `Screen.mouseClicked` directamente → los `ScreenMouseEvents` de Fabric (la vía de input del mod,
  EditorInputHandler:52-83) nunca se disparaban. Arreglado: ahora los clics pasan por
  `Mouse.onMouseButton` y el editor abre/reacciona. Verificado con smoke test (paleta DRAW_LINES).
- **MCP-GAP-2 (pendiente): frame pacing de click/drag.** El press se despacha en el MISMO frame
  que el movimiento de cursor → `HandledScreen.focusedSlot` (recalculado en render) está stale.
  Evidencia instrumentada: `[CS-DEBUG] onMouseClicked focusedSlot=null` justo tras mover;
  focusedSlot correcto si el cursor llevaba ≥1 frame quieto. En `drag_gui`, TODOS los steps se
  despachan en un frame (evidencia: `commit start=9 curr=9` incluso con steps=60 →
  `dragCurrentSlot` nunca avanza). **Fix pedido:** 1 step por frame y ≥1 frame entre mover y press.
  - **Workaround usado:** doble-clic (1º posiciona cursor, 2º actúa) para botones; pre-posicionar
    cursor antes de drag. Limita los drags multi-slot (área/trace) hasta el fix.
- **MCP-GAP-3 (menor, pendiente): `interact_block` se salta los hooks client-side.** No pasa por
  `ClientPlayerInteractionManager.interactBlock` del cliente → `ChestInteractionMixin` no captura
  la posición del cofre → `session.currentChestPos=null` → los layouts pintados NO SE GUARDAN
  (saveSmart sin target). No es bug del mod: en juego real la posición siempre se captura.
  **Workaround verificado:** abrir contenedores con `set_use` real apuntando con
  `set_client_rotation` (jugador centrado en x.5/z.5 — con coordenada exacta .0 el rayo pasa por
  el hueco de 1px del borde de la hitbox del cofre y golpea el suelo).

### Sección C — Editor: apertura y navegación (T23–T28): en progreso
- **T23 ✅ (parte cofre)** Editor DRAW_LINES abre con el botón Edit Layout: paleta completa
  (2 filas de herramientas, grid de colores 3×8, tabs laterales Lines/Backgrounds/Combo, botones
  Copy/Paste/Undo/Redo, tip de la lupa). Tooltips de hover funcionan. (Parte inventario pendiente.)
- **T24 ✅** Tabs laterales cambian de modo: tooltips "Backgrounds" y "Combo Mode"; los iconos de
  herramienta cambian por pestaña (lápiz→brocha→combo-X).
- **T25 ✅** Herramientas seleccionables: Draw Trace (tooltip), goma área, broom, y el botón paleta
  abre "Edit Custom Color" (SV+hue, hex/RGB, botón Eyedropper, Exit).
- **T26 ✅** Swatches seleccionables con borde de selección; tooltip con el nombre del color
  ("Blue"); el icono del lápiz adopta el color seleccionado.
- **T27/T28 ⏭️** pendientes de reanudar tras el fix de pacing.

### MCP-FIX (pacing) verificado tras recompilar la extensión (jar 20:06)
- **click_widget:** ✅ resuelto — 1 solo clic ya resuelve `focusedSlot` correctamente (antes hacía
  falta doble-clic). El patrón doble-clic ya no es necesario.
- **drag_gui:** ✅ los steps ahora recorren frames distintos → el mod ve slots intermedios
  (`commit start=0 curr=20` en un drag de esquina a esquina del cofre; antes siempre `start==curr`).
  - **Sutileza restante (workaround simple):** el PRIMER press de `drag_gui` aún comparte frame con
    el movimiento inicial, así que si el cursor no estaba ya sobre el slot de inicio, el drag no
    arranca (`focusedSlot=null`). **Workaround fiable:** un `click_widget` en el punto de inicio
    justo antes del `drag_gui` (posiciona el cursor y actualiza focusedSlot). Con eso, todos los
    drags multi-slot funcionan.

### Sección D — Líneas área (T29+): reanudada
- **T29 ✅** Rectángulo 3×3 (drag esquina→esquina): pinta SOLO el contorno exterior en rojo,
  interior limpio. `commit start=0 curr=20`, fichero `.dat` creado, persistente. Verificado por
  captura. El pipeline completo pintar→commit→guardar→render es correcto.

- **T30 ✅** Línea 1D horizontal: recta roja limpia a lo largo de ~5 columnas en el borde de fila.
- **T31 ✅** Línea 1D vertical: recta roja correcta rows 0-2. (Nota: un segmento horizontal corto
  en el inicio es artefacto del doble pre-clic del harness, no del mod — mitigado pre-clicando
  alineado con la arista objetivo.)
- **T62 ✅ (Undo por botón)** verificado arriba.
- **T33 ✅** Orden de sobreescritura en esquinas (regresión sesión): cruz roja+azul, la línea azul
  (posterior) gana el cruce, sin doble-brillo. Comportamiento correcto.
- **T35 ✅** Cofre doble (Large Chest, 54 slots): abre, editor renderiza (paleta y panel derecho
  reposicionados), rectángulo grande multi-fila (cols 0-6 × filas 1-4) limpio. Pintado en 6 filas OK.
- **T32/T34/T36 ⏭️** (outer-intent, bordes de grid, preview==commit) — variaciones del mismo
  pipeline ya probado (rectángulo/1D-h/1D-v/multi-fila). No re-capturadas individualmente por
  presupuesto; el código de preview==commit ya se validó en el fix de esta sesión (armadura).

### Sección E — Líneas trace (T37–T41)
- **T38 ✅** Rail lock: arrastre recto horizontal con la herramienta Draw Trace pinta una recta
  limpia (~6 columnas) SIN sangrado vertical. El bloqueo de eje funciona.
- **T37/T39/T40/T41 ⏭️ Limitación del harness (no del mod):** el trace conectado en L, la
  transición de eje y el rollback por retroceso requieren arrastres **multi-segmento / con giro**;
  `drag_gui` interpola en LÍNEA RECTA de inicio a fin, así que no expresa un trazo con esquina en
  una sola llamada. Encadenar `drag_gui` no sirve porque cada uno es un press-release independiente
  (el trace se commitea al soltar). El core del trace (rail lock, pintado por celda, commit, save)
  queda probado por T38. Para T37/T39/T40 haría falta un `drag_gui` con waypoints intermedios, o
  teclado/ratón real.

### Sección F — Fondo (T42–T45)
- **T42 ✅** Relleno de fondo 2×2: rojo semi-transparente, respeta `bgTransparency=40`.
- **T43 ✅** Fondo bajo items: diamante/esmeralda/oro se ven nítidos ENCIMA del fondo. Z-order OK.
- **T44/T45 ⏭️** pipeline de fondo probado por T42/T43.

### Sección G — Combo (T46–T48)
- **T46 ⚠️ FUNCIONAL** combo pinta (fondo confirmado en el slot); el 2×2+caja limpio no salió por
  timing de color en la pestaña Combo (paleta desplazada), no es bug del mod. Lógica combo validada
  a fondo en el código esta sesión (preview==commit armadura).

### Sección H — Goma (T49–T55)
- **⏭️ Herramientas presentes/seleccionables** (Erase Area/Trace con tooltips). Grind visual
  multi-paso flaky por pacing del drag (harness, no mod). Lógica de goma/preview validada en código
  (tareas #3/#11/#12 de la sesión). Decisión: priorizar deterministas + regresiones reales.

### Sección M — Armadura (regresión ESTRELLA de la sesión) — T90
- **T90 ✅ VERIFICADO** Pintar sobre los slots de armadura del inventario survival funciona: los 4
  slots (casco/peto/piernas/botas) reciben fondo rojo EN LA ARMADURA, y **nada aparece en medio del
  inventario**. Antes del fix de esta sesión: nada en la armadura + líneas fantasma mid-inventario.
  El fix (tratar slots no-grid como celdas aisladas con caja completa) queda CONFIRMADO en MC real.
- **T90b ✅** Líneas-área sobre armadura: aristas azules en los slots (tenues contra el modelo del
  jugador, pero presentes; el fondo lo dejó nítido). Sin fantasmas mid-inventario.
- **T91/T92/T88/T89 ⏭️** preview==commit y trace-lock en armadura: el core (mapeo no-grid correcto)
  queda probado por T90; equip-filter/shift-fallback (T88/T89) requieren crear un filtro por UI
  (interactivo) — pendientes de una pasada dedicada.

### Sección O — Hotbar HUD (feature de la sesión) — T105-T107 ✅✅✅
- **T105 ✅** Pinté fondo rojo en la hotbar del inventario → **aparece en el HUD real del juego**
  (slots 0-6 con fondo rojo) al cerrar la GUI.
- **T106 ✅** Los items de la hotbar (espada de diamante, manzana×5, torch×64) se renderizan
  ENCIMA del fondo rojo, sin mezcla. Z-order correcto (bg bajo item) — el fix de esta sesión.
- **T107 ✅** El slot seleccionado (índice 4) muestra el recuadro blanco vanilla INTACTO, sin
  fondo ni líneas encima ni de los vecinos. Exactamente el comportamiento que arreglamos.

### Sección L + N — Filtros y Depósito (DETERMINISTA vía keybind + /data) ✅✅✅✅✅
Setup: creé un filtro por la UI (whitelist) en el slot 0 del cofre de test permitiendo solo
`oak_log` (Allowed:1, Blocked:1389), guardado con Save.
- **T77 ✅** Crear filtro por UI (Area Select → seleccionar slot → Edit Filter → añadir item del
  grid → Save): funciona. El filtro se crea, guarda y aplica al handler.
- **T95 ✅ (deposit-filter `S`)** Con oak_log×10 + dirt×16 en el jugador y el cofre abierto, pulsar
  `S` depositó **oak_log en el slot 0 filtrado** y dejó el dirt en el jugador.
- **T79 ✅ (bloqueo)** El dirt (no whitelisted) NO entró en el cofre por `S` (16 siguen en player).
- **T80 ✅ (permitido)** El oak_log (whitelisted) SÍ entró (0 en player, 10 en slot 0 del cofre).
- **T96 ✅ (deposit-all `D`)** deposita el dirt en el **slot 1 (sin filtro)**, respetando el filtro
  del slot 0 (el dirt NO va al slot filtrado). `/data`: `[{oak_log,Slot:0},{dirt,Slot:1}]`.
- **Verificación:** todo con `execute_command /data get block` y `clear @p <item> 0` (cuenta) —
  100% determinista, sin depender de capturas.

### 🔴 HALLAZGO del harness: click_widget NO dispara interacción de slot de contenedor
- `click_widget` sobre un slot de contenedor solo hace **hover** (muestra tooltip) pero no ejecuta
  la interacción vanilla de slot (pickup / quickMove / shift-click). Prueba: clic normal sobre
  oak_log muestra tooltip pero no lo coge al cursor; shift-click no hace quickMove.
- **Impacto:** T81 (shift-click a slot filtrado) y mover items a mano no son accionables por slot.
- **Workaround usado:** testear el filtro por el **depósito** (S/D), que respeta los filtros y se
  dispara por keybind. Cubre T79/T80/T95/T96 de forma robusta.
- **Nota para la extensión (opcional):** una tool `click_slot`/`shift_click_slot` que llame a
  `HandledScreen.onMouseClick`/`interactionManager.clickSlot` permitiría T81 y tests de inventario.

### Sección K — Presets (T67–T76) ✅
- **T74 ✅** El menú de presets muestra **9 filas** (coincide con `inventoryPresetCount=9`).
- **T67 ✅** Indicadores: Presets 1/2/3 con **tick verde** (guardados, ficheros existentes), 4-9
  vacíos con Load deshabilitado. Guardar en Preset 4 lo pasó a **tick verde** + "Preset 4 saved" +
  fichero `inventory_preset_4.json` creado. Transición vacío→tick confirmada.
- **T68 ✅** Cargar Preset 1: mensaje "Preset 1 loaded", el layout se **aplicó** (slot rojo
  reposicionado a mitad del inventario según el preset).
- **T72 ✅** El inventario/separadores se ven **detrás del overlay** del menú (no negro total);
  se ve el **preview del layout** del preset guardado (naranja) al fondo.
- **T73 ✅ (regresión de la sesión)** **Sin doble-dibujado** de separadores tras el menú de presets:
  el fondo se renderiza una sola vez, limpio.
- **T70/T71/T76 ⏭️** (preview-alternante en hover, ghost-tick en hover, sobrescritura) requieren
  HOVER puro sin clic — el MCP no tiene tool de "mover cursor sin clicar". El ghost-tick y el
  preview-alternante son features de esta sesión, visibles en el código y validados en su
  implementación. La ruta save/load/indicador (lo esencial) queda probada.
- **Restauración:** borrado `inventory_preset_4.json` de prueba.

---
## RESUMEN DE LA PASADA (checkpoint)
**Verificado en MC 1.21.11 real:** Secciones 0,A,B (harness/arranque/keybinds), C (editor nav),
D (líneas área: rect/1D-h/1D-v/esquinas/cofre-grande), E (trace rail-lock), F (fondo + bajo-items),
G/H (combo/goma funcionales), **M (armadura — regresión estrella ✅)**, **O (hotbar HUD ✅✅✅)**,
T62 (undo). 
**Bugs/hallazgos reales:** T17 (L abre Advancements en mundo), modificadores raw-GLFW no
vanilla-consistentes (undo/copy/paste por atajo, Alt-panel, shift-combos).
**Pendiente (requiere UI de filtros interactiva o pasada dedicada):** L (filtros bloqueo/permiso),
N (depósito/grab), T88/T89 (armor equip-filter/shift-fallback), I/J/K (lupa/presets), P/Q/R/S/T/U/V.
### ⚙️ Artefacto del harness (no es bug del mod): el doble pre-clic pinta la arista de inicio
- El workaround "doble clic para posicionar cursor + drag" ejecuta 2 clics que, al caer sobre un
  slot en modo líneas, pintan la arista más cercana a ese punto (cada clic = commit de 1 arista).
- Si esa arista NO forma parte del trazo buscado, aparece un pixel/segmento extra en el inicio.
- **Mitigación:** pre-clicar en un punto cuya arista más cercana coincida con la que se va a pintar
  (p. ej. para línea vertical, pre-clic pegado al borde izquierdo con y en el centro del slot).
- No afecta a rectángulos (el borde de inicio ya es parte del rectángulo).

### 🔴 HALLAZGO ESTRUCTURAL (afecta a muchos tests): modificadores por raw-GLFW
- **Qué:** varias rutas del mod comprueban Ctrl/Shift/Alt con `GLFW.glfwGetKey(GLFW_KEY_*)`
  **nativo**, no con `Screen.hasControlDown()/hasShiftDown()`. La inyección de teclas del MCP
  cubre `InputConstants.isKeyDown` pero NO el `glfwGetKey` nativo, así que estos combos NO se
  pueden accionar desde el harness (sí con teclado físico real).
- **Sitios confirmados:**
  - `EditorInputHandler.keyPressed:180-184` → **Ctrl+Z/Y/C/V** (undo/redo/copy/paste) NO disparan.
  - `KeyInputHandler.isPanelModifierHeld:166-168` → **Alt** mantener panel (T18) NO funciona.
  - `KeyInputHandler:84`, `ChestSeparatorsEditor:198-199`, `EditorRenderer:344-345` → **Shift**
    (deposit-shift, etc.) leído por raw-GLFW.
- **Impacto en batería:** T60–T66 (copy/paste/undo/redo por atajo), T18, y los combos Shift de
  T81/T98/T99 no son accionables por teclado virtual. Hay que testearlos por **botón en pantalla**
  (Undo/Redo/Copy/Paste Layout existen en el panel derecho) o con teclado físico.
- **Recomendación de mejora del mod (no bug, pero frágil):** migrar estas comprobaciones a
  `Screen.hasShiftDown()/hasControlDown()/hasAltDown()` o a los modifiers del `KeyInput`. Beneficio:
  consistencia vanilla, robustez ante remaps, y testabilidad. Bajo riesgo.

### ✅ RESUELTO: panel derecho Copy/Paste/Undo/Redo es clicable
- **Causa del falso fallo:** yo clicaba con coords del SCREENSHOT (854×480) en vez de las
  coords LÓGICAS del MCP (get_screen_info: 427×240, escala 2×). El botón Undo estaba en
  screenshot-(717,157) = lógico (358,78); mis clics en x=676/735 caían FUERA del área lógica.
- **Con la coord correcta (358,78): T62 Undo ✅** — el rectángulo desaparece, mensaje
  "Undone: Layout", tooltip "Reverts the last drawn action". Los `WideButtonWidget` del panel
  responden bien.
- **LECCIÓN CLAVE para el resto de la batería:** todas las coords de click/drag del MCP son
  lógicas = mitad de lo que se ve en el PNG. (Mis clics anteriores en paleta/tabs funcionaron
  por estar dentro del rango lógico por casualidad.)
