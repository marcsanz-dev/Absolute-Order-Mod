# Plan de tests manuales — ChestSeparators / Absolute Order (todos los ports)

Batería **manual** de 5 tests-combo para barrer todas las versiones y loaders de los ports.
Cada test es "una cosa" pero comprueba muchas de golpe, para no eternizarse. Se ejecuta
versión a versión: se abre el Minecraft de un objetivo, se pasa el test, y al confirmar se
cierra ese cliente y se abre el siguiente. Primero el Test 1 en todos los objetivos, luego el
Test 2, etc.

**Leyenda:** ☐ pendiente · ✅ pasa · ❌ falla (con nota) · ⚠️ parcial

---

## Objetivos (versión × loader)

| # | Versión | Loader | Módulo | Task de run | Notas |
|--:|---|---|---|---|---|
| 1 | 1.21.11 (ref) | Fabric | `multiloader` | `:fabric:runClient` | JDK 21 |
| 2 | 1.21.11 (ref) | NeoForge | `multiloader` | `:neoforge:runClient` | JDK 21 |
| 3 | 26.2 | Fabric | `multiloader-26.2` | `:fabric:runClient` | JDK 25 |
| 4 | 26.2 | NeoForge | `multiloader-26.2` | `:neoforge:runClient` | JDK 25 |
| 5 | 26.1.2 | Fabric | `multiloader-26.1` | `:fabric:runClient` | JDK 25 |
| 6 | 26.1.2 | NeoForge | `multiloader-26.1` | `:neoforge:runClient` | JDK 25 |
| 7 | 1.20.1 | Fabric | `multiloader-1.20.1` | `:fabric:runClient` | JDK 21 |
| 8 | 1.20.1 | Forge | `multiloader-1.20.1` | `:forge:runClient` | JDK 21 |
| 9 | 1.19.2 | Fabric | `multiloader-1.19.2` | `:fabric:runClient` | JDK 21 |
| 10 | 1.19.2 | Forge | `multiloader-1.19.2` | `:forge:runClient` | JDK 21 |
| 11 | 1.18.2 | Fabric | `multiloader-1.18.2` | `:fabric:runClient` | JDK 21 |
| 12 | 1.18.2 | Forge | `multiloader-1.18.2` | `:forge:runClient` | JDK 21 |
| 13 | 1.16.5 | Fabric | `multiloader-1.16.5` | `:fabric:runClient` | juego Java 8 |
| 14 | 1.16.5 | Forge | `multiloader-1.16.5` | `:forge:runClient` | juego Java 8 |
| 15 | 1.12.2 | Forge | `multiloader-1.12.2` | `:runClient` | FG 2.3, juego Java 8 |

**Loader primario por versión** (para Tests 2-5): Fabric en todas, **Forge en 1.12.2**.
→ objetivos primarios = #1, #3, #5, #7, #9, #11, #13, #15.

**Orden del barrido:** 1.21.11 → 26.2 → 26.1 → 1.20.1 → 1.19.2 → 1.18.2 → 1.16.5 → 1.12.2
(empezamos por la referencia conocida-buena para fijar "cómo se ve lo correcto").

---

## Protocolo de ejecución

1. **Lanzo** el objetivo (build + `runClient`) y aviso cuando esté en el menú/título.
2. **Tú** entras a un mundo creativo de pruebas y pasas el test.
3. **Al confirmar** ("OK, siguiente"): cierro ese Minecraft y lanzo el siguiente objetivo.
4. **Si hay bug:** paro el barrido, lo reproduzco y lo corrijo. **El fix se aplica a TODAS las
   versiones** (paridad 1:1) antes de continuar. Re-lanzo la versión afectada y se re-testea.
5. Cuando un test está ✅ en todos sus objetivos, pasamos al **siguiente test**.

> Escenario base recomendado (crea uno y reúsalo): mundo creativo superplano, un cofre,
> un cofre doble, un barril y una shulker colocados; un stack de varios items en el inventario.

---

## Test 1 · Arranque, keybinds y panel  *(barrido en los 15 objetivos)*

**Sensible al loader** → se corre en Fabric, Forge y NeoForge de cada versión.

- [ ] El cliente llega al menú y entras a un mundo sin crash.
- [ ] Abres un cofre → aparece el panel/overlay del editor con **`O`**; **`Alt`** (mantener) lo muestra y al soltar lo oculta.
- [ ] **`H`** togglea los botones de edición · **`G`** el botón de depósito · **`M`** la lupa — cada uno con su mensaje en pantalla.
- [ ] Abres tu **inventario** (`E`) y el **inventario creativo**: el panel funciona en ambos; en creativo, al pulsar Edit/Filters/Presets **salta al tab de inventario** y el panel de herramientas se ve bien (recuadros de botones visibles).
- [ ] Slots de armadura y mano izquierda **se ven normales** (no negros).
- [ ] **`Esc`** cierra el editor **sin** cerrar el contenedor.

