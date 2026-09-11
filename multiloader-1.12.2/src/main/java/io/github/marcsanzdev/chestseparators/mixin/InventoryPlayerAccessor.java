package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link InventoryPlayer}'s {@code player} and {@code mainInventory} fields via {@code @Accessor}
 * mixins, replacing {@code @Shadow} declarations that the manual montage's annotation processor does not emit
 * to the refmap (and so fail to bind under SRG/obf in production).
 */
@Mixin(InventoryPlayer.class)
public interface InventoryPlayerAccessor {

    @Accessor("player")
    EntityPlayer chestseparators$getPlayer();

    @Accessor("mainInventory")
    NonNullList<ItemStack> chestseparators$getMainInventory();
}
