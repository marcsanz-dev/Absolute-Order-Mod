# CLAUDE.md

Guidance for working in this repository.

## Project

**Chest Separators** (mod id `chestseparators`, internal name *"Absolute Order: Custom Chest Auto-Sorter"*) is a Minecraft **Fabric** mod targeting Minecraft 1.21.11 on Java 21. It ships two largely independent features:

1. **Visual Separators** — colored lines and slot backgrounds painted onto any container GUI. Purely cosmetic and **client-local**: stored per-player in NBT `.dat` files, never sent to the server.
2. **Whitelist Filters** — per-slot rules that control which items may be inserted into a container, enforced for manual placement, shift-click, and hoppers. **Server-authoritative** for block containers (persisted in the block's data component / chunk NBT) and **client-local** for entity inventories and the Ender Chest.

The two features are edited through the same in-GUI editor overlay but persist through completely separate paths — keep that boundary in mind when changing one.

## Build & Run

The project uses Gradle with `fabric-loom`. **There is no Gradle wrapper checked in** (`gradlew`/`gradlew.bat` are absent), so use a locally installed Gradle or the IDE's Gradle integration.

```bash
gradle build          # Compile, run Mixin processing, and produce the remapped jar in build/libs
gradle runClient      # Launch a dev Minecraft client with the mod loaded
gradle runServer      # Launch a dedicated dev server (validates server-only class loading)
gradle clean          # Wipe build outputs
```

> **Gotcha:** `gradle.properties` hardcodes `org.gradle.java.home` to a machine-specific JDK path
> (`C:/Users/Usuario/.jdks/ms-21.0.10`). On any other machine this line must be edited or removed so
> Gradle can resolve a Java 21 toolchain itself. The compile target is pinned to Java 21 via `build.gradle`.

Always smoke-test changes that touch Mixins or the `mixin/` (non-`client`) package with **both** `runClient` and `runServer` — a server-side `ClassNotFoundException` on a client-only class is the most common breakage (see `WorldMixin` and its dual `EnvType.CLIENT` + `isClient()` guard).

## Architecture

Single source set (`src/main`) with an `environment: "*"` mod, split logically by package. Two Fabric entrypoints:

- `ChestSeparatorsMain` (`main`) — common/server bootstrap: registers network payloads, the data component, and all **server-side** packet receivers. Holds `LOCKED_CHESTS`, the editor concurrency lock map.
- `ChestSeparatorsClient` (`client`) — client bootstrap: keybindings, input handlers, client networking, and the whitelist-sync receiver.

Two Mixin configs, declared in `fabric.mod.json`:
- `chestseparators.mixins.json` — common Mixins (`package io...chestseparators.mixin`).
- `chestseparators.client.mixins.json` — client-only Mixins (`mixin.client` subpackage), gated by `environment: "client"`.

### Package map

| Package | Responsibility |
| --- | --- |
| `mixin/` | Common bytecode injection: inventory insertion rules, hoppers, block-entity NBT, item drops. |
| `mixin/client/` | Client bytecode injection: GUI rendering hooks, screen accessors, chest-open interception. |
| `access/` | Duck-typed interfaces (`IWhitelistProvider`, `IShulkerUUIDProvider`) mixed into vanilla classes. |
| `network/` | Custom packet payloads + codecs. `ModClientNetworking` registers client senders. |
| `data/` | `ChestConfigManager` (separator + whitelist persistence, undo/redo, clipboard) and `SlotWhitelist`. |
| `config/` | `GlobalChestConfig` — global user settings, serialized via Cloth Config. |
| `client/ui/` | The editor: state, geometry, layout, rendering, input, session data. |
| `client/ui/screens/` | Editor sub-screens (state machine — see below). |
| `client/ui/widgets/` | Reusable custom GUI widgets. |
| `registry/` | `ChestSeparatorsComponents` — data component registration. |
| `compat/` | REI and EMI exclusion-zone plugins (declared via `rei_client` / `emi` entrypoints). |
| `integration/` | `ModMenuIntegration` — Cloth Config screen + keybinding rebinding. |
| `event/`, `util/` | Keybinding dispatch and small client-side state holders. |

### The editor (`client/ui`)

`ChestSeparatorsEditor` is instantiated per container screen by `mixin/client/GenericContainerScreenMixin` (which targets `HandledScreen` and validates the screen is a chest/barrel/shulker/cargo-bearing horse). It owns an `EditorSessionData` and delegates to sub-screens.

Sub-screens implement `IEditorSubScreen` and extend `AbstractEditorScreen`. The active one is chosen by an **`EditorState`** enum (`HIDDEN`, `DRAW_LINES`, `VIEW_GROUPS`, `SELECT_SLOTS`, `EDIT_FILTER`):
- `ScreenDrawLines` — paint separator lines/backgrounds (pencil/brush/combo tools, area + trace modes).
- `ScreenColorPicker` — HSV picker, eyedropper, 8 custom palette slots per layer.
- `ScreenViewGroups` — visualize and assign filter groups, slot selection, conflict resolution.
- `ScreenEditFilter` — edit a slot's item whitelist (creative tabs, search, import/export).

Mouse coordinates of `(-1, -1)` are deliberately passed to background widgets while a modal popup is open, so they don't draw hover state or tooltips behind the modal. This idiom appears in several screens — preserve it.

### UI design & interaction conventions (MANDATORY for every new menu, window, widget)

Any new interface **must look and behave like the rest of the mod**. Before building UI, study an existing window (e.g. `ScreenColorPicker`, `ScreenViewGroups`, `PresetsMenu`) and match it. Concretely:

**Visual style**
- Container windows/panels: fill with `UiColors.SURFACE_DARK` / `SURFACE_LIGHT` (dark-mode aware via `GlobalChestConfig.instance.darkMode`) and draw a **bevel** on the border with `drawDarkBevel(...)` (raised for panels, `sunken=true` for inset wells/indicators). Never ship a flat `drawStrokedRectangle` box as the final look for a real window.
- Buttons: reuse `WideButtonWidget` / `ActionIconButtonWidget`. **Every button carries an icon image** from `ModTextures.ICON_*`, not just text. The close/exit control is a labeled button with `ModTextures.ICON_CANCEL` (button text `button.chestseparators.exit`), **never** a bare `"x"` glyph drawn by hand.
- Indicators, ticks, badges, etc. are **generated PNG textures** under `assets/chestseparators/textures/gui/` (32×32, drawn via `context.drawTexture(... GUI_TEXTURED ...)`), not pixels hand-placed with `context.fill`. Generate a texture when you need a new glyph.

**Interaction**
- **Mutual exclusion of mode toggles:** the top editor icons (layout = `entryButton`, filters = `whitelistButton`, presets = `presetsButton`/`chestPresetsButton`) are radio-like — **only one may be selected/pressed at a time.** Activating one must deselect the others (close the open sub-screen via `toggleState(EditorState.HIDDEN)` / close the presets menu). Apply the same rule to any new set of mode toggles.
- Modal overlays keep the top mode icons visible and clickable, hide the unrelated sub-panels (e.g. the left whitelist preview panel), and pass `(-1,-1)` to anything that must not react behind them.
- Hover previews fire from the **specific control** that owns them (e.g. only a row's *Load* button), not from the whole row or from controls whose action wouldn't change anything (e.g. *Save*).
- **A preview must ALWAYS show the exact result the action produces.** This is non-negotiable: whenever you change what a button does (placement order, group re-sort, keep-limits, hotbar handling, etc.) you must mirror that change in its preview in the same commit. The push/pull previews (`updateDepositPreview` / `updateFillPreview` → `applyPreviewReorder`) are simulations of `executeDeposit` / `requestFillFromOpenChest` + the server's `reorderFilteredGroups`; if the two ever diverge the preview is a bug. Prefer sharing the ordering primitive (`FilterPriority`, the reorder logic) between action and preview so they can't drift.
- **One concept, one look — everywhere.** Any element that represents the same thing must be rendered identically in every part of the mod. A filter blob is rounded (`UiTheme.roundRect`) with a marked border (`UiTheme.roundBorder`) and same-group connectors; it must never appear as a flat, square, borderless fill in some other screen (e.g. the preset Load preview). The same holds for buttons, badges, panels, messages, colours. When you add or change a shared visual, hunt down every place it's drawn and keep them in lock-step; never let one screen keep an older style. Reuse the shared renderer (e.g. the filter-blob draw in `GroupBlobRenderer`) instead of re-implementing a look.
- New user-facing strings → add the key to **all 20** `lang/*.json` files (`en_us.json` is the source of truth).

### Whitelist data flow

1. Client opens a container → `mixin/client/ChestInteractionMixin` records the position → client sends `WhitelistRequestPayload`.
2. Server reads the container's `IWhitelistProvider` and replies with `WhitelistPayload`.
3. Client injects the whitelist into the real block entity **and** the dummy GUI inventory (the "flicker killer" in `ChestSeparatorsClient`).
4. On edit, client sends `WhitelistPayload` back; server persists, broadcasts to co-viewers via `PlayerLookup.tracking`, and — if `expelInvalidItems` is enabled and all three rules block an item — ejects offending stacks.

Insertion enforcement order (deposit priority) is a **4-pass** sweep: filtered stacking → filtered filling → unfiltered stacking → unfiltered filling.

Double chests are handled by merging both halves into a `DoubleInventory` server-side; the editor lock acquires **both** block positions atomically (`getAssociatedPositions`).

### Persistence locations

- Visual separators: `run/config/chestseparators/<world>/separators/*.dat` (NBT, keyed by block position/dimension).
- Custom palette: `run/config/chestseparators/<world>/world_palette.dat`.
- Ender Chest whitelist: `run/config/chestseparators/<world>/separators/ender_chest.dat` (client-local — hoppers can't reach the Ender Chest, so it never goes server-side).
- Block-container whitelists: stored server-side via the registered data component (travels with the block in chunk NBT).

## Conventions

- **All comments are in professional English.** This was enforced repo-wide; do not introduce Spanish comments. Comment the *why*, not the *what*; avoid decorative banner comments.
- Mixins use SpongePowered `@Inject`/`@Redirect` with `compatibilityLevel: JAVA_21`. Client-only behavior must be guarded for the dedicated-server class path.
- Duck interfaces in `access/` are the sanctioned way to attach state to vanilla classes — prefer them over reflection.
- User-facing strings are translation keys resolved through `Text.translatable`; add new keys to **every** `lang/*.json`, with `en_us.json` as the source of truth.
- `ChestConfigManager` and `GlobalChestConfig` are client-only singletons (`@Environment(EnvType.CLIENT)`). Never reference them from common/server code in `ChestSeparatorsMain` or the non-client Mixins.

### Ponytail — minimal code (MANDATORY)

Write the minimum code that works. Before adding anything, climb the decision ladder (stop at the first rung that holds, *after* understanding the problem): **1)** does it need to exist? (YAGNI) **2)** already in this codebase? reuse it **3)** stdlib does it? **4)** native platform feature? **5)** installed dependency? **6)** can it be one line? **7)** only then the minimum that works. Prefer deletion over addition; boring over clever; fewest files; shortest working diff. Fix root causes at the source, not per-caller symptoms. Never cut corners on understanding, validation at trust boundaries, error handling, security, accessibility, or anything explicitly requested. Mark an intentional simplification with a `// ponytail:` comment noting its ceiling and upgrade path.

## Git

Commits follow Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`) with a descriptive body explaining motivation. Keep unrelated changes in separate commits.

**Authorship (MANDATORY):** all work is authored 100% by the repository owner (`marcsanz-dev`). Never add AI/assistant attribution to commits — no `Co-Authored-By: Claude ...` trailer, no "Generated with Claude Code" line, no assistant name as author or committer. Commits must carry only the owner's identity. This keeps the assistant off the project's GitHub contributor list.

## Known follow-ups

- `gradle.properties` and `build.gradle` still contain a few Spanish comments (the English cleanup covered `.java` sources only).
- No Gradle wrapper is committed — consider adding `gradlew`/`gradlew.bat` for reproducible builds.
