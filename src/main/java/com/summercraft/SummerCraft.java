package com.summercraft;

import com.summercraft.dev.Showcase;
import com.summercraft.network.DriveStatePayload;
import com.summercraft.network.HornPayload;
import com.summercraft.network.IgnitionPayload;
import com.summercraft.network.SetupPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(SummerCraft.MODID)
public class SummerCraft {
    public static final String MODID = "summercraft";

    public SummerCraft(IEventBus modBus) {
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.ENTITY_TYPES.register(modBus);
        ModRegistry.TABS.register(modBus);
        ModRegistry.COMPONENTS.register(modBus);
        ModRegistry.SOUNDS.register(modBus);

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("4")
                .playToServer(IgnitionPayload.TYPE, IgnitionPayload.STREAM_CODEC, IgnitionPayload::handle)
                .playToServer(HornPayload.TYPE, HornPayload.STREAM_CODEC, HornPayload::handle)
                .playToServer(DriveStatePayload.TYPE, DriveStatePayload.STREAM_CODEC, DriveStatePayload::handle)
                .playToServer(SetupPayload.TYPE, SetupPayload.STREAM_CODEC, SetupPayload::handle));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> SummerCraftCommands.register(event.getDispatcher()));
        if (Showcase.enabled()) Showcase.register();
    }
}
