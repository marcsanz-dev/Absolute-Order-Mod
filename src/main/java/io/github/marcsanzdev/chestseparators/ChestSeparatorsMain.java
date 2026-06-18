package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.*;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Common mod initializer. Registers networking payloads, data components,
 * and all server-side packet receivers.
 */
public class ChestSeparatorsMain implements ModInitializer {

    /** Thread-safe map of chests currently locked for editing, keyed by block position. */
    public static final Map<BlockPos, UUID> LOCKED_CHESTS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        GlobalChestConfig.loadConfig();
        ChestSeparatorsComponents.initialize();

        PayloadTypeRegistry.playS2C().register(ShulkerUUIDPayload.ID, ShulkerUUIDPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistRequestPayload.ID, WhitelistRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorLockRequestPayload.ID, EditorLockRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EditorLockResponsePayload.ID, EditorLockResponsePayload.CODEC);

        // Release all locks held by a player who disconnects abruptly.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(handler.player.getUuid()));
        });

        // Handles editor lock requests, with support for double chests (two associated positions).
        ServerPlayNetworking.registerGlobalReceiver(EditorLockRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    UUID playerUuid = context.player().getUuid();
                    BlockPos targetPos = payload.pos();
                    World world = context.player().getEntityWorld();

                    // Resolve all block positions occupied by this container (1 for single, 2 for double).
                    List<BlockPos> associatedPositions = getAssociatedPositions(world, targetPos);

                    if (payload.isLocking()) {
                        boolean canLock = true;

                        // Deny the lock if any part of the container is held by a different player.
                        for (BlockPos p : associatedPositions) {
                            if (LOCKED_CHESTS.containsKey(p)
                                    && !LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                canLock = false;
                                break;
                            }
                        }

                        if (canLock) {
                            // Acquire all positions atomically so no partial lock can slip through.
                            for (BlockPos p : associatedPositions) {
                                LOCKED_CHESTS.put(p, playerUuid);
                            }
                            if (ServerPlayNetworking.canSend(context.player(), EditorLockResponsePayload.ID)) {
                                ServerPlayNetworking.send(
                                        context.player(), new EditorLockResponsePayload(targetPos, true));
                            }
                        } else {
                            if (ServerPlayNetworking.canSend(context.player(), EditorLockResponsePayload.ID)) {
                                ServerPlayNetworking.send(
                                        context.player(), new EditorLockResponsePayload(targetPos, false));
                            }
                        }
                    } else {
                        // Release all associated positions when the player exits the editor.
                        for (BlockPos p : associatedPositions) {
                            if (LOCKED_CHESTS.containsKey(p)
                                    && LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                LOCKED_CHESTS.remove(p);
                            }
                        }
                    }
                }
            });
        });

        // Handles whitelist fetch requests sent by the client when a container is first opened.
        ServerPlayNetworking.registerGlobalReceiver(WhitelistRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    World world = context.player().getEntityWorld();
                    Inventory targetInventory = getChestInventorySafe(world, payload.pos());

                    if (targetInventory instanceof IWhitelistProvider provider) {
                        if (ServerPlayNetworking.canSend(context.player(), WhitelistPayload.ID)) {
                            ServerPlayNetworking.send(
                                    context.player(), new WhitelistPayload(payload.pos(), provider.getWhitelists()));
                        }
                    }
                }
            });
        });

        // Persists incoming whitelist updates from the client and broadcasts them to co-viewers.
        ServerPlayNetworking.registerGlobalReceiver(WhitelistPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    World world = context.player().getEntityWorld();
                    BlockState state = world.getBlockState(payload.pos());
                    boolean isEnderChest = state.getBlock() == Blocks.ENDER_CHEST;

                    // Ender Chest filters are local-only (hoppers cannot access them); nothing to do server-side.
                    if (isEnderChest) return;

                    Inventory targetInventory = getChestInventorySafe(world, payload.pos());

                    if (targetInventory instanceof IWhitelistProvider provider) {
                        provider.setWhitelists(payload.whitelists());
                        targetInventory.markDirty();

                        // Broadcast the updated whitelist to all other players currently viewing this container.
                        if (world instanceof ServerWorld serverWorld) {
                            for (ServerPlayerEntity trackingPlayer :
                                    PlayerLookup.tracking(serverWorld, payload.pos())) {
                                if (trackingPlayer != context.player()
                                        && ServerPlayNetworking.canSend(trackingPlayer, WhitelistPayload.ID)) {
                                    ServerPlayNetworking.send(trackingPlayer, payload);
                                }
                            }
                        }

                        // Eject items that violate the new whitelist when all three enforcement rules are enabled.
                        if (GlobalChestConfig.instance.expelInvalidItems) {
                            for (int i = 0; i < targetInventory.size(); i++) {
                                ItemStack stack = targetInventory.getStack(i);
                                if (!stack.isEmpty() && payload.whitelists().containsKey(i)) {
                                    SlotWhitelist wl = payload.whitelists().get(i);

                                    if (wl.allowManual() && wl.allowShift() && wl.allowHopper()) {
                                        String itemId = Registries.ITEM
                                                .getId(stack.getItem())
                                                .toString();

                                        if (!wl.allowedItems().contains(itemId)) {
                                            ItemStack extracted = targetInventory.removeStack(i);

                                            double dropX = payload.pos().getX() + 0.5D;
                                            double dropY = payload.pos().getY() + 0.5D;
                                            double dropZ = payload.pos().getZ() + 0.5D;

                                            ItemEntity itemEntity =
                                                    new ItemEntity(world, dropX, dropY, dropZ, extracted);

                                            double dirX = context.player().getX() - dropX;
                                            double dirY = context.player().getEyeY() - dropY;
                                            double dirZ = context.player().getZ() - dropZ;

                                            double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                                            dirX /= distance;
                                            dirY /= distance;
                                            dirZ /= distance;

                                            Random random = new Random();
                                            double force = 0.3D;
                                            itemEntity.setVelocity(
                                                    dirX * force + random.nextGaussian() * 0.05D,
                                                    dirY * force + random.nextGaussian() * 0.05D + 0.1D,
                                                    dirZ * force + random.nextGaussian() * 0.05D);

                                            itemEntity.setToDefaultPickupDelay();
                                            world.spawnEntity(itemEntity);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });
    }

    /**
     * Returns the direction toward the other half of a double chest, or null when the block is not
     * part of a double chest (single chest, shulker box, barrel, or any non-chest container).
     */
    private static Direction getDoubleChestNeighborDirection(BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.get(ChestBlock.FACING);
        return type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
    }

    /**
     * Returns the effective inventory for the given position, merging both halves for double chests.
     */
    private static Inventory getChestInventorySafe(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(state);
        if (neighborDir != null) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            BlockEntity neighborBe = world.getBlockEntity(pos.offset(neighborDir));

            if (be instanceof Inventory && neighborBe instanceof Inventory) {
                if (type == ChestType.RIGHT) {
                    return new DoubleInventory((Inventory) be, (Inventory) neighborBe);
                } else {
                    return new DoubleInventory((Inventory) neighborBe, (Inventory) be);
                }
            }
        }
        // Single chest, Shulker Box, or Barrel — return directly.
        if (be instanceof Inventory) {
            return (Inventory) be;
        }
        return null;
    }

    /**
     * Resolves all block positions occupied by a container (2 for double chests, 1 for all others).
     */
    private static List<BlockPos> getAssociatedPositions(World world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        list.add(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(world.getBlockState(pos));
        if (neighborDir != null) {
            list.add(pos.offset(neighborDir));
        }
        return list;
    }
}
