package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

/**
 * Registers all client-side S2C packet handlers for the mod. Each handler runs on the main client thread —
 * {@link CsClientHandler} already schedules the receiver via {@code Minecraft#addScheduledTask}, so the
 * modern {@code context.queue(...)} wrapper is dropped and the body runs directly. On legacy Forge the
 * SimpleNetworkWrapper routes by {@link net.minecraft.util.ResourceLocation}: the payload is parsed from
 * the buffer with its static {@code read(...)}. chestseparators is a required dependency, so every client
 * has registered all S2C receivers (no per-send guard needed).
 */
public class ModClientNetworking {

    /** Sends the player's current inventory filters to the server (for the Pick Up rule enforcement). */
    public static void sendInventoryFilters() {
        ModNet.sendToServer(new InventoryFiltersPayload(
                new java.util.HashMap<>(ChestConfigManager.getInstance().getPlayerInventoryFilters())));
    }

    public static void init() {

        // Receives the Shulker Box UUID from the server when a Shulker is opened.
        // Stored immediately so it is available when the screen initializes.
        CsNetwork.registerClient(ShulkerUUIDPayload.ID, (buf, player) -> {
            ShulkerUUIDPayload payload = ShulkerUUIDPayload.read(buf);
            ChestPosStorage.lastOpenedShulkerUUID = payload.shulkerUUID();

            // Hot-update the editor if the screen is already open (rare but possible).
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
            if (editor != null && editor.session.isShulkerBox) {
                editor.session.currentShulkerUUID = payload.shulkerUUID();
                ChestConfigManager.getInstance().loadShulkerConfig(payload.shulkerUUID());
            }
        });

        // Receives the server's response to an editor lock request.
        // On success, transitions the editor to VIEW_GROUPS mode.
        // On failure, shows an error status message and plays a rejection sound.
        CsNetwork.registerClient(EditorLockResponsePayload.ID, (buf, player) -> {
            EditorLockResponsePayload payload = EditorLockResponsePayload.read(buf);
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
            if (editor != null
                    && editor.session.currentChestPos != null
                    && editor.session.currentChestPos.equals(payload.pos())) {
                if (payload.granted()) {
                    editor.hasEditorLock = true;
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                } else {
                    editor.showStatus(
                            new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.locked"), TextFormatting.RED);
                    editor.playClickSound(0.5f);
                }
            }
        });

        // Receives the result of a radius transfer and animates the items flying (to chests for a
        // deposit, or back to the player for a grab when reverse is set).
        CsNetwork.registerClient(AutoDepositResultPayload.ID, (buf, player) -> {
            AutoDepositResultPayload payload = AutoDepositResultPayload.read(buf);
            AutoDepositAnimator.addFlights(payload.flights(), payload.reverse());
        });

        // Server-authoritative whitelist for a BLOCK container (chest/barrel/…): applied to the client's
        // physical block entity AND the open dummy GUI inventory to kill the one-tick filter flicker.
        CsNetwork.registerClient(WhitelistS2CPayload.ID, (buf, player) -> {
            WhitelistS2CPayload payload = WhitelistS2CPayload.read(buf);
            if (player == null || player.world == null) return;

            // Detect Ender Chest by inspecting the open menu's inventory slots, not the block
            // position — the payload's pos points to the block the server used, which may differ
            // from the Ender Chest position in edge cases.
            boolean isEnderChest = false;
            if (player.openContainer != null) {
                for (Slot slot : player.openContainer.inventorySlots) {
                    if (slot.inventory instanceof InventoryEnderChest) {
                        isEnderChest = true;
                        break;
                    }
                }
            }
            if (isEnderChest) return;

            // 1. Store data for UI rendering. setCurrentWhitelists replaces the whole working map
            // with only the container's filters; re-mirror the player-inventory filters so the
            // preview panel can still show an inventory group's filter while a chest is open.
            ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
            ChestSeparatorsEditor editorForMirror = ChestSeparatorsEditor.getInstance();
            if (editorForMirror != null && !editorForMirror.session.isInventoryScreenContext) {
                ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
            }

            // 2. Inject into the client-side physical block entity.
            TileEntity be = player.world.getTileEntity(payload.pos());
            if (be instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) be;
                provider.setWhitelists(payload.whitelists());
            }

            // 3. THE FLICKER KILLER: inject into the client's dummy GUI inventory.
            if (player.openContainer instanceof ContainerChest) {
                ContainerChest genericHandler = (ContainerChest) player.openContainer;
                if (genericHandler.getLowerChestInventory() instanceof IWhitelistProvider) {
                    IWhitelistProvider provider = (IWhitelistProvider) genericHandler.getLowerChestInventory();
                    provider.setWhitelists(payload.whitelists());
                }
            }
        });

        // Server-authoritative whitelist for ENTITY containers (chest minecarts), pushed when the GUI opens
        // and after a save. Mirrors the block-chest handler above but keyed by entity UUID.
        CsNetwork.registerClient(EntityWhitelistS2CPayload.ID, (buf, player) -> {
            EntityWhitelistS2CPayload payload = EntityWhitelistS2CPayload.read(buf);
            if (player == null) return;

            // An empty payload means the server entity has no stored filter yet — the migration
            // case for minecarts filtered before this version (their filter lives only in the
            // local .dat, never synced): ignore it so the local filter is preserved and displayed,
            // and it will sync up to the server on the next save.
            if (payload.whitelists().isEmpty()) return;

            // Inject into the client's open container inventory (the minecart) to keep client-side
            // prediction consistent and avoid a flicker.
            if (player.openContainer instanceof ContainerChest) {
                ContainerChest genericHandler = (ContainerChest) player.openContainer;
                if (genericHandler.getLowerChestInventory() instanceof IWhitelistProvider) {
                    IWhitelistProvider provider = (IWhitelistProvider) genericHandler.getLowerChestInventory();
                    provider.setWhitelists(payload.whitelists());
                }
            }

            // If the editor is open on exactly this minecart, make its displayed filters match the
            // server, then re-mirror the player inventory (offset keys) so they are not dropped.
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
            if (editor != null
                    && editor.session.isMinecartChest
                    && payload.entityUuid().equals(editor.session.currentEntityUUID)) {
                ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
            }
        });
    }
}
