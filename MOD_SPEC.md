# MOD_SPEC — ChestSeparators / *Absolute Order* — feature & visual parity

The **source of truth for what the mod must do and look like**. Every port (see `PORTING_GUIDE.md`) is
validated against this file end to end. Rule from the author: **everything here is mandatory** — a port
is wrong if any item is missing. If a future port makes something here impractical, **ask before
dropping it**, never silently.

Verification method for visuals: run the reference build and the ported build side by side, screenshot
the same screens, and compare. Any visual difference must be fixed in the port, not accepted.

Legend: ⚠ = documented from code/lang strings, author to confirm exact intent.

---

## 1. Two features and their trust boundary (sacred on every loader)

- **Visual Separators** — colored lines + slot backgrounds painted on any container/inventory GUI.
  **Purely cosmetic and client‑local**; per‑player, never shared. In multiplayer, players do **not** see
  each other's layout.
- **Whitelist Filters** — per‑slot rules controlling which items may enter a slot.
  **Server‑authoritative and universal** for block containers (all players share them; enforced for
  manual, shift‑click and hopper). **Client‑local** for entity inventories and the Ender Chest.

| Data | Scope | Shared between players |
|------|-------|------------------------|
| Separators (layout) | client‑local, per‑world | No (personal) |
| Filters — block containers | server‑authoritative, per‑world | Yes (universal) |
| Filters — entities / Ender Chest | client‑local | No |
| Everything | **per‑world** | Global **only** presets, and only if the user opts in |

---

## 2. Opening / closing, and the toolbar ("the guide")

- Opening a chest/inventory shows the **normal vanilla screen** plus the mod's **toolbar** ("guide") of
  icon buttons at the top. Until a button is pressed there is **no mod state** — it behaves exactly like
  vanilla with the toolbar overlaid.
- The editor opens **only via the toolbar buttons** (no keybind opens it).
- Three top entry buttons switch between modes: **Edit Layout**, **Manage Filters**, **Inventory
  Presets** (plus **Chest Presets**). Only one is active at a time.
- Closing: **click outside** the interface (when "Close on Click Outside" is on) **or** the **Escape**
  key. The click‑outside exit sound is unified across every closeable screen.
- Every UI interaction plays a sound (unified click family + the specific sounds below).

## 3. Layout painting (Edit Layout)

- **Initial state** on opening Edit Layout: **Lines** tab, **Draw Area** tool, first palette color
  selected = **red** (one of the 16 presets).
- **Three tabs (paint mode):**
  - **Lines** — paint only separator lines.
  - **Backgrounds** — paint only slot background fill.
  - **Combo Mode** — paint both at once; lines are drawn **only on the outer edge** of the painted
    region (no internal lines between adjacent painted cells).
- **Tools:**
  - **Area** — paints a rectangle from the click‑start corner to the release corner.
  - **Trace** — paints along the exact path the cursor travels.
  - **Eraser** — same Area/Trace behaviors but removes existing paint instead of adding.
  - Area vs Trace behave **identically** for painting *and* for slot selection (§4) — one consistent
    concept mod‑wide.
- **Armor / off‑hand (non‑grid) slots:** a single click paints only the clicked edge (matching the hover
  preview); a corner‑to‑corner drag that crosses the midpoint fills the whole box, exactly like normal
  grid slots.
- **Corners / paint order:** the **last painted** edge wins where two edges meet. This is intentional —
  the player controls which color ends on top by painting order (per‑edge sequence stored).
- **Palette:** 16 standard colors + 8 custom slots per tab. Middle‑click a color to copy; left‑click a
  custom slot to paste. "Shared Custom Colors" setting makes the custom column shared across
  Lines/Backgrounds/Combo or kept per‑tab.
- **Magnifier / loupe** ⚠: optional zoom loupe at the cursor while painting or using the eyedropper;
  shape **circle or square**; can independently show lines / backgrounds / combo; toggled by a keybind;
  a hint message advertises it.
- Toolbar actions: Copy Layout, Paste Layout, Undo, Redo, Clear Lines, Clear Backgrounds, Clear Both,
  Open Palette. Undo/redo is a **single unified history** shared with filters (Ctrl+Z undoes the most
  recent action of either kind), with feedback ("Undone: Layout/Filter").

## 4. Color picker

- **Eyedropper** — samples the exact pixel color wherever you click on screen. Must **never** grab the
  white of its own cursor icon during fast mouse movement (fixed: near‑white samples only trusted after
  being stable a couple of frames).
- **HSV area + hue bar + Hex + R/G/B fields** — behave like a standard paint tool. Keyboard: **arrow
  keys** for fine HSV adjustment, **Tab** to move between the hex/RGB fields; fields are click‑to‑focus
  with caret placement.

## 5. Slot groups & per‑slot rules (Manage Filters / group view)

