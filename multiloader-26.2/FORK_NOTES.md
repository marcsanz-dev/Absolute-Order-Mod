# Absolute Order — local fork handoff (Minecraft 26.2 source)

Baseline: user-supplied `Absolute-Order-Mod-main.zip`, module `multiloader-26.2`, upstream mod 2.0.0. The supplied archive contains NO 26.3 module. All historical Minecraft modules in the repository are untouched. The mod ID remains `chestseparators` so this fork replaces upstream, rather than running beside it. MIT license retained.

## Changes

1. Player inventory filters protect matching items during nearby Auto Deposit and open-container Push. With existing target `0` (displayed **Keep: All**), items in matching filtered slots never leave through these actions. A positive value retains that many of each item listed in the filter, across the group's slots (earlier slots have priority). Unfiltered stacks and unrelated items remain eligible. Client deposit/pull previews use corresponding quantity calculations.
2. The inventory-filter editor now has a compact drag/click slider with five stops, **All / 64 / 32 / 16 / 1** from left to right. **All** is distinct from **64**: All protects every matching item physically in filtered slots, whereas 64 protects at most 64 across each group/item. The `targetCount=0` legacy behavior and arbitrary saved values continue to load; moving the new slider selects one of the five stops. `SlotWhitelist.targetCount` codecs, undo/redo and clipboard copy preserve the value. Filtered Pull respects positive amounts; Shift-Pull deliberately retains upstream's override behavior, pulling all that fits.
3. Pickup slot preference is now scoped to `ItemEntity.playerTouch`; inventory actions such as closing a screen with cursor items no longer run pickup-filter routing. Existing `lockHotbarOnReorder` setting is left untouched.
4. Ender Chest filters remain saved in the client's `ender_chest.dat`, but are synchronized to the player's live server-side Ender Chest inventory on opening and editing. The server validates entries against its *actual container size* rather than assuming 27 or 54. Shift-click enforcement uses upstream's existing `IWhitelistProvider` mixins. Modded 54-slot compatibility still requires gameplay verification.
5. Refill of filtered player **main inventory** (raw slots 9..35) now merges matching unfiltered main-inventory stacks into existing home stacks, then fills empty home slots in filter item priority. It does not take from the hotbar (0..8), offhand, armor, or *any* filtered source slot; it never replaces a nonmatching item already occupying a home slot. The server runs this after a filtered-source -> unfiltered-main manual placement, after ground pickups, and before nearby Auto Deposit. The open-container Push action performs the same consolidation using normal synchronized inventory clicks before computing deposit allowances. A filtered 1 + adjacent unfiltered 64 becomes filtered 64 + unfiltered 1 before deposit; Keep: 1 still ultimately protects 1 across the group. In a chest UI, manual moves from an **unfiltered source** to an unfiltered destination do not proactively refill until Push or a subsequent ground pickup; manual moves **out of a filtered main slot** do trigger refill on the server.

Known limitation: the **hover preview** for Push is calculated from the current inventory layout, before its first-click consolidation. If you have pre-existing stray matching stacks in the main inventory, the hover preview might momentarily differ from the final result. The actual transfer uses the consolidated inventory. Recheck the preview after one Push or an inventory-slot move and report gameplay discrepancies.

## Build (WSL/Linux, **JDK 25**, network required for Gradle dependencies)

```bash
cd multiloader-26.2
chmod +x gradlew
java -version
./gradlew :common:test :fabric:build --no-daemon
```

The publishable Fabric artifact is expected under `fabric/build/libs/` with a name like `AbsoluteOrder-2.0.0-fork.1-fabric-mc26.2.jar`. Replace the old mod JAR; do NOT run both together. Install the fork on **both** the Fabric client and dedicated server for filter synchronization. This is *not* a Minecraft 26.3 build.

## Current verification

