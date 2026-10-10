package com.summercraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.summercraft.car.CarEntity;
import com.summercraft.car.EngineType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /summercraft car [engine] [boost]} spawns a finished car, {@code /summercraft body} a bare one,
 * {@code /summercraft stand [engine] [boost]} an engine stand with a finished engine on it.
 * Engine is inline_4, boxer_4, v6 or v8; boost is none, turbo, supercharger or twin.
 */
public final class SummerCraftCommands {
    private static final List<String> BOOST = List.of("none", "turbo", "supercharger", "twin");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SummerCraft.MODID)
                .requires(source -> source.hasPermission(2))
                .then(built("car", false))
                .then(built("stand", true))
                .then(Commands.literal("body").executes(ctx -> spawn(ctx.getSource(), false, null, "none"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> built(String name, boolean stand) {
        RequiredArgumentBuilder<CommandSourceStack, String> boost = Commands.argument("boost", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BOOST, b))
                .executes(ctx -> spawn(ctx.getSource(), stand, engine(StringArgumentType.getString(ctx, "engine")), StringArgumentType.getString(ctx, "boost")));
        RequiredArgumentBuilder<CommandSourceStack, String> engine = Commands.argument("engine", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(EngineType.values()).map(EngineType::id), b))
                .executes(ctx -> spawn(ctx.getSource(), stand, engine(StringArgumentType.getString(ctx, "engine")), "none"))
                .then(boost);
        return Commands.literal(name).executes(ctx -> spawn(ctx.getSource(), stand, EngineType.INLINE_4, "none")).then(engine);
    }

    private static EngineType engine(String id) {
        for (EngineType type : EngineType.values()) if (type.id().equals(id.toLowerCase(Locale.ROOT))) return type;
        return EngineType.INLINE_4;
    }

    private static int spawn(CommandSourceStack source, boolean stand, EngineType engine, String boost) {
        ServerLevel level = source.getLevel();
        Vec3 look = Vec3.directionFromRotation(0, source.getRotation().y);
        Vec3 at = source.getPosition().add(look.scale(stand ? 2.5 : 4));
        boolean turbo = boost.equals("turbo") || boost.equals("twin");
        boolean supercharger = boost.equals("supercharger") || boost.equals("twin");
        CarEntity car = spawn(level, stand ? ModRegistry.ENGINE_STAND.get() : ModRegistry.CAR.get(), at, source.getRotation().y + 90);
        if (car == null) return 0;
        if (engine != null) car.buildComplete(engine, turbo, supercharger);
        source.sendSuccess(() -> Component.translatable("message.summercraft.spawned"), false);
        return 1;
    }

    public static CarEntity spawn(ServerLevel level, EntityType<CarEntity> type, Vec3 at, float yaw) {
        CarEntity car = type.create(level);
        if (car == null) return null;
        car.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(car);
        return car;
    }

    private SummerCraftCommands() {}
}
