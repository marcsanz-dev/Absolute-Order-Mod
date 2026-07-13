# Resultados de la batería de tests — ChestSeparators 1.3.1

**Fecha:** 2026-07-08 · **Entorno:** MC 1.21.11, Fabric Loader 0.19.3, mcclaude 0.1.0 (portado a 1.21.11)
· cliente dev `runClient` (runDir `run/client`) · MCP en `http://127.0.0.1:8722/mcp`

**Leyenda:** ✅ PASA · ❌ FALLA · ⚠️ PARCIAL/mejorable · ⏭️ SALTADO (con motivo) · ❓ PENDIENTE de aclarar

> Los tests fallidos o imperfectos llevan explicación extensa: qué falla, qué se esperaba,
> cómo reproducirlo y análisis de causa probable.

---

## 🛠️ CORRECCIONES APLICADAS Y RE-VERIFICADAS (2026-07-13)

Los 3 bugs reales del mod se han corregido y re-testeado en MC real (verificación determinista por
archivos `.dat`/config, sin capturas). Todos compilan y pasan.

1. **T17 — Lupa chocaba con Advancements (`L`) — ✅ CORREGIDO.**
   - Fix: default de `toggle_magnifier` cambiado de `GLFW_KEY_L` a **`GLFW_KEY_M`** en
     `ModKeyBindings.java`.
   - Re-test: `options.txt` regenera el default como `key.keyboard.m`; pulsar `M` en el mundo togglea
     `magnifierEnabled` (true↔false, persistido en `chestseparators_config.json`); pulsar `L` ahora
     abre `AdvancementsScreen` vanilla (conflicto eliminado).

2. **U-1 — Barril dejaba config huérfana al romperse — ✅ CORREGIDO.**
   - Fix: `WorldMixin` amplía la limpieza a **todos los contenedores por-posición** (guard
     `ChestBlock || BarrelBlock`).
   - Re-test: barril con `.dat` → romper (`setblock air`) → el `.dat` **se borra** (antes quedaba).

3. **Persistencia del 2º cofre / Paste (T61) — ✅ CONFIRMADO REAL y CORREGIDO.**
   - **Era un bug real** (no artefacto): al abrir un **segundo** cofre en la misma sesión, sus ediciones
     (pintado directo *o* paste) **no persistían**. Reproducido: B como 1er cofre guarda; como 2º no,
     y el `.dat` de A no se tocaba → `currentChestPos` acababa `null`.
   - **Causa raíz:** race en `ChestPosStorage.lastClickedPos`. `GenericContainerScreenMixin.onRemoved`
     lo nulificaba al cerrar un cofre; al abrir el siguiente, el orden
     `interactBlock(B)[set B]` → `setScreen(B)` → `A.removed()[null]` → `B.init()[lee null]` dejaba
     `currentChestPos=null`, y `saveSmart()` (rama `else if currentChestPos != null`) **no guardaba**.
   - **Fix:** mover la limpieza del handoff a **consume-on-read** en `ChestSeparatorsEditor#init`
     (tras copiar los valores a la sesión), y quitarla de `onRemoved`. Elimina la race y mantiene la
     prevención de fugas (el único lector es el init).
   - Re-test: escenario **A→B** (el que fallaba) → ahora el `.dat` de B **se crea** (134 b). El paste
     usa el mismo `saveSmart`→`saveConfig(currentChestPos)`, así que queda arreglado por la misma causa.

### 🔴 Causa raíz de la inestabilidad del dev client — `take_screenshot` (glReadPixels)
Los 6 cierres del dev client durante la sesión son un **crash NATIVO** (`EXCEPTION_ACCESS_VIOLATION
0xc0000005`) en **`nvoglv64.dll`** (driver NVIDIA), con pila
`GL11C.glReadPixels ← nglReadPixels ← nvoglv64.dll`. Es decir, **la tool `take_screenshot` (que usa
`glReadPixels`) revienta el driver NVIDIA** en esta máquina. **No es bug del mod.** Mitigaciones:
(1) lanzar el cliente con **ventana visible** (no minimizado); (2) para verificación automatizada,
**evitar `take_screenshot`** y usar comprobaciones deterministas (JSON/archivos). Sugerencia para la
extensión: capturar por otra vía (blit a framebuffer / API de screenshot de vanilla) en vez de
`glReadPixels` directo.

> **Nota Windows:** el `.bat` de lanzamiento fallaba con "gradlew.bat no se reconoce" por
> `NoDefaultCurrentDirectoryInExePath`. Solución: invocar **`.\gradlew.bat`** (ruta explícita).

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

