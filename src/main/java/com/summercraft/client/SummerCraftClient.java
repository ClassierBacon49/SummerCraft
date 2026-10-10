package com.summercraft.client;

import com.summercraft.ModRegistry;
import com.summercraft.block.CarLiftBlock;
import com.summercraft.SummerCraft;
import com.summercraft.car.CarEntity;
import com.summercraft.car.CarPhysics;
import com.summercraft.dev.Showcase;
import com.summercraft.network.HornPayload;
import com.summercraft.network.IgnitionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = SummerCraft.MODID, dist = Dist.CLIENT)
public class SummerCraftClient {
    private static final String CATEGORY = "key.categories.summercraft";
    public static final KeyMapping IGNITION = new KeyMapping("key.summercraft.ignition", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping CLUTCH = new KeyMapping("key.summercraft.clutch", GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping SHIFT_UP = new KeyMapping("key.summercraft.shift_up", GLFW.GLFW_KEY_UP, CATEGORY);
    public static final KeyMapping SHIFT_DOWN = new KeyMapping("key.summercraft.shift_down", GLFW.GLFW_KEY_DOWN, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.summercraft.horn", GLFW.GLFW_KEY_H, CATEGORY);

    private static boolean ignitionHeld, hornHeld;

    public SummerCraftClient(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerEntityRenderer(ModRegistry.CAR.get(), CarRenderer::new);
                    event.registerEntityRenderer(ModRegistry.ENGINE_STAND.get(), CarRenderer::new);
                });
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(IGNITION);
            event.register(CLUTCH);
            event.register(SHIFT_UP);
            event.register(SHIFT_DOWN);
            event.register(HORN);
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "car_hud"), CarHud::render));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());

        CarEntity.inputSource = player -> player instanceof LocalPlayer local
                ? new CarPhysics.Input(Math.max(0, local.input.forwardImpulse), Math.max(0, -local.input.forwardImpulse),
                        local.input.leftImpulse, local.input.jumping, clutchDown())
                : CarPhysics.Input.NONE;
        CarEntity.clientTick = CarSounds::tick;
        CarLiftBlock.openSetup = (car, pos) -> Minecraft.getInstance().setScreen(new SetupScreen(car, pos));
        if (Showcase.enabled()) ShowcaseCamera.register();
    }

    /**
     * Reads the clutch key straight off the keyboard. Its default (Left Control) is also vanilla's sprint key,
     * and a shared key only reports as held for one of the two bindings.
     */
    private static boolean clutchDown() {
        InputConstants.Key key = CLUTCH.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM) return CLUTCH.isDown();
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        CarEntity car = mc.player != null && mc.player.getVehicle() instanceof CarEntity c && c.getDriver() == mc.player ? c : null;

        // Driving controls do nothing from the passenger seat; say so instead of failing silently.
        if (car == null && mc.player != null && mc.player.getVehicle() instanceof CarEntity
                && (IGNITION.consumeClick() | SHIFT_UP.consumeClick() | SHIFT_DOWN.consumeClick())) {
            mc.player.displayClientMessage(Component.translatable("message.summercraft.passenger_seat"), true);
        }

        boolean ignition = car != null && IGNITION.isDown();
        if (ignition != ignitionHeld) {
            ignitionHeld = ignition;
            PacketDistributor.sendToServer(new IgnitionPayload(ignition));
        }
        boolean horn = car != null && HORN.isDown();
        if (horn != hornHeld) {
            hornHeld = horn;
            PacketDistributor.sendToServer(new HornPayload(horn));
        }

        while (SHIFT_UP.consumeClick()) if (car != null) shift(car, 1);
        while (SHIFT_DOWN.consumeClick()) if (car != null) shift(car, -1);
    }

    private static void shift(CarEntity car, int direction) {
        int target = car.physics.gear + direction;
        if (target < CarPhysics.REVERSE || target > CarPhysics.TOP_GEAR) return;
        boolean shifted = car.physics.shift(target);
        if (!shifted && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.summercraft.need_clutch", CLUTCH.getTranslatedKeyMessage()), true);
        }
        car.level().playLocalSound(car.getX(), car.getY(), car.getZ(),
                shifted ? ModRegistry.GEAR_SHIFT.get() : ModRegistry.GEAR_GRIND.get(),
                car.getSoundSource(), shifted ? 0.6f : 0.9f, 1f, false);
    }
}
