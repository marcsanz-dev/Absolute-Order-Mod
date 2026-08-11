package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link NetHandlerPlayServer}'s {@code player} field ({@link EntityPlayerMP}) via an {@code @Accessor}
 * mixin. This replaces a {@code @Shadow} of the same field: the manual (non-MixinGradle) montage's annotation
 * processor does not emit {@code @Shadow} mappings to the refmap, so in production (SRG/obf) a shadowed
 * {@code player} keeps its MCP name and fails to bind; {@code @Accessor} mappings, by contrast, are emitted.
 */
@Mixin(NetHandlerPlayServer.class)
public interface NetHandlerPlayServerAccessor {

    @Accessor("player")
    EntityPlayerMP chestseparators$getPlayer();
}