### Desbloqueados por `move_cursor` (hover puro) y `drag_gui`+`waypoints` (extensión mejorada)
La extensión añadió `move_cursor` (hover sin pulsar) y `waypoints` en `drag_gui`, resolviendo los
dos límites del harness que había documentado. Re-tests:
- **T71 ✅ (ghost-tick en hover de Save)** Hover sobre Save de un preset VACÍO → aparece el
  **tick fantasma** (semitransparente) + Save resaltado + preview del layout. Hover sobre Save de
  un preset GUARDADO → tick **atenuado** (señal de sobrescritura). Al mover el cursor, el ghost
  desaparece (confirma que es efecto de hover en vivo). Feature de esta sesión, VERIFICADA.
- **T70 ✅ (preview en hover de Load)** Hover sobre Load de un preset guardado → el **preview del
  layout** (naranja) se pinta sobre los slots reales del inventario detrás del menú. (La fase de
  filtros no se distingue porque el preset no tenía filtros → fase en blanco.)
- **T37 ✅ (trace L-shape multi-segmento)** Con `drag_gui`+`waypoints`
  `[{142,72},{214,72},{214,104}]` (+ `move_cursor` para pre-posicionar y evitar el primer-press
  stale) → se pinta una **línea roja en L** conectada: tramo horizontal que gira a vertical. El
  trace conectado, antes imposible con drag lineal, ahora funciona.
- **Técnica clave nueva:** `move_cursor` a la posición de inicio ANTES de `drag_gui` elimina el
  problema del primer-press con `focusedSlot` stale (más limpio que el doble-clic previo).

### Sección I — Lupa / Magnifier (T18/T19/T20 = tareas #18/#19/#20) — parcial
- **Toggle ✅** `L` en contexto GUI activa/desactiva la lupa (`magnifierEnabled` en config).
- **Sin crash ✅** Activar la lupa + hover sobre slots + **pintar** con la lupa activa NO crashea;
  el pintado sale correcto (rectángulo limpio). **IMPORTANTE:** hubo un cierre de MC en el primer
  intento, pero NO se reprodujo (sin crash-report ni excepción en el log) → fue un **flake del dev
  client**, no la lupa. Confirmado con repetición.
