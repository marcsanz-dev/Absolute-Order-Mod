# Mechanical yarn(1.21.11 snapshot) -> Mojmap rename script for the client/editor cluster.
# Reproducible: apply to the OLD src client/ui files copied into common. Fabric-API refactors
# (networking, keybind poll, screen-mouse events, world-render) are done BY HAND afterwards.
# Usage: sed -i -f multiloader/tools/client-mechanical-rename.sed <copied .java files>

# ---- package-qualified imports (specific first) ----
s#net\.minecraft\.client\.gui\.DrawContext#net.minecraft.client.gui.GuiGraphics#g
s#net\.minecraft\.client\.gui\.screen\.ingame\.HandledScreen#net.minecraft.client.gui.screens.inventory.AbstractContainerScreen#g
s#net\.minecraft\.client\.gui\.screen\.ingame\.ShulkerBoxScreen#net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen#g
s#net\.minecraft\.client\.gui\.screen\.ingame\.InventoryScreen#net.minecraft.client.gui.screens.inventory.InventoryScreen#g
s#net\.minecraft\.client\.gui\.screen\.ingame\.CreativeInventoryScreen#net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen#g
s#net\.minecraft\.client\.gui\.screen\.Screen#net.minecraft.client.gui.screens.Screen#g
s#net\.minecraft\.client\.gui\.widget\.TextFieldWidget#net.minecraft.client.gui.components.EditBox#g
s#net\.minecraft\.client\.gui\.Click#net.minecraft.client.input.MouseButtonEvent#g
s#net\.minecraft\.client\.input\.KeyInput#net.minecraft.client.input.KeyEvent#g
s#net\.minecraft\.client\.input\.CharInput#net.minecraft.client.input.CharacterEvent#g
s#net\.minecraft\.client\.MinecraftClient#net.minecraft.client.Minecraft#g
s#net\.minecraft\.client\.gl\.RenderPipelines#net.minecraft.client.renderer.RenderPipelines#g
s#net\.minecraft\.client\.render\.item\.ItemRenderState#net.minecraft.client.renderer.item.ItemStackRenderState#g
s#net\.minecraft\.client\.item\.ItemModelManager#net.minecraft.client.renderer.item.ItemModelResolver#g
s#net\.minecraft\.client\.render\.command\.OrderedRenderCommandQueue#net.minecraft.client.renderer.OrderedSubmitNodeCollector#g
s#net\.minecraft\.client\.render\.OverlayTexture#net.minecraft.client.renderer.texture.OverlayTexture#g
s#net\.minecraft\.client\.render\.Camera#net.minecraft.client.Camera#g
s#net\.minecraft\.client\.util\.math\.MatrixStack#com.mojang.blaze3d.vertex.PoseStack#g
s#net\.minecraft\.client\.util\.InputUtil#com.mojang.blaze3d.platform.InputConstants#g
s#net\.minecraft\.client\.option\.KeyBinding#net.minecraft.client.KeyMapping#g
s#net\.minecraft\.client\.world\.ClientWorld#net.minecraft.client.multiplayer.ClientLevel#g
s#net\.minecraft\.screen\.slot\.Slot#net.minecraft.world.inventory.Slot#g
s#net\.minecraft\.screen\.GenericContainerScreenHandler#net.minecraft.world.inventory.ChestMenu#g
s#net\.minecraft\.screen\.ScreenHandler#net.minecraft.world.inventory.AbstractContainerMenu#g
s#net\.minecraft\.text\.Text#net.minecraft.network.chat.Component#g
s#net\.minecraft\.util\.Formatting#net.minecraft.ChatFormatting#g
s#net\.minecraft\.util\.Identifier#net.minecraft.resources.Identifier#g
s#net\.minecraft\.util\.math\.MathHelper#net.minecraft.util.Mth#g
s#net\.minecraft\.util\.math\.BlockPos#net.minecraft.core.BlockPos#g
s#net\.minecraft\.util\.math\.Vec3d#net.minecraft.world.phys.Vec3#g
s#net\.minecraft\.util\.math\.Direction#net.minecraft.core.Direction#g
s#net\.minecraft\.util\.math\.RotationAxis#com.mojang.math.Axis#g
s#net\.minecraft\.util\.HeldItemContext#net.minecraft.world.item.HeldItemContext#g
s#net\.minecraft\.registry\.Registries#net.minecraft.core.registries.BuiltInRegistries#g
s#net\.minecraft\.item\.ItemDisplayContext#net.minecraft.world.item.ItemDisplayContext#g
s#net\.minecraft\.item\.ItemGroup#net.minecraft.world.item.CreativeModeTab#g
s#net\.minecraft\.item\.ItemStack#net.minecraft.world.item.ItemStack#g
s#net\.minecraft\.item\.Item#net.minecraft.world.item.Item#g
s#net\.minecraft\.particle\.ParticleTypes#net.minecraft.core.particles.ParticleTypes#g
s#net\.minecraft\.sound\.SoundEvents#net.minecraft.sounds.SoundEvents#g
s#net\.minecraft\.sound\.SoundEvent#net.minecraft.sounds.SoundEvent#g
s#net\.minecraft\.sound\.SoundCategory#net.minecraft.sounds.SoundSource#g
s#net\.minecraft\.block\.entity\.#net.minecraft.world.level.block.entity.#g
s#net\.minecraft\.block\.enums\.ChestType#net.minecraft.world.level.block.state.properties.ChestType#g
s#net\.minecraft\.block\.BlockState#net.minecraft.world.level.block.state.BlockState#g
s#net\.minecraft\.block\.#net.minecraft.world.level.block.#g
s#net\.minecraft\.world\.World#net.minecraft.world.level.Level#g
# ---- bare type identifiers (word-boundary) ----
s#\bDrawContext\b#GuiGraphics#g
s#\bMinecraftClient\b#Minecraft#g
s#\bMathHelper\b#Mth#g
s#\bFormatting\b#ChatFormatting#g
s#\bHandledScreen\b#AbstractContainerScreen#g
s#\bScreenHandler\b#AbstractContainerMenu#g
s#\bGenericContainerScreenHandler\b#ChestMenu#g
s#\bMatrixStack\b#PoseStack#g
s#\bClientWorld\b#ClientLevel#g
s#\bRotationAxis\b#Axis#g
s#\bVec3d\b#Vec3#g
s#\bItemGroup\b#CreativeModeTab#g
s#\bCreativeInventoryScreen\b#CreativeModeInventoryScreen#g
s#\bKeyInput\b#KeyEvent#g
s#\bCharInput\b#CharacterEvent#g
# ---- Text -> Component (type + factories) ----
s#\bText\.translatable#Component.translatable#g
s#\bText\.literal#Component.literal#g
s#\bText\.empty#Component.empty#g
s#\bText\.of\b#Component.literal#g
s#\bList<Text>#List<Component>#g
s#<Text>#<Component>#g
s#\bText\[\]#Component[]#g
s#\bText #Component #g
s#(\bText)#(Component)#g
# ---- render/text method renames ----
s#\.getMatrices()#.pose()#g
s#\.drawTexture(#.blit(#g
s#\.drawText(#.drawString(#g
s#\.drawStrokedRectangle(#.renderOutline(#g
s#\.textRenderer\b#.font#g
s#\.currentScreenHandler\b#.containerMenu#g

