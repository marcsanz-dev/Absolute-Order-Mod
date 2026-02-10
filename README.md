# 📦 Chest Separators | Visual Inventory Organization

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Fabric](https://img.shields.io/badge/Loader-Fabric-c9b889?style=for-the-badge&logo=fabric&logoColor=black)
![Render](https://img.shields.io/badge/Rendering-Mixin_Injection-blueviolet?style=for-the-badge)
![NBT](https://img.shields.io/badge/Data-Persistent_NBT-2D7D9A?style=for-the-badge)

**Chest Separators** is a lightweight, client-side utility mod designed to enhance inventory management UX without altering server-side data. Unlike traditional mods that require crafting physical items (wasting inventory slots), this project implements a **Virtual Overlay System**, allowing users to draw visual dividers directly onto the container GUI.

> **⚠️ Engineering Focus**
> This project demonstrates advanced **Java Bytecode Manipulation** using the Mixin framework to inject rendering hooks into the vanilla pipeline, ensuring compatibility and high performance.

---

## 📸 Interface & Workflow

The user experience focuses on fluidity and speed ("Drag & Paint"), minimizing clicks for large-scale organization.

| **The "Hero" View** | **Efficient Interaction** |
| :---: | :---: |
| <img src="img/hero-showcase.png" width="300"> | <img src="img/drag-paint-feature.png" width="300"> |
| *Clean visualization. Separators render seamlessly behind items.* | *Fluid input handling. Users can click and drag to paint lines instantly across slots.* |

| **Integrated Tooling** | **Universal Support** |
| :---: | :---: |
| <img src="img/editor-ui-tools.png" width="300"> | <img src="img/entity-support.png" width="300"> |
| *Contextual Editor UI. Features a palette, eraser, and clipboard logic.* | *Robust architecture. Works on static blocks (Chests) and dynamic entities (Donkeys).* |

---

## 🏗️ Technical Architecture

The core engineering challenge was rendering custom UI elements inside the vanilla container screen without causing conflicts or visual artifacts (Z-fighting).

### 1. The Rendering Pipeline (Dual-Layer Strategy)
To achieve the visual effect where lines appear *behind* items but *above* the background texture, the rendering logic is split into two injection points:
* **Layer 1 (Background):** Injected at the `HEAD` of the `drawSlots` method. This renders the persistent separator data relative to the container's coordinate system. Crucially, this happens **after** the matrix translation to the GUI origin but **before** the item rendering loop.
* **Layer 2 (Overlay):** Injected at the `TAIL` of the `render` method. This handles the transient Editor UI (buttons, palette, tooltips) and the "Preview Lines" during a drag operation, ensuring they are always on top (High Z-Index).

### 2. Context-Aware Persistence
The mod employs a **Polymorphic Data Strategy** to save configurations, handling the disparate nature of Minecraft containers:
* **Static Blocks:** Uses `BlockPos` + `DimensionID` to create unique NBT files for chests, barrels, and shulker boxes.
* **Dynamic Entities:** Detects if the opened inventory belongs to an entity (e.g., Llama, Minecart) and switches strategy to use the entity's persistent `UUID`.

### 3. Mixin-Based Interception
Instead of extending classes (which limits compatibility), the mod uses **SpongePowered Mixins** to surgically modify `HandledScreen` and `GenericContainerScreen`.
* **Input Sandbox:** When "Edit Mode" is active, the Mixin intercepts mouse events at the `ScreenMouseEvents` level, consuming them to prevent vanilla item interaction (pickup/drop) while painting lines.

---

## 🚀 Key Features

### 🎨 Non-Destructive Visualization
* **Zero Slot Waste:** Separators are purely visual client-side renderings. They do not exist as items, leaving 100% of the inventory available for storage.
* **Server-Agnostic:** Since no server-side logic or blocks are added, this client mod works on any multiplayer server (Vanilla, Spigot, Modded).

### 🛠️ Advanced Editor Suite
* **Clipboard System:** Implements a deep-copy mechanism to replicate layouts. Users can copy the separator configuration from one chest and paste it onto another instantly.
* **Smart Erase:** The interaction logic detects context—clicking an existing line with the same color acts as an eraser, while painting over it with a new color updates it.

### ⚡ Performance Optimization
* **Lazy Loading:** Configuration data is only loaded from disk (NBT I/O) when a specific container is opened, keeping memory footprint negligible.
* **Bitwise Packing:** Line data for a single slot (Top, Bottom, Left, Right + Color) is compressed into efficient integer bitmasks rather than heavy objects.

---

## 💻 Installation & Setup

1.  **Prerequisites:** Install [Minecraft Java Edition](https://www.minecraft.net/) and the [Fabric Loader](https://fabricmc.net/).
2.  **Fabric API:** Ensure the [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) mod is installed.
3.  **Deployment:** Drop the generated `.jar` file into your `%appdata%/.minecraft/mods` folder.
4.  **Usage:** Open any chest and click the **Pencil (✎)** icon to start organizing.
