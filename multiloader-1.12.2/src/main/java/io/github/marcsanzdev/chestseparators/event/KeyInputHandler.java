package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.AutoDepositRequestPayload;
import io.github.marcsanzdev.chestseparators.network.ModNet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 1.12.2 (E1) port of the mod's client-tick key handling. The modern ports register a Architectury
 * {@code ClientTickEvent.CLIENT_POST} callback; here the same body runs from a Forge
 * {@link TickEvent.ClientTickEvent} at the END phase (one logical tick).
 *
 * <p>The hotkeys fire only with no screen open (chest closed). The same toggles are also wired into the
 * chest screen via {@code GenericContainerScreenMixin} so they work with the chest open too.
 */
public class KeyInputHandler {

    // Double-tap-sneak gesture state for the radius auto-deposit trigger.
    private static boolean wasSneakDown = false;
    private static long lastSneakTapTime = 0L;
    private static final long DOUBLE_TAP_WINDOW_MS = 350L;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft client = Minecraft.getMinecraft();
        if (client.player == null) return;

        // These fire only with no screen open (chest closed).
        if (client.currentScreen != null) return;

        while (ModKeyBindings.openEditorKey.isPressed()) actionBar(client, togglePreviewPanel());
        while (ModKeyBindings.toggleMagnifierKey.isPressed()) actionBar(client, toggleMagnifier());

        handleAutoDepositTriggers(client);
    }

    // Fires the radius auto-deposit from either the dedicated (optional) hotkey or a quick
    // double-tap of the sneak key. The server performs the move and replies with the animation data.
    private static void handleAutoDepositTriggers(Minecraft client) {
        boolean sneakDown = client.gameSettings.keyBindSneak.isKeyDown();

        if (!GlobalChestConfig.instance.autoDepositEnabled) {
            wasSneakDown = sneakDown;
            return;
        }

        while (ModKeyBindings.autoDepositKey.isPressed())
            triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);

        if (GlobalChestConfig.instance.autoDepositDoubleSneak && sneakDown && !wasSneakDown) {
            long now = System.currentTimeMillis();
            if (now - lastSneakTapTime <= DOUBLE_TAP_WINDOW_MS) {
                triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);
                lastSneakTapTime = 0L;
            } else {
                lastSneakTapTime = now;
            }
        }
        wasSneakDown = sneakDown;
    }

    private static void triggerAutoDeposit(Minecraft client, int action) {
        if (client.player == null) return;

        int radius = GlobalChestConfig.instance.autoDepositRadius;

        // Entity (chest minecart / boat) filters are client-side only, so gather the ones near the
        // player and forward them keyed by UUID; the server can't read these on its own.
        Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists = new HashMap<>();
        if (client.world != null) {
            AxisAlignedBB box = client.player.getEntityBoundingBox().grow(radius);
            for (Entity entity : client.world.getEntitiesInAABBexcluding(
                    client.player, box, e -> e instanceof IWhitelistProvider)) {
                Map<Integer, SlotWhitelist> wl =
                        ChestConfigManager.getInstance().readEntityWhitelists(entity.getUniqueID());
                if (!wl.isEmpty()) entityWhitelists.put(entity.getUniqueID(), wl);
            }
        }

        // chestseparators is a REQUIRED dependency, so every connected server has the receiver; the send
        // is unconditional (the E5 NetworkManager.canServerReceive guard has no 1.12.2 equivalent).
        ModNet.sendToServer(new AutoDepositRequestPayload(
                radius,
                GlobalChestConfig.instance.autoDepositThroughWalls,
                action,
                ChestConfigManager.getInstance().readEnderWhitelists(),
                entityWhitelists));
    }

    // Each toggle flips its config flag and returns the feedback text, leaving the caller to display
    // it: the action bar when no screen is open, or the editor's status overlay when a chest is open
    // (the action bar would otherwise be hidden behind the chest GUI).

    public static ITextComponent toggleMagnifier() {
        GlobalChestConfig.instance.magnifierEnabled = !GlobalChestConfig.instance.magnifierEnabled;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.magnifierEnabled,
                "message.chestseparators.magnifier_on",
                "message.chestseparators.magnifier_off");
    }

    public static ITextComponent toggleEditButtons() {
        GlobalChestConfig.instance.showEditButtons = !GlobalChestConfig.instance.showEditButtons;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showEditButtons,
                "message.chestseparators.edit_buttons_visible",
                "message.chestseparators.edit_buttons_hidden");
    }

    public static ITextComponent togglePreviewPanel() {
        GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showLeftPanel,
                "message.chestseparators.preview_panel_visible",
                "message.chestseparators.preview_panel_hidden");
    }

    public static ITextComponent toggleDepositButton() {
        GlobalChestConfig.instance.showDepositButton = !GlobalChestConfig.instance.showDepositButton;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showDepositButton,
                "message.chestseparators.deposit_button_visible",
                "message.chestseparators.deposit_button_hidden");
    }

    private static ITextComponent label(boolean on, String onKey, String offKey) {
        return new TextComponentTranslation(on ? onKey : offKey);
    }

    private static void actionBar(Minecraft client, ITextComponent message) {
        if (client.player != null) {
            message.getStyle().setColor(TextFormatting.GRAY);
            client.player.sendStatusMessage(message, true);
        }
    }

    public static boolean isModifierPressed() {
        // Whether the "show panel" modifier key (default Left Alt) is currently held. Reading the mapping's
        // live pressed state keeps this loader-agnostic (no raw LWJGL key poll needed).
        return ModKeyBindings.showPanelModifierKey.isKeyDown();
    }
}
