package com.summercraft.client;

import com.summercraft.ModRegistry;
import com.summercraft.car.CarEntity;
import com.summercraft.car.CarPhysics;
import com.summercraft.car.PartSlot;
import com.summercraft.item.PartItem;
import com.summercraft.item.WrenchItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The dashboard while driving, and part, bolt and tuning readouts while working on a car. */
public final class CarHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55DD55;
    private static final int YELLOW = 0xFFEEDD44;
    private static final int AMBER = 0xFFFF9922;
    private static final int RED = 0xFFEE4444;
    private static final int DIAL_FACE = 0xE0101418;
    private static final int DIAL_RIM = 0xFF5A6068;
    private static final int NEEDLE = 0xFFFF5522;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.player.getVehicle() instanceof CarEntity car) {
            if (car.getDriver() == mc.player) drawDashboard(g, mc.font, car);
        } else if (CarRenderer.targetedCar(mc) instanceof CarEntity car) {
            drawPartInfo(g, mc, car);
        }
    }

    // ---------------------------------------------------------------- dashboard

    private static void drawDashboard(GuiGraphics g, Font font, CarEntity car) {
        int radius = 34;
        int baseY = g.guiHeight() - radius - 14;
        int tachX = 12 + radius, speedX = tachX + radius * 2 + 34;

        float rpm = car.getRpm();
        float redline = car.engineType() != null ? (float) car.engineType().redline : 6500f;
        dial(g, font, tachX, baseY, radius, rpm / 8000f, 8, redline / 8000f, "RPM x1000", i -> String.valueOf(i));
        float kmh = (float) Math.abs(car.physics.vz) * 3.6f;
        dial(g, font, speedX, baseY, radius, kmh / 160f, 8, 2f, "km/h", i -> String.valueOf(i * 20));

        // Gear indicator between the dials
        int gear = car.getGear();
        String gearText = gear == CarPhysics.REVERSE ? "R" : gear == CarPhysics.NEUTRAL ? "N" : String.valueOf(gear);
        int midX = (tachX + speedX) / 2;
        g.fill(midX - 11, baseY - 14, midX + 11, baseY + 10, DIAL_FACE);
        g.renderOutline(midX - 11, baseY - 14, 22, 24, DIAL_RIM);
        g.pose().pushPose();
        g.pose().translate(midX, baseY - 9, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, gearText, 0, 0, gear == CarPhysics.REVERSE ? AMBER : WHITE);
        g.pose().popPose();
        small(g, font, String.valueOf(Math.round(kmh)), midX, baseY + 14, GRAY, true);

        // Fuel and temperature bars
        int barX = speedX + radius + 10, barW = 70;
        bar(g, font, barX, baseY - 28, barW, "FUEL", car.getFuel() / CarEntity.TANK_SIZE, car.getFuel() < 10 ? AMBER : 0xFFDDAA33);
        float temp = car.getTemperature();
        bar(g, font, barX, baseY - 14, barW, "TEMP", Mth.clamp((temp - 20) / 120, 0, 1), temp > 115 ? RED : temp > 100 ? YELLOW : 0xFF4FA8E8);
        bar(g, font, barX, baseY, barW, "OIL", car.getOil() / CarEntity.TANK_SIZE, car.getOil() < 10 ? RED : 0xFF8B6B2E);
        if (car.has(com.summercraft.car.PartSlot.TURBO) || car.has(com.summercraft.car.PartSlot.SUPERCHARGER)) {
            bar(g, font, barX, baseY - 42, barW, "BOOST", car.getBoost() / 1.2f, 0xFF66DDFF);
        }

        // Warning lights
        boolean keyOn = car.isRunning() || car.isCranking();
        int lightsY = baseY + 14;
        int x = barX;
        x = lamp(g, font, x, lightsY, "OIL", RED, keyOn && (car.getOil() < 5 || car.isCranking()));
        x = lamp(g, font, x, lightsY, "TEMP", RED, temp > 115);
        x = lamp(g, font, x, lightsY, "FUEL", AMBER, car.getFuel() < 10);
        x = lamp(g, font, x, lightsY, "CHK", AMBER, keyOn && car.enginePower() < 0.75);
        lamp(g, font, x, lightsY, "(P)", RED, Minecraft.getInstance().player.input.jumping);
        if (car.dynoActive) drawDyno(g, font, car);

        small(g, font, Component.translatable("hud.summercraft.controls",
                SummerCraftClient.IGNITION.getTranslatedKeyMessage(), SummerCraftClient.CLUTCH.getTranslatedKeyMessage(),
                SummerCraftClient.SHIFT_UP.getTranslatedKeyMessage(), SummerCraftClient.SHIFT_DOWN.getTranslatedKeyMessage()).getString(),
                barX, baseY + 28, GRAY, false);
    }

    /** Torque (orange) and wheel horsepower (blue) against revs, with the peaks, while on the dyno. */
    private static void drawDyno(GuiGraphics g, Font font, CarEntity car) {
        int w = 180, h = 100;
        int x = g.guiWidth() - w - 10, y = 10;
        g.fill(x - 6, y - 6, x + w + 6, y + h + 34, 0xD0101418);
        g.renderOutline(x - 6, y - 6, w + 12, h + 40, DIAL_RIM);
        double[] torque = car.physics.dynoTorque, power = car.physics.dynoPower;
        double maxTorque = 50, maxHp = 50, peakTorque = 0, peakHp = 0;
        int peakTorqueRpm = 0, peakHpRpm = 0;
        for (int i = 0; i < torque.length; i++) {
            double hp = power[i] / 745.7;
            maxTorque = Math.max(maxTorque, torque[i]);
            maxHp = Math.max(maxHp, hp);
            if (torque[i] > peakTorque) { peakTorque = torque[i]; peakTorqueRpm = i * 100; }
            if (hp > peakHp) { peakHp = hp; peakHpRpm = i * 100; }
        }
        double scale = Math.max(maxTorque, maxHp) * 1.1;
        for (int i = 1; i <= 4; i++) g.fill(x, y + h * i / 4, x + w, y + h * i / 4 + 1, 0x30FFFFFF);
        for (int k = 1; k < 8; k++) g.fill(x + w * k / 8, y, x + w * k / 8 + 1, y + h, 0x20FFFFFF);
        for (int i = 0; i < torque.length && i < 80; i++) {
            int px = x + i * w / 80;
            if (torque[i] > 0) {
                int py = y + h - (int) (torque[i] / scale * h);
                g.fill(px, py, px + 2, py + 2, 0xFFFF9922);
            }
            if (power[i] > 0) {
                int py = y + h - (int) (power[i] / 745.7 / scale * h);
                g.fill(px, py, px + 2, py + 2, 0xFF55AAFF);
            }
        }
        for (int k = 0; k <= 8; k += 2) small(g, font, String.valueOf(k), x + w * k / 8, y + h + 3, GRAY, true);
        small(g, font, "RPM x1000", x + w - 20, y + h + 3, GRAY, true);
        g.drawString(font, "DYNO", x, y + h + 10, WHITE);
        g.drawString(font, String.format("%.0f hp @ %d", peakHp, peakHpRpm), x + 34, y + h + 10, 0xFF55AAFF);
        g.drawString(font, String.format("%.0f Nm @ %d", peakTorque, peakTorqueRpm), x + 34, y + h + 20, 0xFFFF9922);
        if (peakHp == 0) small(g, font, Component.translatable("hud.summercraft.dyno_hint").getString(), x + w / 2, y + h / 2, GRAY, true);
    }

    private interface Label {
        String at(int index);
    }

    /** A round gauge: ticks from bottom-left round to bottom-right, numbers, a red zone and a needle. */
    private static void dial(GuiGraphics g, Font font, int cx, int cy, int r, float value, int majors, float redFrom, String title, Label label) {
        disc(g, cx, cy, r + 2, DIAL_RIM);
        disc(g, cx, cy, r, DIAL_FACE);
        double start = Math.toRadians(225), sweep = Math.toRadians(270);
        int steps = majors * 4;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            double a = start - sweep * t;
            boolean major = i % 4 == 0;
            int color = t >= redFrom ? RED : WHITE;
            int inner = r - (major ? 7 : 4);
            for (int d = inner; d <= r - 2; d++) {
                int px = cx + (int) Math.round(Math.cos(a) * d), py = cy - (int) Math.round(Math.sin(a) * d);
                g.fill(px, py, px + 1, py + 1, color);
            }
            if (major) {
                int lx = cx + (int) Math.round(Math.cos(a) * (r - 13)), ly = cy - (int) Math.round(Math.sin(a) * (r - 13));
                small(g, font, label.at(i / 4), lx, ly - 2, color, true);
            }
        }
        small(g, font, title, cx, cy + r / 2 - 2, GRAY, true);

        double needle = start - sweep * Mth.clamp(value, 0, 1.03f);
        for (double d = -3; d <= r - 5; d += 0.5) {
            int px = cx + (int) Math.round(Math.cos(needle) * d), py = cy - (int) Math.round(Math.sin(needle) * d);
            g.fill(px, py, px + 2, py + 2, NEEDLE);
        }
        disc(g, cx, cy, 3, 0xFF2A2E33);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int width, String label, float fraction, int color) {
        small(g, font, label, x, y + 1, GRAY, false);
        int bx = x + 22;
        g.fill(bx - 1, y - 1, bx + width + 1, y + 7, DIAL_RIM);
        g.fill(bx, y, bx + width, y + 6, 0xFF1A1D21);
        g.fill(bx, y, bx + Math.round(width * Mth.clamp(fraction, 0, 1)), y + 6, color);
    }

    private static int lamp(GuiGraphics g, Font font, int x, int y, String text, int color, boolean lit) {
        int w = font.width(text) / 2 + 6;
        g.fill(x, y, x + w, y + 9, lit ? color : 0xFF2A2E33);
        small(g, font, text, x + w / 2, y + 2, lit ? 0xFF000000 : 0xFF555A60, true);
        return x + w + 3;
    }

    private static void small(GuiGraphics g, Font font, String text, int x, int y, int color, boolean centred) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        int dx = centred ? -font.width(text) / 2 : 0;
        g.drawString(font, text, dx, 0, color, false);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- working on the car

    private static void drawPartInfo(GuiGraphics g, Minecraft mc, CarEntity car) {
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 14;
        ItemStack held = mc.player.getMainHandItem();

        if (held.getItem() instanceof PartItem) {
            Component text = car.pickInstallTarget(mc.player, held.getItem())
                    .<Component>map(slot -> Component.translatable("hud.summercraft.install", slot.displayName()))
                    .orElseGet(() -> car.installProblem(held.getItem()));
            g.drawCenteredString(mc.font, text, cx, y, WHITE);
            return;
        }

        car.aim(mc.player).ifPresent(aim -> {
            PartSlot slot = aim.slot();
            CarEntity.InstalledPart part = car.getPart(slot);
            if (part == null) return;
            g.drawCenteredString(mc.font, slot.displayName(), cx, y, WHITE);

            int count = part.bolts.length;
            String size = slot.boltSize() + "mm";
            int left = cx - (count * 9 + 4 + mc.font.width(size)) / 2;
            for (int i = 0; i < count; i++) {
                int px = left + i * 9;
                int turns = part.bolts[i];
                int color = turns == CarEntity.BOLT_STEPS ? GREEN : turns == 0 ? RED : YELLOW;
                g.fill(px - 1, y + 10, px + 8, y + 19, i == aim.bolt() ? WHITE : 0xFF000000);
                g.fill(px, y + 11, px + 7, y + 18, 0xFF222222);
                int fill = Math.max(1, Math.round(7f * turns / CarEntity.BOLT_STEPS));
                g.fill(px, y + 18 - fill, px + 7, y + 18, color);
            }
            boolean wrench = held.getItem() instanceof WrenchItem;
            int sizeColor = wrench ? (WrenchItem.size(held) == slot.boltSize() ? GREEN : RED) : GRAY;
            g.drawString(mc.font, size, left + count * 9 + 4, y + 11, sizeColor);

            List<Component> lines = new ArrayList<>();
            if (slot.item().getDefaultInstance().isDamageableItem()) {
                lines.add(Component.translatable("hud.summercraft.condition", Math.round(part.condition)));
            }
            if (held.is(ModRegistry.SCREWDRIVER.get())) {
                if (slot == PartSlot.CARBURETOR) lines.add(Component.translatable("hud.summercraft.mixture", String.format("%.2f", part.setting)));
                if (slot == PartSlot.DISTRIBUTOR) lines.add(Component.translatable("hud.summercraft.timing", Math.round(part.setting)));
                if (slot == PartSlot.CARBURETOR || slot == PartSlot.DISTRIBUTOR) lines.add(Component.translatable("hud.summercraft.hint.screwdriver"));
            }
            if (wrench) {
                lines.add(Component.translatable("hud.summercraft.hint.wrench"));
            } else if (held.isEmpty() && part.isFullyLoose()) {
                lines.add(Component.translatable("hud.summercraft.hint.remove"));
            } else if (held.isEmpty() && slot.hinge() != null) {
                lines.add(Component.translatable(part.open ? "hud.summercraft.hint.close" : "hud.summercraft.hint.open"));
            } else if (!part.isSecure() && !held.is(ModRegistry.SCREWDRIVER.get())) {
                lines.add(Component.translatable("hud.summercraft.hint.need_wrench"));
            }
            int line = y + 23;
            for (Component text : lines) {
                g.drawCenteredString(mc.font, text, cx, line, GRAY);
                line += 10;
            }
        });
    }

    private CarHud() {}
}
