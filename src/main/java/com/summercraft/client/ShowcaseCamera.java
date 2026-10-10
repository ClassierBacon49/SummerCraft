package com.summercraft.client;

import com.summercraft.car.CarEntity;
import com.summercraft.dev.Showcase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link Showcase}: screenshots each camera position and the setup screen, then quits the game. */
public final class ShowcaseCamera {
    private static int ticks;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ticks++;
        if (ticks == 40) mc.options.hideGui = true;
        for (int i = 0; i < Showcase.VIEWS.length; i++) {
            if (ticks == Showcase.VIEWS[i].tick() + 15) mc.getEntityRenderDispatcher().setRenderHitBoxes(Showcase.VIEWS[i].hitboxes());
            if (ticks == Showcase.VIEWS[i].tick() + 25) shot(mc, i);
        }
        if (ticks == Showcase.SCREEN_OPEN) {
            mc.options.hideGui = false;
            CarEntity car = mc.level.getEntitiesOfClass(CarEntity.class, new AABB(mc.player.blockPosition()).inflate(12)).stream()
                    .filter(c -> !c.isStand()).findFirst().orElse(null);
            if (car != null) mc.setScreen(new SetupScreen(car, BlockPos.containing(car.position()).below(2)));
        }
        if (ticks == Showcase.SCREEN_SHOT) shot(mc, Showcase.VIEWS.length);
        if (ticks == Showcase.SCREEN_SHOT + 20) mc.stop();
    }

    private static void shot(Minecraft mc, int index) {
        Screenshot.grab(mc.gameDirectory, "showcase_" + index + ".png", mc.getMainRenderTarget(), message -> {});
    }

    private ShowcaseCamera() {}
}