**OK si:** todo lo anterior responde y no hay stacktrace de `chestseparators` en el log.

| Obj | 1 F | 2 NF | 3 F | 4 NF | 5 F | 6 NF | 7 F | 8 Fg | 9 F | 10 Fg | 11 F | 12 Fg | 13 F | 14 Fg | 15 Fg |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| T1 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**Test 1 COMPLETO: 15/15.** 1.12.2 Forge verificado con el **jar publicado en instancia real** (PrismLauncher `AbsoluteOrder1122Forge`, Forge 14.23.5.2847, Java 8): FML carga 6 mods incl. `chestseparators@2.0.0`, Mixin 0.8.7 activo, cero excepciones, entra a mundo. El dev-runClient queda como fallo dev-only (no bloquea el jar).

---

## Test 2 · Separadores y fondos  *(el editor de dibujo — barrido por versión)*

- [ ] Entras en modo edición y dibujas una **línea** con la herramienta **área** (un rectángulo 3×3 → solo contorno exterior).
- [ ] Herramienta **trace** (arrastre en L, cambia de eje) traza bien la línea proyectada.
- [ ] **Goma** (área y trace) borra; **cuentagotas** copia un color existente.
- [ ] **Paleta de 16 colores**: al clicar cada color sale el mensaje en su propio color; los nombres están traducidos.
- [ ] Pestañas **Líneas / Fondo / Combo** cambian y la activa se resalta; aplicas un **fondo** de color a unos slots.
- [ ] **Undo/redo** (`Z`/`Y`) deshace y rehace los últimos trazos.
- [ ] **Copy/paste** (`C`/`V`) del layout entre dos cofres.
- [ ] **Persistencia:** cierras y reabres el cofre → líneas y fondos siguen ahí.

**OK si:** el dibujo se ve nítido, la persistencia aguanta y no hay artefactos de render (paneles oscuros, recuadros que faltan).

| Obj | 1.21.11 | 26.2 | 26.1 | 1.20.1 | 1.19.2 | 1.18.2 | 1.16.5 | 1.12.2 |
|---|---|---|---|---|---|---|---|---|
| T2 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  ← **Test 2 completo 8/8**

---

## Test 3 · Filtros / whitelist + auto-insert + presets  *(barrido por versión)*

- [ ] Asignas items a slots (whitelist) y el slot muestra su **preview**; el **orden del filtro** determina qué slot se rellena primero.
- [ ] **Variantes item+metadata** (sobre todo 1.12.2): manzana dorada **encantada**, **salmón** cocinado, distintos tipos de **madera/lana** se distinguen como filtros separados.
- [ ] Con un cofre lleno: **`S`** deposita solo lo filtrado · **`D`** deposita todo · **`V`+Shift** agarra del cofre según filtro.
- [ ] **Presets:** aplicas un preset de cofre (1-5) y uno de inventario (1-5); **rename inline** de un preset; el nombre y el targetCount **persisten** al reabrir.
- [ ] Los presets por defecto (seed) aparecen bien montados (variantes correctas).

**OK si:** el contenido del cofre tras `S`/`D`/`V` coincide con los filtros, y los presets se aplican y persisten.

| Obj | 1.21.11 | 26.2 | 26.1 | 1.20.1 | 1.19.2 | 1.18.2 | 1.16.5 | 1.12.2 |
|---|---|---|---|---|---|---|---|---|
| T3 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  ← ya verificado previamente por el usuario en todas las versiones

---

## Test 4 · Gestos, auto-deposit y lupa  *(barrido por versión)*

- [ ] **Doble-tap sneak** (rápido) dispara **auto-deposit**: se anima la **tapa del cofre** (las dos mitades en cofre doble) y el **item volador** tiene **brillo correcto** (ni súper-iluminado ni negro — atención a 1.12.2).
- [ ] Un sneak lento **no** dispara el gesto.
- [ ] **`C`+Shift** deposita junk; el resto se queda.
- [ ] **Lupa `M`**: toggle on/off + zoom sobre el slot al pasar el cursor.
- [ ] **Persistencia de estado:** dejas `G`/`H`/`M` en cierto estado, reinicias el cliente → siguen igual.

**OK si:** los gestos disparan correctamente, la animación y el brillo del item se ven bien, y los toggles persisten.

| Obj | 1.21.11 | 26.2 | 26.1 | 1.20.1 | 1.19.2 | 1.18.2 | 1.16.5 | 1.12.2 |
|---|---|---|---|---|---|---|---|---|
| T4 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  ← ya verificado previamente por el usuario en todas las versiones

---

## Test 5 · Persistencia, limpieza y sync  *(el pesado — barrido por versión)*

- [ ] Configuras un **cofre**, un **barril** y una **shulker**; los rompes → su config por-posición (`.dat`) **se borra** (sin huérfanos).
- [ ] La **config global** (keybinds, toggles) persiste tras cerrar y reabrir el juego.
- [ ] **Shulker con layout**: la rompes y la recolocas → conserva/recupera su config según diseño.
- [ ] (Si lo pruebas en **LAN/servidor**): el **hopper** respeta el filtro del slot destino; el **minecart entity filter** se sincroniza; la **whitelist** se sincroniza cliente↔servidor.

