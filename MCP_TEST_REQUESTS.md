# Peticiones a la extensión MC-Claude para desbloquear los tests restantes

Documento para pasar a la IA que mantiene la extensión **mcclaude** (MC 1.21.11, yarn, Java 21).
El objetivo: añadir 3 tools/capacidades que desbloquean las secciones **P-UI**, **Q** y **T** de la
batería de tests de ChestSeparators, hoy imposibles de automatizar por falta de puntos de entrada.

Cada bloque incluye: *qué falta*, *por qué está bloqueado ahora*, *contrato de la tool propuesta*,
*boceto de implementación (yarn 1.21.11)* y *cómo lo usaría el agente*.

> Nota de mapeos: los nombres de clase/método son orientativos de yarn 1.21.x. Ajusta a los mapeos
> exactos de 1.21.11 al implementar. Todo lo que toque UI o mundo debe ejecutarse en el **render
> thread** con `MinecraftClient.getInstance().execute(() -> { ... })` y devolver la respuesta cuando
> la acción se haya aplicado (no antes).

---

## 1) `open_screen` — abrir menús vanilla (desbloquea **P-UI**)

### Por qué está bloqueado
No hay forma de abrir el **menú de pausa** (`GameMenuScreen`). `key_action escape` estando en el
mundo devuelve `context:"world"` y NO abre la pausa (el escape-in-world es vanilla puro, no un
keybind interceptable). Sin pausa no se llega al botón **Mods** de ModMenu → la pantalla de config
cloth-config del mod (dark mode, toggles, `inventoryPresetCount`, etc.) es **inalcanzable**, y
tampoco se pueden probar rebinds de teclas desde Options → Controls.

### Contrato propuesto
```
Tool: open_screen
Args:
  target: "pause" | "title" | "options" | "controls" | "mod_config"
  mod_id: string   (opcional, requerido solo si target=="mod_config")
Return:
  { "opened": true, "screen_class": "<clase resultante>", "title": "<título>" }
  ó { "opened": false, "reason": "..." }
```

### Implementación (boceto)
```java
MinecraftClient mc = MinecraftClient.getInstance();
mc.execute(() -> {
  switch (target) {
    case "pause"    -> mc.setScreen(new GameMenuScreen(true));           // requiere estar en mundo
    case "title"    -> mc.setScreen(new TitleScreen());
    case "options"  -> mc.setScreen(new OptionsScreen(mc.currentScreen, mc.options));
    case "controls" -> mc.setScreen(new ControlsListScreen(mc.currentScreen, mc.options)); // o KeybindsScreen
    case "mod_config" -> {
      // Opción sin acoplar a ModMenu: usar la API de ModMenu si está presente vía reflexión,
      // o abrir directamente la factory del mod:
      //   var factory = ModMenuApi lookups...  (o) el mod expone getModConfigScreenFactory()
      //   mc.setScreen(factory.create(mc.currentScreen));
    }
  }
});
```
- `get_screen_info` ya enumera bien los `ButtonWidget` de `GameMenuScreen`/`OptionsScreen` (son
  `drawableChildren` estándar), así que una vez abiertos, `click_widget` navega sin cambios.
- **No hace falta** que la extensión dependa de ModMenu: con `open_screen "pause"` el agente clica
  el botón "Mods" → el mod → config, todo con `click_widget`. `target:"mod_config"` es un atajo
  opcional.

### Cómo lo usaría el agente
`open_screen pause` → `get_screen_info` (leer botones) → `click_widget` en "Mods" → en el mod → en
"Config" → cambiar toggles → `get_screen_info` para verificar estado. Desbloquea toda la sección
**P-UI** y los tests de rebind de teclas.

---

## 2) `disconnect_to_title` — salir del mundo sin matar el proceso (desbloquea **Q**)

### Por qué está bloqueado
Para probar **persistencia entre recargas** (¿los separadores/filtros sobreviven a cerrar y reabrir
el mundo?) hace falta salir al menú de título y volver a entrar. `quit_game` **mata todo el proceso**
(hay que relanzar MC entero, lento y frágil). `open_world` ya existe para entrar, pero falta el paso
intermedio: **volver al título sin cerrar el juego**.

### Contrato propuesto
```
Tool: disconnect_to_title
Args: {}
Return: { "disconnected": true, "screen_class": "net.minecraft.client.gui.screen.TitleScreen" }
```
Y (recomendado) endurecer `open_world` para que **bloquee hasta que el mundo esté totalmente
cargado** y devuelva sólo entonces:
```
open_world  ->  espera a: integrated server arrancado + world != null + player spawneado
Return: { "loaded": true, "world": "<nombre>", "player_pos": {...} }
```

