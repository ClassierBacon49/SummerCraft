package com.summercraft.client;

import com.summercraft.car.CarEntity;
import com.summercraft.car.CarSetup;
import com.summercraft.network.SetupPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** The lift's tuning screen: four tabs of settings, plus raise/lower and apply. */
public class SetupScreen extends Screen {
    private enum Tab { WHEELS, ALIGNMENT, SUSPENSION, POWERTRAIN }

    private static final int PANEL_W = 300, PANEL_H = 214;

    private final CarEntity car;
    private final BlockPos lift;
    private Tab tab = Tab.WHEELS;

    // The setup being edited; sent to the server on Apply.
    private CarSetup.TyreType tyres;
    private int widthFront, widthRear, offset, rideHeight;
    private float camberFront, camberRear, toeFront, toeRear, springFront, springRear, dampers;
    private CarSetup.ExhaustType exhaust;
    private CarSetup.Drivetrain drivetrain;

    private Button applyButton, liftButton;

    public SetupScreen(CarEntity car, BlockPos lift) {
        super(Component.translatable("setup.summercraft.title"));
        this.car = car;
        this.lift = lift;
        load(car.getSetup());
    }

    private void load(CarSetup s) {
        tyres = s.tyres();
        widthFront = s.widthFront();
        widthRear = s.widthRear();
        offset = s.offset();
        camberFront = s.camberFront();
        camberRear = s.camberRear();
        toeFront = s.toeFront();
        toeRear = s.toeRear();
        rideHeight = s.rideHeight();
        springFront = s.springFront();
        springRear = s.springRear();
        dampers = s.dampers();
        exhaust = s.exhaust();
        drivetrain = s.drivetrain();
    }