- A **group** = the set of slots that share one filter.
- **Selection** = same as background painting: click slots one by one, or drag with **Area** (rectangle)
  or **Trace** (path). **Double‑Shift** runs a flood‑fill that selects every slot enclosed by separator
  lines.
- **Per‑slot rules** decide how items may enter the selected slots:
  - **Manual** — placing an item by hand.
  - **Shift** — shift‑click insertion.
  - **Hopper** — hopper transfer (block containers only; **never** on Ender Chest).
  - **Pick Up** — items walked over that go into your inventory (inventory filters).
  Each rule is independently active/inactive; new‑group defaults are configurable.
- **Conflict popups:**
  - **Selection conflict** — selecting a slot that already belongs to another group warns that it will
    leave the old group (losing its filter); options: overwrite / deselect conflicts & continue / cancel.
  - **Unsaved changes** — leaving the filter screen with unsaved edits asks: Save & Exit / Discard
    Changes / keep editing.
  - **Expel invalid items** ⚠ — when a new filter is applied and existing items no longer match, an
    optional prompt ejects them to the player's inventory or drops them (setting "Expel Invalid Items").

## 6. Filters (whitelist editor)

- Left panel = item list with **Allowed** / **Blocked** counts and a **Search** box.
- **Import Extracted** — adds every item currently in the selected slots to the filter.
- **Allow All** — whitelists every **obtainable** item (the ones shown; items unobtainable in survival —
  bedrock, spawners, barrier, command block, etc. — are never shown/added).
- **Clear All** — empties the filter (so any item may enter).
- **Filter order = priority:** auto‑insertion (shift / hopper / grab / push / pull / deposit) fills slots
  following the item **order** the user set in the filter.
- **Drag interactions:** an item drags as a **horizontal bar showing its sprite + name**; drag to
  **reorder** (raise/lower priority), drag out to the central panel to **remove**, drag in from the
  central panel to **add**. New sounds: add → item_frame.add_item, remove → item_frame.remove_item;
  a pure reorder keeps the neutral UI click.
- **Tag browsing** ⚠: middle‑click an item to browse its tags (hint message advertises it).
- **Previews are exact:** every preview (insertion preview, Load preview, push/pull preview) must show
  precisely what the committed action produces — preview and result are always identical.

## 7. Presets

- Three kinds: **Inventory presets**, **Small‑chest presets**, **Large‑chest presets** (chest presets
  split by container size). Each holds a layout **and** its filters, for fast reuse.
- **5 default presets per kind ship with the mod** and are always present on install (the user may
  delete them). Named defaults: chest = Building, Mining, Food & Farming, Redstone, Combat; inventory =
  Miner, Builder, Explorer, Farmer, Nether & End.
- **Global vs per‑world:** presets are per‑world unless the user enables the "global presets" option, so
  the same presets are available across all worlds/servers without recreating them.
- **Hover preview** shows the preset's layout **and** filters together, drawn exactly like the rest of
  the mod. **Detail panel** lists the groups in order armor/off‑hand → hotbar → main inventory
  (top‑left→bottom‑right). **Inline rename**; **overwrite** keeps the existing name and warns before
  replacing. Paging for many presets.

## 8. Auto‑deposit / push / pull / grab

- **Push to Chest Filters** (hotkey + toolbar) — moves matching inventory items into the open chest's
  filters, **reordered to the filter's order**. **+Shift** also fills the chest's empty slots.
- **Pull to Inventory Filters** (hotkey + toolbar) — moves the chest's matching items into your
  inventory filters, reordered. **+Shift** also pulls unfiltered items into empty inventory slots.
- **Auto‑Deposit to Nearby Chests** ⚠ — one trigger sorts your whole inventory into nearby filtered
  chests. Trigger = **Double‑Sneak** (or keybind); configurable **radius**, **through‑walls**,
  **animation**, and **particle trail**. Reports count deposited / none in reach. A complementary
  **auto‑grab** reports "Grabbed N" / "Nothing nearby to grab".
- **Animation:** items visibly fly from the player to the destination chest/shulker/Ender Chest, which
  **opens** to show each item entering, then closes. Instant (no animation) when disabled.
- **Lock Hotbar on Auto‑Sort** — optional setting: when a push/pull/auto‑deposit re‑sorts the inventory,
  the **hotbar is left untouched** so the item in hand is never swapped away. Must remain a user toggle.
- **Button pulses:** push pulses the deposit button; pull pulses the fill button (150 ms).
- **Specific messages** (full set in the lang files): `push_no_filters` (container has no filters yet),
  `push_no_slot` (filters exist but no free slot), `pull_no_filters` (inventory has no filters yet),
  `pull_no_match` (filters exist but nothing matches). Also deposit/grab done/none variants. Look/feel of
  "no valid slots" and "nothing nearby" messages is unified.

## 9. Multiplayer / networking

