package com.doctorchewiik.totalcraft.data;

import com.doctorchewiik.totalcraft.Totalcraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unit and map definitions, reloaded with datapacks so they can be tweaked without code. */
public final class GameData {
    private static final Gson GSON = new GsonBuilder().create();

    public static final Map<String, UnitType> UNITS = new LinkedHashMap<>();
    public static final Map<String, MapType> MAPS = new LinkedHashMap<>();

    private GameData() {
    }

    public static UnitType unit(String id) {
        return UNITS.get(id);
    }

    public static MapType map(String id) {
        return MAPS.get(id);
    }

    public static class UnitLoader extends SimpleJsonResourceReloadListener {
        public UnitLoader() {
            super(GSON, "totalcraft/units");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            List<UnitType> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : entries.entrySet()) {
                try {
                    loaded.add(UnitType.fromJson(e.getKey().getPath(), e.getValue().getAsJsonObject()));
                } catch (Exception ex) {
                    Totalcraft.LOGGER.error("Unité invalide {} : {}", e.getKey(), ex.getMessage());
                }
            }
            loaded.sort(Comparator.comparing(UnitType::champion).thenComparingInt(UnitType::cost));
            UNITS.clear();
            loaded.forEach(u -> UNITS.put(u.id(), u));
            Totalcraft.LOGGER.info("Totalcraft : {} types d'unités chargés", UNITS.size());
        }
    }

    public static class MapLoader extends SimpleJsonResourceReloadListener {
        public MapLoader() {
            super(GSON, "totalcraft/maps");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            MAPS.clear();
            entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        try {
                            MAPS.put(e.getKey().getPath(), MapType.fromJson(e.getKey().getPath(), e.getValue().getAsJsonObject()));
                        } catch (Exception ex) {
                            Totalcraft.LOGGER.error("Carte invalide {} : {}", e.getKey(), ex.getMessage());
                        }
                    });
            Totalcraft.LOGGER.info("Totalcraft : {} cartes chargées", MAPS.size());
        }
    }
}