    private CarSetup draft() {
        return new CarSetup(tyres, widthFront, widthRear, offset, camberFront, camberRear, toeFront, toeRear,
                rideHeight, springFront, springRear, dampers, exhaust, drivetrain).validated();
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int x = left(), y = top();

        // Tabs
        int tabW = (PANEL_W - 20) / Tab.values().length;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("setup.summercraft.tab." + t.name().toLowerCase()), b -> {
                tab = t;
                rebuildWidgets();
            }).bounds(x + 10 + t.ordinal() * tabW, y + 22, tabW - 2, 18).build();
            button.active = t != tab;
            addRenderableWidget(button);
        }

        int cx = x + 20, cy = y + 50, w = PANEL_W - 40, h = 20, gap = 24;
        switch (tab) {
            case WHEELS -> {
                addRenderableWidget(CycleButton.<CarSetup.TyreType>builder(CarSetup.TyreType::displayName)
                        .withValues(CarSetup.TyreType.values()).withInitialValue(tyres)
                        .withTooltip(v -> Tooltip.create(Component.translatable("setup.summercraft.tyre." + v.name().toLowerCase() + ".desc")))
                        .create(cx, cy, w, h, Component.translatable("setup.summercraft.tyres"), (b, v) -> tyres = v));
                slider(cx, cy + gap, w, h, "width_front", CarSetup.MIN_WIDTH, CarSetup.MAX_WIDTH, 10, widthFront, v -> widthFront = (int) v, v -> (int) v + " mm");
                slider(cx, cy + gap * 2, w, h, "width_rear", CarSetup.MIN_WIDTH, CarSetup.MAX_WIDTH, 10, widthRear, v -> widthRear = (int) v, v -> (int) v + " mm");
                slider(cx, cy + gap * 3, w, h, "offset", CarSetup.MIN_OFFSET, CarSetup.MAX_OFFSET, 5, offset, v -> offset = (int) v, v -> signed((int) v) + " mm");
            }
            case ALIGNMENT -> {
                slider(cx, cy, w, h, "camber_front", CarSetup.MIN_CAMBER, CarSetup.MAX_CAMBER, 0.25, camberFront, v -> camberFront = (float) v, v -> String.format("%+.2f°", v));
                slider(cx, cy + gap, w, h, "camber_rear", CarSetup.MIN_CAMBER, CarSetup.MAX_CAMBER, 0.25, camberRear, v -> camberRear = (float) v, v -> String.format("%+.2f°", v));
                slider(cx, cy + gap * 2, w, h, "toe_front", CarSetup.MIN_TOE, CarSetup.MAX_TOE, 0.05, toeFront, v -> toeFront = (float) v, SetupScreen::toe);
                slider(cx, cy + gap * 3, w, h, "toe_rear", CarSetup.MIN_TOE, CarSetup.MAX_TOE, 0.05, toeRear, v -> toeRear = (float) v, SetupScreen::toe);
            }
            case SUSPENSION -> {
                slider(cx, cy, w, h, "ride_height", CarSetup.MIN_RIDE, CarSetup.MAX_RIDE, 1, rideHeight, v -> rideHeight = (int) v, v -> signed((int) v) + " cm");
                slider(cx, cy + gap, w, h, "spring_front", CarSetup.MIN_SPRING, CarSetup.MAX_SPRING, 0.05, springFront, v -> springFront = (float) v, SetupScreen::stiffness);
                slider(cx, cy + gap * 2, w, h, "spring_rear", CarSetup.MIN_SPRING, CarSetup.MAX_SPRING, 0.05, springRear, v -> springRear = (float) v, SetupScreen::stiffness);
                slider(cx, cy + gap * 3, w, h, "dampers", CarSetup.MIN_SPRING, CarSetup.MAX_SPRING, 0.05, dampers, v -> dampers = (float) v, SetupScreen::stiffness);
            }
            case POWERTRAIN -> {
                addRenderableWidget(CycleButton.<CarSetup.ExhaustType>builder(CarSetup.ExhaustType::displayName)
                        .withValues(CarSetup.ExhaustType.values()).withInitialValue(exhaust)
                        .withTooltip(v -> Tooltip.create(Component.translatable("setup.summercraft.exhaust." + v.name().toLowerCase() + ".desc")))
                        .create(cx, cy, w, h, Component.translatable("setup.summercraft.exhaust"), (b, v) -> exhaust = v));
                addRenderableWidget(CycleButton.<CarSetup.Drivetrain>builder(CarSetup.Drivetrain::displayName)
                        .withValues(CarSetup.Drivetrain.values()).withInitialValue(drivetrain)
                        .withTooltip(v -> Tooltip.create(Component.translatable("setup.summercraft.drivetrain." + v.name().toLowerCase() + ".desc")))
                        .create(cx, cy + gap, w, h, Component.translatable("setup.summercraft.drivetrain"), (b, v) -> drivetrain = v));
            }
        }

        int by = y + PANEL_H - 28;
        liftButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            boolean up = car.liftHeight() < CarEntity.LIFT_HEIGHT / 2;
            send(up ? SetupPayload.RAISE : SetupPayload.LOWER);
        }).bounds(x + 10, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("setup.summercraft.stock"), b -> {
            load(CarSetup.STOCK);
            rebuildWidgets();
        }).bounds(x + 105, by, 90, 20).build());
        applyButton = addRenderableWidget(Button.builder(Component.translatable("setup.summercraft.apply"), b -> send(SetupPayload.APPLY))
                .bounds(x + 200, by, 90, 20).build());
        updateButtons();
    }

    private void send(int action) {
        CompoundTag setup = action == SetupPayload.APPLY ? draft().save() : new CompoundTag();
        PacketDistributor.sendToServer(new SetupPayload(car.getId(), lift, action, setup));
    }

    private void updateButtons() {
        boolean up = car.liftHeight() >= CarEntity.LIFT_HEIGHT - 0.05f;
        boolean low = car.liftHeight() < CarEntity.LIFT_HEIGHT / 2;
        liftButton.setMessage(Component.translatable(low ? "setup.summercraft.raise" : "setup.summercraft.lower"));
        applyButton.active = up;
        applyButton.setTooltip(up ? null : Tooltip.create(Component.translatable("message.summercraft.raise_first")));
    }

    @Override
    public void tick() {
        super.tick();
        if (car.isRemoved() || car.distanceToSqr(minecraft.player) > 100) {
            onClose();
            return;
        }
        updateButtons();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        Component engine = car.engineType() != null ? car.engineType().displayName() : Component.translatable("setup.summercraft.no_engine");
        g.drawCenteredString(font, Component.translatable("setup.summercraft.heading", engine), x + PANEL_W / 2, y + 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("setup.summercraft.tab." + tab.name().toLowerCase() + ".desc"),
                x + PANEL_W / 2, y + 152, 0xA0A0A0);

        float lift = car.liftHeight();
        String status = lift >= CarEntity.LIFT_HEIGHT - 0.05f ? "up" : lift <= 0.01f ? "down" : "moving";
        g.drawCenteredString(font, Component.translatable("setup.summercraft.lift_" + status), x + PANEL_W / 2, y + 166,
                status.equals("up") ? 0x55DD55 : 0xEEDD44);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, 0xFF5A6068);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xF0181B20);
        g.fill(x, y + 44, x + PANEL_W, y + 45, 0xFF3A3F46);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void slider(int x, int y, int w, int h, String key, double min, double max, double step, double value,
                        DoubleConsumer setter, DoubleFunction<String> format) {
        addRenderableWidget(new Slider(x, y, w, h, Component.translatable("setup.summercraft." + key), min, max, step, value, setter, format));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String toe(double value) {
        String direction = value > 0.001 ? " in" : value < -0.001 ? " out" : "";
        return String.format("%.2f°", Math.abs(value)) + direction;
    }

    private static String stiffness(double value) {
        return String.format("%.0f%%", value * 100);
    }

    /** A slider over a real range, snapped to a step. */
    private static final class Slider extends AbstractSliderButton {
        private final Component label;
        private final double min, max, step;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> format;

        Slider(int x, int y, int w, int h, Component label, double min, double max, double step, double value,
               DoubleConsumer setter, DoubleFunction<String> format) {
            super(x, y, w, h, Component.empty(), (value - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.format = format;
            updateMessage();
        }

        private double current() {
            double raw = min + value * (max - min);
            return Mth.clamp(min + Math.round((raw - min) / step) * step, min, max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty().append(label).append(": " + format.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