- `git diff --check`: passed locally.
- Pure Java quantity-policy checks (11 scenarios) and dedicated retention-slider stop checks: passed using available JDK 21.
- Filter-refill logic compiled against isolated Minecraft API stubs using JDK 21; 8 standalone scenarios passed (stack fill, empty home, hotbar isolation, protected sources, item/component compatibility, priority). This is **not** a real mod compile.
- Full Gradle/compile/tests: **not run successfully**. The execution environment has Java 21, not Java 25, and could not download the Gradle 9.4.0 wrapper distribution (`UnknownHostException: services.gradle.org`).
- Minecraft singleplayer/multiplayer and Ender Chest 54-slot compatibility: **not tested**.

## Manual test matrix (back up client configs and the world first)

1. Filter arrows in hotbar, Keep: All. Carry 64. Auto Deposit next to a filtered chest; none of those 64 may leave.
2. Change Keep to 1. Carry 64 arrows in the filtered slot; Auto Deposit must move at most 63 and leave 1. Repeat with Push from an open filtered chest, and compare preview to result.
3. Place extra arrows in an unfiltered slot; automatic deposit should move those freely, retaining the 1 arrow in the filtered slot. Test two slots in one filter group, testing total retention across the group.
4. Pull from a chest with an arrow filter, Keep: 1: it should pull only 1 when none is held and 0 afterward. Shift-Pull explicitly ignores the cap as before. Confirm the preview matches each case.
5. Manually place spruce logs in the hotbar and close/reopen inventory. No filter-driven relocation should occur. New logs picked up from the ground should still prefer dedicated empty slots. Test stacks and empty slots.
6. Ender Chest 27 slots: configure filters, close/reopen, shift-click into matching slots, verify wrong items are refused as configured. Repeat against your **actual 54-slot Ender Chest mod**, with filters above slot 27, after logout/reconnect, and after server restart. Repeat after changing filters, and try both directions.
7. Validate an unrelated chest and vanilla shift-click behavior, plus all applicable 3 filter-rule toggles. Verify the server and client are both running the fork, not a mixture of fork and upstream JARs.

**Rollback:** keep the old mod JAR and a copy of `config/chestseparators/` on the client, plus a server world backup. Do not deploy the 26.2 artifact onto Minecraft 26.3. For a proper 26.3 port, obtain the exact corresponding upstream 26.3 source module first; do not just change `minecraft_version` in `gradle.properties`.

## Follow-up fork revision (additional commits 0008–0009)