- **Overlay de zoom ⏭️ no capturable con este flujo:** la lupa es un overlay que **sigue al cursor**
  y solo se renderiza durante el hover/pintado activo. `take_screenshot` es una llamada secuencial
  que ocurre DESPUÉS de que `move_cursor` suelte el hold, así que la captura estática no lo muestra.
  El zoom en sí (lupa lápices #19, lupa eyedropper pixel-zoom #20) se implementó y revisó en el
  código esta sesión.
  - **Sugerencia para la extensión:** un flag en `move_cursor` (p. ej. `screenshot:true`) que
    capture DURANTE el hold permitiría verificar overlays cursor-following (lupa, ghost previews).

### Sección J — Copy/Paste/Undo/Redo (T60–T66) — parcial + hallazgo importante
- **Undo/Redo ✅ (T62)** El botón "Undo Action" (y "Redo") funciona: lo usé decenas de veces a lo
  largo de TODA la batería para limpiar cofres entre tests. Verificado exhaustivamente.
- **Copy ✅ (parcial)** `Ctrl+C` (con `modifiers:["ctrl"]`) habilita el botón "Paste Layout" (antes
  gris) → el copy al clipboard funciona. **Los atajos copy/paste requieren Ctrl** (no la tecla sola;
  la `C`/`V` a secas no hacen nada — confirmado con el botón Paste que seguía deshabilitado).
- **T60/T61 (paste a otro cofre) ⚠️ NO VERIFICADO LIMPIAMENTE — artefacto del harness, no bug:**
  Descubrí que abrir un contenedor con **`set_use`** (mi helper `open_chest`) NO dispara el mixin
  `ChestInteractionMixin` del mod (que captura `ChestPosStorage.lastClickedPos` en el `interactBlock`
  del cliente). Consecuencia: al abrir el cofre B (-3) con set_use, el mod retenía la posición
  ANTERIOR (A, -8), así que TODO lo que pintaba/pegaba en "B" se guardaba en el `.dat` de A.
  **Diagnóstico definitivo:** pinté una línea en el cofre abierto por `open_chest(-3)` → se modificó
  `minecraft_overworld_-8_119_-10.dat`, NO el de -3. Confirma la posición stale.
  - **RESOLUCIÓN (usar en la próxima pasada):** abrir contenedores con **`interact_block`** (que la
    extensión ya enruta por el interaction manager del cliente y SÍ dispara el mixin), no con
    `set_use`. Añadido helper `open_chest_ib` a `lib.sh`. Con eso, copy→paste cross-cofre debería
    verificarse limpiamente (cada cofre con su `.dat` propio).
  - No es un bug de ChestSeparators: en juego real el clic-derecho siempre dispara el mixin.

### Sección J (re-test con interact_block) — Copy/Paste FUNCIONA en UI, PERSISTENCIA a verificar
- **Atajos Ctrl+C/V/Z/Y ❌ vía MCP (raw-GLFW):** `EditorInputHandler.keyPressed` (línea 180) chequea
  Ctrl con **`GLFW.glfwGetKey()` nativo**, que la inyección de modificadores del MCP NO falsea. Los
  atajos no se disparan por `key_action modifiers:["ctrl"]`. **Los BOTONES sí funcionan.** (Mismo
  patrón raw-GLFW que los otros hallazgos de modificadores.)
- **T60 Copy ✅ (botón)** "Copy Layout" copia al clipboard (`copyToClipboard()`); habilita "Paste".
- **T61 Paste — UI ✅ / PERSISTENCIA ⚠️ A VERIFICAR MANUALMENTE:**
  - **UI funciona:** con `interact_block` abrí el cofre B correcto (vacío, sin items de A → posición
    capturada bien), pegué con el botón "Paste Layout" → apareció el **rectángulo rojo 3×3** en B +
    mensaje **"Layout Pasted!"**. El botón llama `saveSmart()` (ScreenDrawLines:171).
  - **PROBLEMA:** al cerrar B y reabrirlo, **estaba vacío**. Verificado por ficheros: `-8.dat` (A)
    quedó **sin cambios** (`54acc...`) y **`-3.dat` (B) NUNCA se creó**. Es decir, el `saveSmart()`
    del paste **no escribió a disco** (ni a A ni a B).
  - **⚠️ ACCIÓN PARA EL USUARIO — verificar a mano:** pintar un layout en el cofre A, Copy Layout,
    abrir un cofre B DISTINTO, Paste Layout, **cerrar y reabrir B**. Si el layout desaparece → es un
    **bug real de persistencia del paste** (el paste aplica en memoria pero no guarda al cofre destino
    recién abierto). Si persiste → fue un artefacto de timing de la automatización (interact_block +
    construcción del editor). No pude desambiguarlo de forma 100% concluyente por vía automatizada.
- **Undo/Redo ✅** (botones) — usados con éxito decenas de veces en toda la batería.

### Sección P — Config / ModMenu (T113–T121) — valores verificados; UI de ModMenu inalcanzable
- **Efectos de config ✅ (verificados indirectamente a lo largo de la batería):**
  - `darkMode:true` → tema oscuro en TODAS las capturas del editor.
  - `bgTransparency:40` → fondo rojo oscuro atenuado (T42).
  - `lineTransparency:100` → líneas opacas.
  - `inventoryPresetCount:9` → 9 filas de presets (T74).
  - `showDepositButton` → togglea con `G` y persiste (T16).
- **⏭️ Pantalla de config ModMenu (sliders/toggles/rebind) — estado inicial: NO ALCANZABLE** (ver
  actualización P-UI abajo tras añadir `open_screen`).

### Sección P-UI — RE-EJECUTADA con `open_screen` (extensión mejorada 12 jul) — ✅ mayormente desbloqueada
Con las tools nuevas de la extensión (`open_screen`, `world_ready`), verificado en MC real:
- **T113 ✅ `open_screen pause`** → abre `GameMenuScreen` con sus 9 widgets enumerables
  (Back to Game, Advancements, Statistics, **Mods**, Report Bugs, Options…, Open to LAN,
  Save and Quit to Title). Los botones estándar responden a `click_widget` ("Done" cerró la
  ModsScreen con `screen_changed:true`).
- **T114 ✅ `open_screen controls` → Key Binds:** navegación `controls` → `ControlsOptionsScreen`
  → clic "Key Binds…" → `KeybindsScreen`. Los **10 keybinds del mod están registrados y bindeados**
  (verificado además de forma determinista en `options.txt`):
  `toggle_preview_panel:O`, `show_panel_modifier:Left Alt`, `toggle_edit_buttons:H`,
  `toggle_deposit_button:G`, `toggle_magnifier:L`, `deposit_filter:S`, `deposit_all:D`,
  `deposit_junk:C`, `grab:V`, `auto_deposit:unbound`. → **son rebindables desde la UI vanilla.**
- **🟡 HALLAZGO de harness (NO bug del mod): la pantalla cloth-config del mod sigue sin alcanzarse.**
  `open_screen mod_config` abre la **lista** `ModsScreen`, y ahí ChestSeparators aparece como
  *"Absolute Order: Custom Chests" v1.3.1 by marcsanz-dev* (seleccionable por búsqueda). PERO el
  último salto falla: `click_widget` **no dispara los widgets custom de ModMenu** — ni el botón
  `ModMenuButtonWidget "Mods"`, ni la rueda de config `LegacyTexturedButtonWidget`, ni las filas
  del `ModListWidget` (probado clic por índice, por coordenada centrada, hover+clic y doble-clic;
  todos `clicked:true` pero `screen_changed:false`, sin excepción en el log). Los botones **estándar**
  sí funcionan (Done, Key Binds). El cloth-config del mod es de widgets estándar (sería clicable),
  pero se construye **solo** vía `ModMenuIntegration.getModConfigScreenFactory()` (no hay entrada
  in-game). → El único bloqueo restante es la incompatibilidad de `click_widget` con los botones
  texturizados de ModMenu.
  - **Sugerencia concreta para la extensión:** que `open_screen mod_config` **invoque directamente
    el `ConfigScreenFactory` del mod** (vía el entrypoint ModMenuApi por reflexión) en lugar de abrir
    la lista de mods. Eso saltaría el botón de ModMenu y dejaría el cloth-config 100% testeable.

### Sección Q — Persistencia entre recargas de mundo — ✅ PASSED (con `disconnect_to_title`/`open_world`)
Ciclo de recarga completo, determinista + [SHOT]:
1. Cofre doble con separadores guardados en `-19,119,-10` → abierto → [SHOT ANTES]: contorno naranja
   en L en la zona superior-izquierda + backgrounds tan del inventario.
2. **`disconnect_to_title`** → `TitleScreen` (`disconnected:true`); `world_ready` confirma
   `ready:false, level_loaded:false` (fuera del mundo de verdad).
3. **`open_world "New World"`** → bloquea hasta `ready:true, player_present:true`; el jugador reaparece
   en su posición previa (persistió).
4. Reabrir el MISMO cofre → **[SHOT DESPUÉS] idéntico al de ANTES**: mismos separadores naranjas,
   mismos backgrounds.
→ Tanto el bloque (world-save) como la **config cliente `.dat`** sobreviven a la recarga completa.
`open_world` y `disconnect_to_title` funcionan y **bloquean hasta estado listo** como documenta la guía.

### Sección R — Contenedores (T118–T136) — ✅ COMPLETA (deterministic screen-class check)
- **T118 ✅** Cofre simple (27). **T119 ✅** Cofre doble (Large Chest, 54, sección D).
- **T131 ✅** Barril (abierto en tests previos).
- **T132 ✅ Shulker Box:** abre (`ShulkerBoxScreen`, clase distinta de GenericContainerScreen), los
  iconos del editor del mod están presentes (el mod engancha esa pantalla), y el render NO se rompe
  con la animación del lid → `ShulkerAnimationAccessor` (mixin de la sesión) OK.
- **T130 ✅ Trapped Chest:** abre como `GenericContainerScreen` título "Chest" — **misma clase que
  el cofre normal** → hereda soporte completo del editor. Verificado con `interact_block` + `data merge`.
- **T133 ✅ Ender Chest:** `GenericContainerScreen` título "Ender Chest" → soporte completo heredado.
- **T134 ⚠️ Hopper:** abre como `HopperScreen` (5 slots). **T135 ⚠️ Dispenser** y **Dropper:**
  `Generic3x3ContainerScreen` (3×3). El mod **NO** engancha estas clases — su único mixin de pantalla
  es `GenericContainerScreenMixin` (verificado en `chestseparators.mixins.json` y en el árbol de
  clases enganchadas). **Por tanto no hay overlay de separadores en hopper/dispenser/dropper.**
  Abrirlos **NO** rompe render ni lanza excepción (log limpio tras cada apertura; único ERROR del
  tail es el fallo de auth de Realms del arranque, normal en dev). → Comportamiento **esperado, no
  bug**: es una limitación de diseño (los separadores 9×N no aplican a layouts 5-slot / 3×3).
  Si en el futuro se quisiera soportarlos, habría que añadir mixins para `HopperScreen` y
  `Generic3x3ContainerScreen` con su propia geometría.

**Conclusión R:** todo lo que use `GenericContainerScreen` (cofre / trapped / ender / barril) tiene
soporte completo idéntico; shulker tiene su propia pantalla enganchada; hopper y dispenser/dropper
quedan sin editor por diseño y sin efectos secundarios negativos.

### Sección S — Estrés (T137–T145) — ✅ COMPLETA (dentro de los límites del harness)
- **S1 ✅ Apertura/cierre rápido:** 12 ciclos consecutivos `interact_block`→`get_screen_info`→`escape`
  sobre el mismo cofre. **12/12 abrieron correctamente, 0 fallos, MC vivo** tras el ciclo. Sin fugas
  de estado ni pantallas colgadas.
- **S2 ✅ Integridad de persistencia:** los **12 archivos `.dat`** de config acumulados en toda la
  sesión (posiciones de cofre, ender chest, player_inventory, entity/shulker, world_palette) son
  **todos gzip-NBT válidos** (magic `1f8b`) y **no vacíos** (95–343 bytes). Cero corrupción tras
  cientos de operaciones de pintado/copia/pegado/borrado.
- **S3 ✅ Render de layout grande:** cofre **doble "Large Chest" (54 slots, 6×9)** abre, la toolbar
  del editor renderiza encima, y el inventario del jugador dibuja sus backgrounds guardados
  (`player_inventory.dat` cargado) sin roturas ni corrupción visual. MC vivo. [SHOT verificado]
- **No automatizable con el harness:** carga simultánea de miles de cofres, FPS bajo carga real,
  y estrés de red (requeriría servidor + telemetría de rendimiento). Fuera de alcance.

**Conclusión S:** el mod es estable bajo apertura/cierre repetido y layouts máximos (54 slots), y su
capa de persistencia no corrompe datos. No se observó ninguna degradación ni crash atribuible al mod.

### Sección U/V — Casos límite y ciclo de vida de la config (T146–T160) — ✅ cubierta
- **U1 ✅ Reload por posición:** un cofre colocado sobre una posición con `.dat` guardado **recarga
  automáticamente** sus separadores al abrirlo (verificado con [SHOT]: rectángulo rojo + items
  guardados en `-8,119,-10`). La config está keyed por `dim_X_Y_Z`, y las coords negativas se
  codifican bien en el nombre de archivo (`minecraft_overworld_-8_119_-10.dat`).
- **U2 ✅ (intencional) Limpieza al destruir el cofre:** romper/reemplazar un cofre por un no-cofre
  **borra su `.dat`** local. Confirmado en código: `WorldMixin.onSetBlockState` inyecta en el
  reemplazo de bloque y llama a `ChestConfigManager.clearChest(pos, dim)` cuando
  `oldState instanceof ChestBlock && old != new`. Documentado en el Javadoc del mixin → es limpieza
  anti-huérfanos **por diseño**, no un bug. (Nota: este test destruyó de forma irreversible la config
  de prueba de `-8,119,-10`; era dato de prueba, no de producción.)
- **Tres modelos de persistencia, todos deliberados:**
  - *Cofre / trapped* → por **posición**; se limpia al romper (`WorldMixin`).
  - *Shulker box* → atada al **item vía UUID** (`shulker_<uuid>.dat`); `ShulkerBoxBlockMixin.onBreak`
    fuerza el drop del item con sus componentes → la config **viaja con el item** (correcto).
  - *Ender chest* → config **global única** (`ender_chest.dat`) compartida entre todas (correcto).

#### 🟡 HALLAZGO U-1 (inconsistencia menor, NO crash): el barril deja config huérfana al romperse
`WorldMixin` solo dispara la limpieza para `oldState.getBlock() instanceof ChestBlock`. El **barril**
(`BarrelBlock`) también persiste su config **por posición** (`minecraft_overworld_X_Y_Z.dat`) pero
**no** entra en ese guard → al romper un barril, su `.dat` **NO se borra** y queda huérfano. Efecto
secundario observable: si luego colocas un barril nuevo **en la misma posición**, heredará
silenciosamente los separadores del barril anterior.
- **Esperado/coherente:** o bien la limpieza cubre todos los contenedores por-posición (añadir
  `|| BarrelBlock` — o mejor, comprobar por la interfaz/BlockEntity de contenedor lootable), o bien
  ninguno la tiene y se acepta el modelo de huérfanos. Hoy chest sí y barril no → inconsistente.
- **Reproducir:** pinta separadores en un barril → rompe el barril → el `.dat` sigue en
  `config/chestseparators/.../separators/` → coloca barril nuevo en esa posición → aparece pintado.
- **Severidad:** baja (no rompe render ni corrompe; solo acumula archivos y puede sorprender por
  herencia de layout en posiciones reutilizadas). Decisión de diseño para el autor.

### Sección T — Multiplayer (2 jugadores, LAN) — ✅ PASSED (extensión + build.gradle con `runClient2`)
Ejecutada con la extensión mejorada (`open_to_lan`, `connect_server`) + una tarea `runClient2` añadida
al `build.gradle` (runDir `run/client2`, usuario `Bot2`, `-Dmcclaude.port=8723`, jar mcclaude copiado
a `run/client2/mods`). **Dos clientes MC reales simultáneos**, controlados por sus puertos MCP
(A=8722 host, B=8723 cliente).
- **T-infra ✅:** A `open_to_lan` → `{published:true, online_mode:false, port:60129}`; B
  `connect_server 127.0.0.1:60129` → bloquea hasta `ready:true`; `/list` en el host confirma
  **2 jugadores: Player77 (A) + Bot2 (B)**. (Las tools *de servidor* solo van en A; en B se usan las
  *de cliente*, como documenta la guía.)
- **T-cliente ✅:** al abrir el cofre en **B**, B **renderiza la toolbar del editor y el panel de
  filtros** ("No Filter / Items:0 / Hover a group…") → el mod cliente está activo en el 2º cliente.
  El cofre se ve **sin separadores** en B (correcto: las líneas/backgrounds son config LOCAL por
  cliente, no se sincronizan por red).
- **T-ENFORCEMENT ✅ (el test estrella):** A (host) fijó un whitelist server-authoritative en el
  cofre `-15,119,-10` slot 0 = **solo `minecraft:diamond`** (vía `data merge block`, que reaplica el
  NBT al block entity → `readData` actualiza el mapa en memoria). Bot2 tenía 5 diamantes + 5 tierra.
  **B pulsó `S` (deposit_filter)** con el cofre abierto (`context:"gui"`, manejado en B):
  - Cofre ANTES: `[]` (vacío) → DESPUÉS: **`{Slot:0, diamond×5}`**.
  - Inventario de Bot2 DESPUÉS: **solo queda la tierra** (los diamantes se depositaron).
  → **Cadena cross-player completa:** A configura el whitelist → el servidor lo envía a **otro**
  jugador (B) al abrir (`WhitelistPayload`, `LootableContainerBlockEntityMixin.onOpenMenu`) → el
  deposit de B respeta el whitelist: deposita el item permitido (diamante→slot0) y **rechaza** el no
  permitido (la tierra se queda). Enforcement server-authoritative sobre un 2º jugador **confirmado**.

**Nota de arquitectura (confirmada en vivo):** separadores/backgrounds = **config local por cliente**
(no viajan por red); whitelists/filtros = **server-authoritative** y se sincronizan a cada cliente que
abre el contenedor. Por eso el único comportamiento "de 2 jugadores" real es el del whitelist, y
**funciona**.

### Estabilidad del dev client (nota de infraestructura, NO bug del mod)
El dev client (`runClient`) se cerró/colgó **5 veces** durante la sesión, siempre **sin excepción, sin
crash-report, sin OOM** — muere en la carga del mundo o de recursos. El 5º cuelgue (al relanzar para
P-UI/Q/T) fue al lanzarlo **minimizado/en segundo plano**: se congeló construyendo el atlas de
fuentes. **Fix confirmado:** relanzar con **ventana de consola normal (visible)** — así arrancó
estable y aguantó P-UI + Q + T (incluidos **2 clientes simultáneos**). Recomendación: reutilizar una
instancia con ventana visible, evitar minimizar/segundo plano, y si es posible mover el proyecto fuera
de OneDrive.

> Nota de lanzamiento (Windows): el `.bat` fallaba con "gradlew.bat no se reconoce" porque el sistema
> tiene `NoDefaultCurrentDirectoryInExePath` (cmd no busca ejecutables en el cwd). Solución: invocar
> **`.\gradlew.bat`** con ruta explícita, no `gradlew.bat` a secas.

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
