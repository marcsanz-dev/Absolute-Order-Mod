# Absolute Order

**Absolute Order** (formerly *Chest Separators*) is a Minecraft utility mod for organizing containers and your inventory. It combines a purely visual layer — colored dividers and slot backgrounds painted onto any container GUI — with a powerful per-slot **filter** system that controls which items may enter each slot, and one-key **auto-deposit / auto-pull** to move items between your inventory and nearby storage.

Version **2.0** ("The Liftoff Update") rebuilt the mod as a **multi-loader, multi-version** project: one codebase now ships for Fabric, NeoForge and Forge across a wide range of Minecraft versions.

---

## Features

### Visual separators (cosmetic, client-side)
- Paint colored **lines** between slots and **backgrounds** on slots of any container (chests, barrels, shulker boxes, entity inventories, the Ender Chest…).
- Full **HSV color picker** with an eyedropper and per-layer custom palette slots.
- **Drag & paint** with pencil/brush/area/trace tools, plus a smart eraser and per-container reset.
- **Undo / redo** and an NBT **clipboard** to copy a whole layout onto another container.
- Purely client-side and cosmetic: stored per-player on your machine, never sent to the server.

### Whitelist filters (functional)
- Assign each slot a **filter** of allowed items; group several slots into one logical filter.
- Enforced for **manual placement, shift-click, and hoppers** (each rule can be toggled per filter).
- The filter's item order is a **priority order** you arrange by dragging — it decides which slot an item lands in.
- **Presets** for quick reuse, import from the creative tabs / search, and export.
- Filters on **block containers are server-authoritative** (they travel with the block); entity inventories and the Ender Chest are client-local.

### Auto-deposit & auto-pull
- One key sweeps matching items from your inventory **into** nearby filtered storage (auto-deposit), or pulls items **from** storage to top up your filtered inventory slots (pull).
- Live **previews** show exactly what an action will do before you commit it.

### Quality of life
- Cloth Config settings screen and rebindable keys.
- REI / EMI / JEI exclusion-zone integration so the editor never overlaps recipe UIs.
- Localized into 20 languages.

---

## Supported versions & loaders

| Minecraft | Loaders |
|---|---|
| 26.2, 26.1 | Fabric, NeoForge |
| 1.21.11 | Fabric, NeoForge |
| 1.21.1 | Fabric, NeoForge |
| 1.20.1 | Fabric, Forge |
| 1.19.2 | Fabric, Forge |
| 1.18.2 | Fabric, Forge |
| 1.16.5 | Fabric, Forge |
| 1.12.2 | Forge |

> Quilt is covered by the Fabric jar. Each release jar is named `AbsoluteOrder-<version>-<loader>-mc<version-or-range>.jar`.

---

## Installation

1. Install the matching loader for your Minecraft version (Fabric, NeoForge, or Forge).
2. On Fabric, also install the **Fabric API**; on all loaders, **Architectury API** is required (Forge/NeoForge builds bundle what they need — see the download page notes).
3. Drop the jar for your version and loader into your `mods` folder.

> **Playing on a server?** Filters for block containers are **server-authoritative**, so the mod must be installed **on the server** as well as on the client for those filters to persist in multiplayer. The visual separators and inventory filters are client-side and work without a modded server.

---

## Building from source

The project uses Gradle with Architectury Loom. Each version lives in its own module (`multiloader`, `multiloader-1.20.1`, `multiloader-1.12.2`, …). Build a module with its required JDK, for example:

```bash
# from a version module directory
./gradlew build
```

JDK requirements: **Java 21** for the modern ports, **Java 25** for the 26.x ports, and **Java 11** for the legacy 1.12.2 port.

---

## License & credits

Created and maintained by **marcsanz-dev**. See the in-repo license for terms.