- **Chest lock:** because block‑container **filters are universal**, while one player is editing a
  chest's filters no other player may edit them — a second player pressing the filters button gets the
  warning "Someone is already editing this chest!". **Layout editing is personal and never locks.**
- Locks cover **double chests atomically** (both halves acquired/released together); grant/deny/release
  is server‑driven.
- **Sync:** block‑container filters are persisted server‑side and enforced server‑authoritatively. Entity
  filters/layout are client‑local. (Minecart‑type entities sync their filters to the server so the
  **hopper** rule can be enforced; old filters need one re‑save to sync.) ⚠ author to confirm wording.

## 10. Special inventories

- **All container types** behave like a chest: chest, trapped chest, shulker box, barrel, chest boat,
  chest minecart, double chests, etc.
- **Ender Chest** — filters are **personal per player** (Ender Chests differ per player) and must **never
  offer the hopper rule** (a hopper cannot insert into an Ender Chest).
- **Shulkers / minecarts / chest boats** key their filters+layout **by ID**, so a shulker keeps its
  filters and layout when broken and re‑placed.
- **Armor & off‑hand** are isolated **non‑grid** cells but are full filter/layout targets like any slot.

## 11. Where separators render

- Inside **every** container GUI (chests, trapped chests, shulkers, Ender Chest, chest boats, minecarts,
  barrels, …).
- On **every player‑inventory slot** in any screen: main inventory, hotbar, **armor**, and **off‑hand**.
- **Hotbar separator lines** are drawn on the in‑game HUD hotbar.
- Both **layout and filters** are supported in all these places.

## 12. Recipe book / potion effects (no overlap)

- The mod's hover panel (the one showing the filter for the slot under the cursor) must **never overlap**
  normal vanilla interfaces. When a mod sub‑screen (layout/filters/presets) is open: close the recipe
  book and re‑center the whole GUI (button included); hide the potion‑effects display; when returning to
  the normal inventory, restore the recipe book if it was open. With book+potion open, the preview docks
  to the far left.

## 13. Cristal design system (visual parity — all mandatory)

Glass panels, rounded "blob" group shapes with connectors/rim, rounded rects & insets, the icon set,
badges, previews, and all animations (button press flash, drop ghosts, delete fly‑out, deposit fly, blob
rendering) are **essential** to the look. A concept must look **identical everywhere** it appears
(e.g. the preset Load preview reuses the exact live‑editor blob methods). Validate by screenshot
comparison old vs new build; fix any drift in the port.

## 14. Config settings (ModMenu / cloth‑config — full list, all must exist)

Grouped as in the config screen; exact wording/behavior per the English tooltips in `lang/en_us.json`
(source of truth):

- **Interface:** Show Left Panel, Enable Preview Animation, Show toolbar (master), Close on Click
  Outside, Dark Mode.
- **Layout:** Background Transparency, Line Transparency, Shared Custom Colors.
- **Filters:** Default Rule Manual / Shift / Hopper (defaults for new groups), Expel Invalid Items.
- **Toolbar buttons:** per‑icon toggles — Edit lines, Filters, Deposit (inventory→chest), Deposit
  (chest→inventory), Inventory presets, Chest presets.
- **Magnifier:** Show Magnifier, Magnifier Shape (Circle/Square), Magnifier tabs Lines/Backgrounds/Combo.
- **Auto‑deposit:** Auto‑Deposit to Nearby Chests, Trigger Double‑Sneak, Radius, Deposit Through Walls,
  Deposit Animation, Deposit Trail Effect, Lock Hotbar on Auto‑Sort (+ its dimmed second tooltip line).
- **Global presets** toggle ⚠ (the opt‑in that makes presets shared across worlds).

## 15. Keybinds (category "Absolute Order" — keep identical across all ports)

Toggle Preview Panel, Show Panel Modifier (hold), Toggle Magnifier, Auto‑Deposit to Nearby Chests,
**Push to Chest Filters**, **Pull to Inventory Filters**. (Opening the editor is **not** a keybind — it
is the toolbar button.)

## 16. Sounds

- **Every UI interaction has a sound.** Base UI = `UI_BUTTON_CLICK` at varied pitches (unified exit sound
  on click‑outside across all closeable screens).
- **Filter add via drag** = `item_frame.add_item`; **remove via drag** = `item_frame.remove_item`; pure
  reorder keeps the UI click.
- **Auto‑deposit** = item pickup + container open/close (chest / shulker / Ender Chest) as items fly in.

## 17. Localization

All user‑facing text is translation keys. **20 language files** must be kept and every new/changed string
translated into all of them. Keybinds and translations are preserved **identically** across every port.

---

## 18. Parity priority

**Everything above is mandatory.** There is no "nice‑to‑have" tier. When porting, if any feature or
visual cannot be reproduced on a target loader/version, **stop and ask the author** before shipping the
port without it. Confirm the ⚠ items with the author when first encountered.