**OK si:** no quedan configs huérfanas, la global persiste y (en su caso) el sync de servidor funciona.

| Obj | 1.21.11 | 26.2 | 26.1 | 1.20.1 | 1.19.2 | 1.18.2 | 1.16.5 | 1.12.2 |
|---|---|---|---|---|---|---|---|---|
| T5 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  ← **Test 5 completo 8/8 · batería manual COMPLETA (T1-T5)**

---

## Registro de bugs encontrados

| # | Test | Versión/loader | Síntoma | Estado | Fix aplicado a todas |
|--:|---|---|---|---|---|
| 1 | T1 | 1.21.11 Fabric | No existía keybind para togglear la barra de botones superior (`showEditButtons` no tenía tecla) | ✅ corregido | Sí (8) — nueva keybind `toggle_edit_buttons` default **H**, cableada en mundo + cofre + Hotkeys + lang×20 |
| 2 | T1 | 1.21.11 Fabric | Mantener `Alt` no mostraba el panel izquierdo con el cofre abierto (`isModifierPressed()` usaba `KeyMapping.isDown()`, falso con pantalla abierta) | ✅ corregido | Sí (8) — poll GLFW/LWJGL crudo de la tecla enlazada |
| 3 | T1 | 1.19.2 Forge | Crash al seleccionar el mod en la lista de Forge (`getRootResource` estricta): `logoFile="assets/chestseparators/icon.png"` (ruta con `/`). También el logo fallaba silencioso en el resto de Forge/NeoForge | ✅ corregido | Sí (11 módulos Forge/NeoForge) — `logoFile="icon.png"` + `icon.png` en raíz de resources (patrón de 1.16.5) |
| 4 | T1 | 1.16.5 Forge | dev-runClient crashea en horneado de modelos (`Transformation.func_227987_b_` no remapeado) + `ClassMetadataNotFound` en Mixin | ✅ corregido | Solo 1.16.5 — `forge_version 36.2.42→36.2.34` (recomendada; issue architectury-plugin#45) + juego en Java 8 |
| 5 | T1 | 1.12.2 Forge | dev-runClient: el @Mod no se descubre cuando MixinBooter está en runtime (conflicto FG 2.3). **Solo dev** — el jar publicado carga el @Mod vía `FMLCorePluginContainsFMLMod` | ⏳ dev-only, no bloquea el jar | No aplica (dev-env); se verifica con el jar real |
| 9 | T5 | 1.12.2 Forge (real) | Los filtros de una shulker se perdían al romper/recolocar. DOS causas: (a) el UUID no viajaba en el drop (vanilla usa `saveToNbt`, no `writeToNBT`); (b) `loadShulkerConfig` cargaba solo visual y `requestChestWhitelistsIfNeeded` pedía el whitelist al servidor por posición (vacío tras romper) y lo pisaba | ✅ corregido | Solo 1.12.2 — UUID en `saveToNbt` + `loadShulkerConfig` carga `data.filters` + excluir shulker de la petición al servidor (como ender/minecart) |
| 10 | T3/pick | 1.12.2 Forge (real) | Título de pestaña creativa crudo (`itemGroup.buildingBlocks`) en el picker de filtros: `getTranslatedTabLabel()` resuelve por el LanguageMap común (sin claves `itemGroup.*` vanilla) | ✅ corregido | Solo 1.12.2 — resolver con el client `I18n.format("itemGroup."+getTabLabel())` |
| 7 | T2 | 1.12.2 Forge | El mensaje "Color Copied!/Pasted" no salía teñido con el color copiado (E1 §-codes solo tienen 16 colores nombrados; el overload `showStatus(msg,int)` descartaba el RGB) | ✅ corregido | Solo 1.12.2 — guardar `statusMessageColor` en la sesión y pintar el texto con ese RGB en el `drawString` (las modernas ya usan `TextColor.fromRgb`) |
| 6 | T1 extra | 1.12.2 Forge (instancia real) | Traducciones de keybinds crudas en el Controls de vanilla (`key.chestseparators.*`) + categoría cruda. Causa: `LangFix` solo inyectaba en el `LanguageMap` común (TextComponentTranslation); el Controls usa el `Locale` estático de `client.I18n`, que no se tocaba | ✅ corregido | 1.12.2 — `LangFix` inyecta también en el `Locale` de `I18n` (reflexión por tipo, campo estático) + `key.categories.chestseparators` añadida a lang de 1.12.2 y 1.16.5–1.20.1 (26.x/1.21.11 ya la tenían) |

**Total aprox. de sesiones:** 15 (Test 1) + 8×4 (Tests 2-5) = **47**.
Se puede recortar el Test 5 a un representante por render-era si se hace largo.
