package com.doctorchewiik.totalcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/** A battlefield, loaded from data/<namespace>/totalcraft/maps/*.json. */
public record MapType(String id, String name, String description, String terrain, double goldMultiplier,
                      List<Modifier> modifiers) {

    public static MapType fromJson(String id, JsonObject obj) {
        List<Modifier> mods = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(obj, "modifiers", new com.google.gson.JsonArray())) {
            mods.add(Modifier.fromJson(e.getAsJsonObject()));
        }
        return new MapType(
                id,
                GsonHelper.getAsString(obj, "name", id),
                GsonHelper.getAsString(obj, "description", ""),
                GsonHelper.getAsString(obj, "terrain", "plains"),
                GsonHelper.getAsDouble(obj, "gold_multiplier", 1.0),
                mods);
    }

    public double multiplier(UnitType type, Modifier.Stat stat) {
        double mult = 1.0;
        for (Modifier m : modifiers) {
            if (m.stat() == stat && m.appliesTo(type)) {
                mult *= m.value();
            }
        }
        return mult;
    }

    public String fullDescription() {
        StringBuilder sb = new StringBuilder(name);
        if (!description.isEmpty()) {
            sb.append('\n').append(description);
        }
        for (Modifier m : modifiers) {
            sb.append("\n• ").append(m.describe());
        }
        if (goldMultiplier != 1.0) {
            int percent = (int) Math.round((goldMultiplier - 1.0) * 100);
            sb.append("\n• Butin : ").append(percent >= 0 ? "+" : "").append(percent).append('%');
        }
        return sb.toString();
    }
}
