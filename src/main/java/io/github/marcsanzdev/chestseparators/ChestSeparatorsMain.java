package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.network.*;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Common entry point for the mod.
 * <p>
 * Initializes server-side and common components such as networking payload registries,
 * data components, and global server-side payload receivers during the bootstrap phase.
 */
public class ChestSeparatorsMain implements ModInitializer {

    @Override
    public void onInitialize() {
        GlobalChestConfig.loadConfig();

        /** Registers the custom Data Components for ItemStacks. */
        ChestSeparatorsComponents.initialize();

        /** Registers network payloads for bidirectional communication. */
        registerPayloads();

        /** Clears concurrency locks if a player abruptly disconnects from the server. */
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            NetworkHandlers.clearLocksForPlayer(handler.player.getUuid());
        });

        /** Registers global network receivers handled by the NetworkHandlers class. */
        registerReceivers();
    }

    /**
     * Centralized registry for all networking payloads.
     */
    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ShulkerUUIDPayload.ID, ShulkerUUIDPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistRequestPayload.ID, WhitelistRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorLockRequestPayload.ID, EditorLockRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EditorLockResponsePayload.ID, EditorLockResponsePayload.CODEC);
    }

    /**
     * Links incoming network packets to their respective handler methods in {@link NetworkHandlers}.
     */
    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(EditorLockRequestPayload.ID, NetworkHandlers::handleEditorLockRequest);
        ServerPlayNetworking.registerGlobalReceiver(WhitelistRequestPayload.ID, NetworkHandlers::handleWhitelistRequest);
        ServerPlayNetworking.registerGlobalReceiver(WhitelistPayload.ID, NetworkHandlers::handleWhitelistUpdate);
    }
}