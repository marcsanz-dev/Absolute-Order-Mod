# Absolute Order fork — Minecraft 26.3 Fabric port candidate

This module was copied from the **user-tested Minecraft 26.2 fork** after patches 0001–0016. The 26.2 module is intentionally unchanged. The original public repository snapshot supplied by the user had no 26.3 source module. No claim is made that this has already compiled or been tested against Minecraft 26.3.

## Scope

- Minecraft **26.3**, Java **25**, **Fabric** client and dedicated server.
- Fork version **2.1.0-fork.1**, original mod ID **chestseparators**, archive basename **AbsoluteOrder**.
- Preserve upstream item filters, custom layouts and translation strings. Previous improvements: auto-deposit protected quantities, live Push preview, filter slot refill, hotbar shift-click prioritization, quantity slider and detents, Ender Chest filter synchronization and expanded container-size handling.
- 26.3 build requires Architectury 22 and updated Fabric API, Fabric Loader, Cloth Config, Mod Menu and REI compile APIs.
- **NeoForge 26.3 is not supported by this port candidate**. The existing 26.2 NeoForge module is unchanged.

## How to compile in WSL Debian (requires working internet for Gradle dependencies)

```bash
cd ~/dev/Absolute-Order-Mod/multiloader-26.3
java -version
./gradlew :common:test :fabric:build --no-daemon
```

Expected Fabric release artifact:

`fabric/build/libs/AbsoluteOrder-2.1.0-fork.1-fabric-mc26.3.jar`

Don't use a `raw`, `sources`, or `dev` JAR for production. The mod must be present on **both client and server** for all filter synchronization behavior. Remove the upstream Absolute Order JAR first: both have the same `chestseparators` mod ID. Configure matching 26.3 dependencies.

## Verification status

- Source copied from v5 fork that the user reported working in 26.2 multiplayer, including 54-slot Ender Chest compatibility.
- 26.3 dependency versions confirmed from Architectury documentation and 26.3 mod releases; code/API compatibility remains **unverified**.
- This environment only has Java 21 and cannot resolve Gradle/Maven artifacts. Full Java 25 compile, tests and gameplay must run on the user's WSL machine.
- Architectury API 22 includes breaking API changes relative to 21.x; fix the first actual Java compiler error before treating the port as buildable.

## Manual testing before deployment

1. Launch 26.3 client and separate dedicated Fabric test server with the same compiled fork JAR and matching dependency set.
2. Confirm main-inventory filtered slots refill after all inventory click modes and do not pull deliberately placed hotbar stacks; preserve direct hotbar shift-click routing.
3. Confirm Keep: All and Keep: 1/16/32/64/intermediate quantities and slider detents.
4. Compare Auto Deposit / Push hover preview with actual transfers; verify no item duplication or loss.
5. Verify native 27-slot and 54-slot Ender Chest filter routing and slots beyond 27, both immediate and after reconnect and server restart.
6. Test with two clients, including per-player Ender Chest inventory isolation.
7. Only after passing these, tag and publish your own release and deploy it to LXC 115 with a rollback backup.

Preserve the upstream MIT LICENSE.txt and original attribution. This is an independent community fork, not an upstream release.
