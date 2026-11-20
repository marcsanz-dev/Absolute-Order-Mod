# Release instructions for the Absolute Order community fork

**Proposed release:** `2.1.0-fork.1` for **Minecraft 26.3 / Fabric**.

**Artifact filename:** `AbsoluteOrder-2.1.0-fork.1-fabric-mc26.3.jar`.

**Mod ID:** `chestseparators` (unchanged from upstream). Install on both server and clients. Never put both original and forked Absolute Order JARs in a `mods` folder.

**Source baseline:** working 26.2 fork after patches 0001–0016, preserved in `multiloader-26.2/`. First Fabric-only 26.3 port candidate in `multiloader-26.3/`. The upstream project remains credited and `LICENSE.txt` is unchanged.

Build from `multiloader-26.3` with JDK 25:

```bash
./gradlew :common:test :fabric:build --no-daemon
```

Check `fabric/build/libs/` for the **non-raw** Fabric 26.3 release JAR. Test on 26.3 client + dedicated server with a 54-slot Ender Chest mod before creating a Git tag or GitHub Release.

Recommended release tag after verification: `v2.1.0-fork.1-mc26.3`. Release title: `Absolute Order 2.1.0-fork.1 — Fabric 26.3`.

Recommended changelog:
- Protect player filters during automatic deposit and pull, with retained item quantities.
- Consolidate items into filtered main-inventory slots; preserve manually arranged hotbar items.
- Update manual/Shift-click routing to respect preferred filtered slots.
- Continuous retained-quantity slider with detents at stack-size marks.
- Reconcile deposit previews with simulated transfers.
- Synchronize Ender Chest filters, with size-aware compatibility for expanded inventories.

The 26.3 port is **not compiled or gameplay-verified by the assistant**. Source changes here are only the initial migration; expect to resolve any 26.3-specific Minecraft API or mixin changes surfaced by Gradle/in-game testing.
