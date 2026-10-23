package com.doctorchewiik.totalcraft;

import com.doctorchewiik.totalcraft.network.Network;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Totalcraft.MODID)
public class Totalcraft {
    public static final String MODID = "totalcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Totalcraft(IEventBus modBus) {
        modBus.addListener(Network::register);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
