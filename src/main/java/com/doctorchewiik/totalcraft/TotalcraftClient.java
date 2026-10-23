package com.doctorchewiik.totalcraft;

import com.doctorchewiik.totalcraft.client.ClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Totalcraft.MODID, dist = Dist.CLIENT)
public class TotalcraftClient {
    public TotalcraftClient(IEventBus modBus) {
        modBus.addListener(ClientEvents::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onComputeFov);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
    }
}
