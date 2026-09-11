package io.github.marcsanzdev.chestseparators.neoforge;

import io.github.marcsanzdev.chestseparators.compat.ChestSeparatorsReiPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/**
 * REI on NeoForge discovers client plugins via the {@code @REIPluginClient} annotation (its forge/neoforge
 * variant), not via a data entrypoint like Fabric's {@code rei_client}. This thin subclass carries the
 * annotation and reuses the common exclusion-zone logic, so the editor overlay is not drawn over by REI on
 * NeoForge either. REI ships a native 26.1 build for both loaders, so this compiles as a compile-only API.
 */
@REIPluginClient
public class NeoForgeReiPlugin extends ChestSeparatorsReiPlugin {}
