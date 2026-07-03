package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles incoming network payloads and complex server-side logic.
 * <p>
 * This class isolates networking interaction from the main entry point,
 * managing container concurrency locks, data synchronization, and item expulsion.
 */
public class NetworkHandlers {

    /**
     * Thread-safe map tracking containers currently being edited.
     * Maps {@link BlockPos} to the {@link UUID} of the player holding the lock.
     */
    private static final Map<BlockPos, UUID> LOCKED_CHESTS = new ConcurrentHashMap<>();

    public static void clearLocksForPlayer(UUID playerUuid) {
        LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(playerUuid));
    }

    /**
     * Manages editor access requests, ensuring atomic locking for double chests.
     */
    public static void handleEditorLockRequest(EditorLockRequestPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;

            UUID playerUuid = player.getUuid();
            BlockPos targetPos = payload.pos();
            World world = player.getEntityWorld();

            List<BlockPos> associatedPositions = getAssociatedPositions(world, targetPos);

            if (payload.isLocking()) {
                boolean canLock = true;

                // Check if any part of the container is already locked by another player
                for (BlockPos p : associatedPositions) {
                    if (LOCKED_CHESTS.containsKey(p) && !LOCKED_CHESTS.get(p).equals(playerUuid)) {
                        canLock = false;
                        break;
                    }
                }

                if (canLock) {
                    for (BlockPos p : associatedPositions) {
                        LOCKED_CHESTS.put(p, playerUuid);
                    }
                    sendLockResponse(player, targetPos, true);
                } else {
                    sendLockResponse(player, targetPos, false);
                }
            } else {
                for (BlockPos p : associatedPositions) {
                    if (LOCKED_CHESTS.containsKey(p) && LOCKED_CHESTS.get(p).equals(playerUuid)) {
                        LOCKED_CHESTS.remove(p);
                    }
                }
            }
        });
    }

    private static void sendLockResponse(ServerPlayerEntity player, BlockPos pos, boolean success) {
        if (ServerPlayNetworking.canSend(player, EditorLockResponsePayload.ID)) {
            ServerPlayNetworking.send(player, new EditorLockResponsePayload(pos, success));
        }
    }

    /**
     * Synchronizes the container's whitelist configuration with the client.
     */
    public static void handleWhitelistRequest(WhitelistRequestPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;

            Inventory targetInventory = getChestInventorySafe(player.getEntityWorld(), payload.pos());

            if (targetInventory instanceof IWhitelistProvider provider) {
                if (ServerPlayNetworking.canSend(player, WhitelistPayload.ID)) {
                    ServerPlayNetworking.send(player, new WhitelistPayload(payload.pos(), provider.getWhitelists()));
                }
            }
        });
    }

    /**
     * Processes whitelist updates and triggers item expulsion for non-compliant items.
     */
    public static void handleWhitelistUpdate(WhitelistPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (player == null) return;

            World world = player.getEntityWorld();
            Inventory targetInventory = getChestInventorySafe(world, payload.pos());

            if (targetInventory instanceof IWhitelistProvider provider) {
                provider.setWhitelists(payload.whitelists());
                targetInventory.markDirty();

                // Broadcast updates to all players currently tracking this block position
                if (world instanceof ServerWorld serverWorld) {
                    for (ServerPlayerEntity trackingPlayer : PlayerLookup.tracking(serverWorld, payload.pos())) {
                        if (trackingPlayer != player && ServerPlayNetworking.canSend(trackingPlayer, WhitelistPayload.ID)) {
                            ServerPlayNetworking.send(trackingPlayer, payload);
                        }
                    }
                }

                processItemExpulsion(player, world, targetInventory, payload);
            }
        });
    }

    private static void processItemExpulsion(ServerPlayerEntity player, World world, Inventory inventory, WhitelistPayload payload) {
        if (!GlobalChestConfig.instance.expelInvalidItems) return;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && payload.whitelists().containsKey(i)) {
                SlotWhitelist wl = payload.whitelists().get(i);

                // Only expel if all entry methods (Manual, Shift, Hopper) are strictly locked
                if (wl.allowManual() && wl.allowShift() && wl.allowHopper()) {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();

                    if (!wl.allowedItems().contains(itemId)) {
                        ItemStack extracted = inventory.removeStack(i);
                        launchItemAtPlayer(player, world, payload.pos(), extracted);
                    }
                }
            }
        }
    }

    private static void launchItemAtPlayer(ServerPlayerEntity player, World world, BlockPos pos, ItemStack stack) {
        double dropX = pos.getX() + 0.5D;
        double dropY = pos.getY() + 0.5D;
        double dropZ = pos.getZ() + 0.5D;

        ItemEntity itemEntity = new ItemEntity(world, dropX, dropY, dropZ, stack);

        double dirX = player.getX() - dropX;
        double dirY = player.getEyeY() - dropY;
        double dirZ = player.getZ() - dropZ;

        double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= distance;
        dirY /= distance;
        dirZ /= distance;

        Random random = new Random();
        double force = 0.3D;
        itemEntity.setVelocity(
                dirX * force + random.nextGaussian() * 0.05D,
                dirY * force + random.nextGaussian() * 0.05D + 0.1D,
                dirZ * force + random.nextGaussian() * 0.05D
        );

        itemEntity.setToDefaultPickupDelay();
        world.spawnEntity(itemEntity);
    }

    private static Inventory getChestInventorySafe(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction neighborDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                BlockEntity neighborBe = world.getBlockEntity(pos.offset(neighborDir));

                if (be instanceof Inventory && neighborBe instanceof Inventory) {
                    return (type == ChestType.RIGHT)
                            ? new DoubleInventory((Inventory) be, (Inventory) neighborBe)
                            : new DoubleInventory((Inventory) neighborBe, (Inventory) be);
                }
            }
        }
        return (be instanceof Inventory) ? (Inventory) be : null;
    }

    private static List<BlockPos> getAssociatedPositions(World world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        list.add(pos);

        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction neighborDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                list.add(pos.offset(neighborDir));
            }
        }
        return list;
    }
}