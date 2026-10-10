package com.summercraft.dev;

import com.summercraft.ModRegistry;
import com.summercraft.SummerCraftCommands;
import com.summercraft.car.CarEntity;
import com.summercraft.car.EngineType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Development aid: with SUMMERCRAFT_SHOWCASE set, builds a little test yard next to the first player to join
 * (a car with its front on a slab, one with its front up a full block, one parked at 45 degrees) and flies the camera
 * round so the client half can take screenshots. Does nothing otherwise.
 */
public final class Showcase {
    /** Camera position and look target relative to a subject car, and whether to show hitboxes. */
    public record View(int tick, int subject, Vec3 camera, Vec3 target, boolean hitboxes) {}

    public static final View[] VIEWS = {
            new View(80, 0, new Vec3(4.2, 1.1, 0.2), new Vec3(0, 0.7, 0), false),
            new View(120, 1, new Vec3(4.2, 1.3, 0.2), new Vec3(0, 0.9, 0), false),
            new View(160, 2, new Vec3(2.5, 5.0, 1.0), new Vec3(0, 0.5, 0), true),
    };
    public static final int SCREEN_OPEN = -1, SCREEN_SHOT = 210;

    private static ServerPlayer player;
    private static final List<CarEntity> subjects = new ArrayList<>();
    private static int tick;

    public static boolean enabled() {
        return System.getenv("SUMMERCRAFT_SHOWCASE") != null;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer joined) {
                player = joined;
                tick = 0;
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> onTick());
    }

    private static void onTick() {
        if (player == null) return;
        tick++;
        ServerLevel level = player.serverLevel();
        if (tick == 20) {
            level.setDayTime(5000);
            level.setWeatherParameters(6000, 0, false, false);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            BlockPos origin = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(10))));
            // A flat stone pad to build on.
            for (int x = -8; x <= 8; x++) {
                for (int z = -6; z <= 6; z++) {
                    level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y < 4; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            // Car 0: front wheels on a slab. Car 1: front wheels up a full block. Cars face +Z (yaw 0).
            for (int x = -1; x <= 0; x++) {
                for (int z = 1; z <= 3; z++) {
                    level.setBlockAndUpdate(origin.offset(x - 5, 0, z), Blocks.STONE_SLAB.defaultBlockState());
                    level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            subjects.add(spawn(level, Vec3.atBottomCenterOf(origin.offset(-5, 0, 0)).add(0, 0, -0.3), 0));
            subjects.add(spawn(level, Vec3.atBottomCenterOf(origin).add(0, 0, -0.3), 0));
            // Car 2: parked at 45 degrees next to a wall.
            for (int z = -5; z <= 5; z++) {
                level.setBlockAndUpdate(origin.offset(7, 0, z), Blocks.BRICKS.defaultBlockState());
                level.setBlockAndUpdate(origin.offset(7, 1, z), Blocks.BRICKS.defaultBlockState());
            }
            subjects.add(spawn(level, Vec3.atBottomCenterOf(origin.offset(5, 0, 0)), 45));
        }
        if (subjects.isEmpty()) return;
        for (View view : VIEWS) {
            if (tick != view.tick()) continue;
            CarEntity car = subjects.get(view.subject());
            Vec3 camera = car.position().add(view.camera()), target = car.position().add(view.target());
            Vec3 d = target.subtract(camera);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
            player.teleportTo(level, camera.x, camera.y - player.getEyeHeight(), camera.z, yaw, pitch);
        }
    }

    private static CarEntity spawn(ServerLevel level, Vec3 at, float yaw) {
        CarEntity car = SummerCraftCommands.spawn(level, ModRegistry.CAR.get(), at.add(0, 1.2, 0), yaw);
        car.buildComplete(EngineType.INLINE_4, false, false);
        car.setColor(0xD9822B);
        return car;
    }

    private Showcase() {}
}