### Implementación (boceto)
```java
MinecraftClient mc = MinecraftClient.getInstance();
mc.execute(() -> {
  if (mc.world != null) mc.world.disconnect();   // desconecta la sesión lógica
  mc.disconnect();                               // guarda + para el integrated server
  mc.setScreen(new TitleScreen());
});
// devolver la respuesta cuando currentScreen sea TitleScreen (poll corto en el mismo hilo lógico)
```
- Para el "espera a cargado" de `open_world`: sondear en el server thread que
  `server.isLoading()==false` y que `mc.player != null && mc.world != null`, con timeout.
- **Importante para la estabilidad:** exponer una tool `world_ready` (o un campo `ready` en
  `get_player_info`) que confirme que la carga terminó, para que el agente no interactúe demasiado
  pronto (era una de las causas probables de los cierres del dev client).

### Cómo lo usaría el agente
Pintar separadores en un cofre → `disconnect_to_title` → `open_world` (bloquea hasta listo) →
reabrir el mismo cofre → verificar por `data get` / screenshot que los separadores siguen. Esto
prueba la sección **Q** (persistencia real entre sesiones) de forma determinista.

---

## 3) Multiplayer real (desbloquea **T**) — en dos niveles

Esto es lo más pesado. Divido en dos niveles; con el **Nivel A** ya se cubre el ~80% del valor.

### Contexto importante (leer antes)
Mucho de lo "multiplayer" del mod **ya se ejercita en singleplayer** porque hay un **integrated
server**: el path de red `ServerPlayNetworking` (p. ej. `WhitelistPayload` server→cliente al abrir
un cofre, o el filtrado de hopper/shift-click validado en servidor) **ya corre** en SP. Lo que
*sólo* aparece con 2 jugadores reales es la **interacción cruzada**: el jugador A configura un cofre
y el jugador B lo ve; A y B compitiendo por depositar; enforcement del whitelist cuando quien
interactúa NO es quien lo configuró.

### Nivel A — "Abrir a LAN" + segundo cliente controlable (recomendado)
```
Tool: open_to_lan
Args: { "port": int (opcional), "allow_cheats": bool, "game_mode": "creative"|"survival" }
Return: { "opened": true, "port": <int> }     // usa IntegratedServer.openToLan(...)
```
- Implementación: `mc.getServer().openToLan(GameMode.X, allowCheats, port)` (devuelve el puerto).
- Luego, un **segundo dev client** se lanza con su **propio puerto MCP** (p. ej.
  `-Dmcclaude.port=8723`) y se conecta a `127.0.0.1:<port>` con una tool nueva:
```
Tool: connect_server
Args: { "address": "127.0.0.1", "port": <int> }
Return: { "connecting": true }
```
- El agente controlaría **dos instancias MCP** (8722 y 8723) — una por jugador — y podría orquestar
  interacciones A↔B de forma totalmente determinista.
- Requisito de arranque: permitir múltiples run configs o un flag para el 2º cliente con puerto MCP
  distinto y, si se puede, cuenta offline distinta (`--username Player2`).

### Nivel B — jugador "bot" server-side (si el Nivel A es inviable)
Un `FakePlayer`/`ServerPlayerEntity` sintético en el servidor cuya posición, inventario y acciones
(abrir cofre, shift-click, hopper) se controlen por tools:
```
Tool: spawn_bot_player   -> { "bot_id": "...", "name": "Bot1" }
Tool: bot_open_container  { "bot_id", "x","y","z" }
Tool: bot_move_stack      { "bot_id", "from_slot","to_slot", "shift":bool }
Tool: bot_get_inventory   { "bot_id" }
```
- Más complejo (los fake players en Fabric sin Carpet son laboriosos y frágiles con la lógica de
  `ScreenHandler`), pero evita gestionar dos procesos/puertos. Sólo si el Nivel A no encaja.

### Cómo lo usaría el agente (con Nivel A)
Cliente-A (8722) pinta separadores + define un whitelist en un cofre → `open_to_lan` →
Cliente-B (8723) `connect_server` → Cliente-B abre el mismo cofre → verifica que **ve** los mismos
separadores/whitelist (payload server→cliente) → Cliente-B intenta depositar un item **no** permitido
por el whitelist → verifica que el servidor lo **rechaza**. Eso valida el enforcement real
cross-player de la sección **T**.

---

## Prioridad sugerida
1. **`open_screen`** (P-UI) — barato, alto valor, desbloquea toda la config UI y rebinds.
2. **`disconnect_to_title` + `open_world` bloqueante + `world_ready`** (Q) — medio, y de paso mejora
   la **estabilidad** (no interactuar antes de que el mundo cargue fue causa probable de cierres).
3. **`open_to_lan` + `connect_server` (Nivel A)** (T) — el más caro; hacerlo al final.

## Fuera del alcance de la extensión (pero relacionado con la estabilidad)
El dev client se cerró 4 veces sin excepción/crash-report. Recomendaciones (no son tools):
mover el proyecto **fuera de OneDrive** (la sincronización interfiere con `run/`), reutilizar **una
sola instancia** en vez de matar/relanzar, y bajar la carga de screenshots. El `world_ready` de
arriba también ayuda a no provocar el cierre por interactuar demasiado pronto.
