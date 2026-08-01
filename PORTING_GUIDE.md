# Porting Guide — ChestSeparators / *Absolute Order* (multiloader)

The playbook we follow **for every single version port**. The goal is one shared codebase
(Architectury) that builds a jar per loader, with the in‑GUI editor overlay looking and behaving
**identically** to the current Fabric build. Nothing here is optional: a port is "done" only when the
whole checklist is signed off.

Companion documents:
- **`MOD_SPEC.md`** — the source of truth for *what the mod must do and look like* (feature + visual
  parity checklist). Every port is validated against it.
- **`CLAUDE.md` → "Multiloader port"** — the ordered roadmap (which version/loader is next).

---

## 0. Ground rules (decided, do not re‑litigate)

- **Framework:** Architectury API. Structure: `common/` (all logic + mixins, compiled once per render
  era) + `fabric/`, `neoforge/`, `forge/` (thin loader modules). Quilt is **not** a separate module —
  we certify the Fabric jar on Quilt and declare compatibility.
- **Mappings:** Mojang official (Mojmap) + **Parchment** for parameter names, across every module and
  every version. No yarn. (`common` mixins are written once against Mojmap names.)
- **Integrations:** every port ships **complete** — config screen (cloth‑config + the loader's config
  entrypoint) and recipe‑viewer compat (REI / EMI / JEI) — never a stripped build.
- **Separators are client‑local; block‑container filters are server‑authoritative.** That boundary is
  sacred on every loader — see `MOD_SPEC.md`.
- **Authorship:** commits are 100% `marcsanz-dev`, no AI attribution (see `CLAUDE.md → Git`).

## Render eras (why "write once" only holds *within* an era)

Minecraft's GUI drawing API changed across eras. The overlay is re‑implemented **once per era**; within
an era it is copy‑exact. Group ports by era and reuse the era's overlay implementation.

| Era | Versions (this project) | Drawing API surface |
|-----|-------------------------|---------------------|
| **E5 – Pipelines** | 26.x, 1.21.late | `RenderPipelines` + `DrawContext` (current base) |
| **E4 – GuiGraphics** | 1.20.1 | `DrawContext`/`GuiGraphics`, `RenderSystem` |
| **E3 – DrawContext+MatrixStack** | 1.19.2 | `DrawContext` early + `MatrixStack` |
| **E2 – MatrixStack** | 1.18.2, 1.16.5 | `MatrixStack`, `DrawableHelper` |
| **E1 – Immediate GL** *(mythic, separate track)* | 1.12.2, 1.9.x | GL11 immediate mode, no shared code |

> The current code targets **E5**. Forward ports (26.x) stay in E5. Backports drop an era at a time.

## Loader × version reality (not a full matrix)

| Version | Fabric | NeoForge | Forge | Notes |
|---------|:---:|:---:|:---:|-------|
| 26.x (26.1 → 26.2) | ✅ | ✅ | — | Forge is legacy on 26.x; Quilt via Fabric |
| 1.21.x *(reference)* | ✅ | ✅ | — | Quilt via Fabric |
| 1.20.1 | ✅ | ✅ | ✅ | NeoForge forked from Forge here |
| 1.19.2 / 1.18.2 / 1.16.5 | ✅ | — | ✅ | NeoForge did not exist pre‑1.20.1 |
| 1.12.2 / 1.9.x | — | — | ✅ (legacy) | Mythic track, hand‑written |

---

## Per‑version checklist (run top to bottom, tick every box)

### 1. Pre‑flight research
- [ ] Confirm the version's **render era** (table above) → which overlay implementation to reuse/adapt.
- [ ] Confirm availability for that loader+version of: Mojmap + **Parchment** build, **Fabric API /
      NeoForge / Forge**, **cloth‑config**, config entrypoint (**ModMenu** on Fabric; NeoForge/Forge
      config screen registration), **REI / EMI / JEI**.
- [ ] Note any missing dependency and its substitute *before* starting (never discover mid‑port).

### 2. Module setup
- [ ] Add the version to the Architectury build (common + the loaders from the matrix).
- [ ] Wire Mojmap + Parchment for this version; project compiles the empty modules.

### 3. Core logic (non‑render)
- [ ] Port config data, persistence paths, undo/redo, filter model, priority ordering, presets.
- [ ] Networking via **Architectury Networking** (lock request/response, whitelist sync, push/pull,
      sort‑open‑filters). Verify the server‑authoritative path on a dedicated server.

### 4. GUI / overlay (era‑specific)
- [ ] Reuse the era's overlay implementation; adapt only the drawing calls that changed.
- [ ] Validate against `MOD_SPEC.md` visuals (Cristal glass theme, blobs, previews, badges, animations).

### 5. Mixins — full audit (the crash‑prevention step)
- [ ] Every `@Mixin` is **single‑target** (no multi‑target accessor collapse).
- [ ] Every `@Accessor`/`@Invoker`/`@Inject` target exists on the mapped class **in this version** —
      verify against the remapped artifact, not just dev. (Reuse the audit method from `CLAUDE.md`.)
- [ ] `@Inject` injection points resolve (`require = 1` will hard‑fail otherwise).

### 6. Access & config
- [ ] Fabric access wideners ⇄ Forge/NeoForge **access transformers** kept in sync (same members).
- [ ] Config screen registered per loader; **all settings present and functional**.
- [ ] REI / EMI / JEI integration wired and shown.

### 7. Build
- [ ] `build` produces a remapped jar **per loader** with no warnings.
- [ ] Inspect the production jar (strings/refmap) — mixin names remapped to real intermediary/SRG.

### 8. Test matrix (dev client + dedicated server)
- [ ] Singleplayer: editor overlay, separators, filters, presets, palette, previews, sounds.
- [ ] Multiplayer (2 clients + server): **chest lock** grant/deny/release (single + double chest),
      server‑authoritative filter enforcement (manual / shift / hopper), separators stay client‑local.
- [ ] Run through `MOD_SPEC.md` end to end — **every feature and visual accounted for**.

### 9. Sign‑off
- [ ] Checklist complete, `MOD_SPEC.md` parity confirmed, jars built for all loaders in the matrix.
- [ ] Update `CLAUDE.md → Multiloader port` (mark version done, name the next one).
- [ ] Commit (conventional, `marcsanz-dev`).

---

## Migration one‑offs (done once, not per version)

These belong to **Phase 1** (the 1.21.x reference port) and are not repeated afterwards:
- Adopt the Architectury project layout (common + loader modules).
- Migrate the whole codebase from yarn to **Mojmap + Parchment** names.
- Convert Fabric networking / events to Architectury equivalents.
- Author the E5 overlay implementation as the reusable template.
