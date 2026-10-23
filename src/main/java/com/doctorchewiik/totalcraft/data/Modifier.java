package com.doctorchewiik.totalcraft.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.Map;

/** A multiplier applied to every unit matching {@code target} (a category, a tag, a unit id or "all"). */
public record Modifier(String target, Stat stat, double value) {
    public enum Stat { DAMAGE, DEFENSE, SPEED, HEALTH }

    private static final Map<String, String> TARGET_LABELS = Map.ofEntries(
            Map.entry("all", "Toutes les unités"),
            Map.entry("infantry", "Infanterie"),
            Map.entry("ranged", "Unités à distance"),
            Map.entry("cavalry", "Cavalerie"),
            Map.entry("monster", "Monstres"),
            Map.entry("champion", "Champions"),
            Map.entry("undead", "Morts-vivants"),
            Map.entry("beast", "Bêtes"),
            Map.entry("illager", "Illageois"),
            Map.entry("magic", "Magie"),
            Map.entry("desert", "Unités du désert"),
            Map.entry("frost", "Unités du givre"),
            Map.entry("large", "Grandes unités"),
            Map.entry("arthropod", "Arthropodes"),
            Map.entry("construct", "Golems"));

    public static Modifier fromJson(JsonObject obj) {
        return new Modifier(
                GsonHelper.getAsString(obj, "target", "all"),
                Stat.valueOf(GsonHelper.getAsString(obj, "stat").toUpperCase(Locale.ROOT)),
                GsonHelper.getAsDouble(obj, "value", 1.0));
    }

    public boolean appliesTo(UnitType type) {
        return target.equals("all") || type.matches(target);
    }

    public static String label(String key) {
        return TARGET_LABELS.getOrDefault(key, key);
    }

    public String describe() {
        String statLabel = switch (stat) {
            case DAMAGE -> "dégâts";
            case DEFENSE -> "défense";
            case SPEED -> "vitesse";
            case HEALTH -> "points de vie";
        };
        int percent = (int) Math.round((value - 1.0) * 100);
        return label(target) + " : " + statLabel + " " + (percent >= 0 ? "+" : "") + percent + "%";
    }
}