- `fix(inventory): refill filtered main slots before deposits` — adds server-side movement recovery, ground-pickup consolidation, nearby Auto Deposit pre-pass, and client-synchronized open-container Push pre-pass.
- `feat(filters): replace quantity button with retention slider` — five-step click/drag quantity control (All, 64, 32, 16, 1) with safe preservation of older custom quantities and unit tests.
- These patches apply **after** the first seven fork patches. They are not a port to Minecraft 26.3.
- Multiplayer Ender Chest sync (earlier test #6), 54-slot Ender Chest compatibility, and the new manual-click integration still require in-game verification. Keep production server 26.3 untouched.

## Corrective revision after 0010 — inventory priority, Push preview, continuous Keep slider

1. Client inventory-filter state is now populated alongside the C2S sync so predicted inventory
   clicks and the server's menu clicks use identical filter maps. The inventory menu intercepts
   shift-clicks from unfiltered main slots and refills dedicated main slots first; filtered-origin
   cursor placements into unfiltered main slots trigger refill even with an unfinished cursor stack.
   Manually placed hotbar items are never sources for that refill.
2. Refill uses one shared copied-stack implementation on both gameplay inventories and the Push
   preview. The preview simulates refill, exact per-group Keep quantities, the same four container
   deposit passes, and the post-Push sort, then displays the final predicted stacks for every
   changed slot. It doesn't mutate inventory while the player hovers the button.
3. Keep slider now permits integer values 1–64, with marked reference values All, 64, 32, 16, 1
   from left to right. Wheel over the slider adjusts by one for precise selection. All is still
   the separate saved value 0; previously saved larger values are preserved until manually edited.
4. Headless Java checks passed for Keep slider mapping and for inventory slot refill using Minecraft
   stub objects (1 + 64 -> 64 + 1, Keep 1 -> 63 + 1 movable, hotbar untouched, snapshot unchanged).
   These are NOT Gradle tests or proof of a successful full mod build. Java 25 compilation and in-game
   testing (including expanded Ender Chests and dedicated multiplayer synchronization) remain required.

Suggested regression: a filtered slot with 1 Ender Chest plus an unfiltered main slot with 64
should preview AND actually deposit 64 with Keep: 1, leaving 1 in the filtered slot. Check
manually moving half/full stacks out and a shift-click from an unfiltered main slot; verify
hotbar behavior remains intentional, and compare Push hover with the final chest/inventory.

## Follow-up: hotbar shift-click priority and magnetic Keep slider (patches 0014–0015)

- Explicitly **Shift-clicking FROM the hotbar** in the vanilla player inventory screen now tries matching filtered **main** inventory slots before any unrelated main slot. It finishes existing identical home stacks first, then uses empty home slots, honoring group priorities. If the home stack is 1 and the hotbar has 64 identical items, the home becomes 64 and the remaining 1 stays in the hotbar *for that click*; it will not immediately spill into an unfiltered main slot. If all matching home slots are full, vanilla shift-click behavior is unchanged. This is intentionally different from a normal manual click placing items **INTO** the hotbar, which continues to be allowed. Both logical sides execute the same quick-move path for client prediction/server authority.
- Keep quantities still support **every integer 1–64** and the separate `All` setting. While dragging, 16/32/64 engage a 2-pixel magnetic zone and hold until the mouse is more than 4 pixels away (the widget's own GUI coordinate system). Mouse wheel still changes by exactly one. The saved value format is unchanged.
- Source review and standalone Java 21 slider mapping/detent tests passed. Full Gradle compilation with JDK 25, integration gameplay, and dedicated-server filter synchronization have **not** been verified in this environment. The target remains Minecraft **26.2**.

Quick in-game regressions: (1) home slot has 1 Ender Chest, hotbar has 64: Shift-click hotbar stack; home fills to 64 and 1 stays in hotbar; (2) home slot empty, hotbar has 64: all 64 move to home; (3) home slot already full: vanilla may shift extra items into unfiltered main slots; (4) ordinary click to put items into hotbar stays intact; (5) slider detents 16/32/64 hold briefly, while dragging still selects other values and wheel fine-adjusts; (6) verify identical results on the two sides of a dedicated-server connection.

## Inventory reconciliation after all player-menu clicks (patch 0016)

- Previously, the player-inventory refill hook only recognized a PICKUP that originated in a
  filtered main slot, followed by a PICKUP placement into an unfiltered main slot. Shift-clicks,
  swaps, quick crafting, or moving stacks that already sat in unfiltered slots could bypass it.
  A later unrelated manual click sometimes repaired the scattered stacks by accident.
- The player inventory menu now runs the existing, shared refill policy after **every completed
  menu click** (on both client and server). This includes all normally processed click types and
  early-return paths; an immediate second click is no longer required to reconcile filtered slots.
- It deliberately runs only for `InventoryMenu`: chest/container Push's synthetic click sequence
  runs on a different menu and retains its own pre-deposit consolidation. The refill still only
  pulls from **unfiltered main-inventory slots 9–35**. Hotbar 0–8, equipment, offhand, and other
  filtered slots are not sources; manually arranged hotbar items remain exempt. When all filtered
  slots for an item reach capacity, extra stacks in unfiltered slots are expected and permissible.
- Build remains **Minecraft 26.2 / JDK 25**. In this environment the changed mixin was compiled
  against isolated API stubs with JDK 21 and the patch was tested against the v4 source, but
  a real Gradle build and singleplayer/multiplayer gameplay tests have NOT been performed.

Regression focus: place 1 Ender Chest in a filtered main slot and 64 in an unfiltered main slot.
Try normal click, Shift-click, swap/number-key, right-click split, quick crafting/drag, and a
click outside the inventory. The filtered slot should promptly fill to 64 (leaving 1 outside)
whenever those actions finish. Then move a stack deliberately into the hotbar and verify it is
not automatically taken back. Test the Push preview/result and container deposit afterward.