s#net\.minecraft\.registry\.tag\.#net.minecraft.tags.#g
s#net\.minecraft\.text\.MutableText#net.minecraft.network.chat.MutableComponent#g
s#\bTextFieldWidget\b#EditBox#g
s#\bItemRenderState\b#ItemStackRenderState#g
s#\bItemModelManager\b#ItemModelResolver#g
s#\bOrderedRenderCommandQueue\b#OrderedSubmitNodeCollector#g
s#\bMutableText\b#MutableComponent#g
s#\bSoundCategory\b#SoundSource#g
s#\bAnimationStage\b#AnimationStatus#g
s#\.currentScreen\b#.screen#g
s#\.sendMessage(#.displayClientMessage(#g
s#\.formatted(#.withStyle(#g
s#SoundEvents\.ENTITY_ITEM_PICKUP#SoundEvents.ITEM_PICKUP#g
s#SoundEvents\.BLOCK_SHULKER_BOX_OPEN#SoundEvents.SHULKER_BOX_OPEN#g
s#SoundEvents\.BLOCK_SHULKER_BOX_CLOSE#SoundEvents.SHULKER_BOX_CLOSE#g
s#SoundEvents\.BLOCK_ENDER_CHEST_OPEN#SoundEvents.ENDER_CHEST_OPEN#g
s#SoundEvents\.BLOCK_ENDER_CHEST_CLOSE#SoundEvents.ENDER_CHEST_CLOSE#g
s#SoundEvents\.BLOCK_CHEST_OPEN#SoundEvents.CHEST_OPEN#g
s#SoundEvents\.BLOCK_CHEST_CLOSE#SoundEvents.CHEST_CLOSE#g

# ---- hub/EditBox/registry general rules ----
s#import net\.minecraft\.item\.\*;#import net.minecraft.world.item.*;#g
s#BuiltInRegistries\.ITEM_GROUP#BuiltInRegistries.CREATIVE_MODE_TAB#g
s#CREATIVE_MODE_TAB\.getId(#CREATIVE_MODE_TAB.getKey(#g
s#\.setDrawsBackground(#.setBordered(#g
s#\.setChangedListener(#.setResponder(#g
s#\.getIndex()#.getContainerSlot()#g
s#Block\.getBlockFromItem(#Block.byItem(#g
s#client\.world\b#client.level#g
s#getInstance()\.world\b#getInstance().level#g
s#\.getText()#.getValue()#g
