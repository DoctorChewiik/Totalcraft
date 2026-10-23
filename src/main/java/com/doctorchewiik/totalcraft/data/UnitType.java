package com.doctorchewiik.totalcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A recruitable squad type, loaded from data/<namespace>/totalcraft/units/*.json. */
public record UnitType(
        String id,
        String name,
        String description,
        ResourceLocation entity,
        int squadSize,
        int cost,
        String category,
        Set<String> tags,
        double health,
        double damage,
        double armor,
        double speed,
        double range,
        int attackCooldown,
        String projectile,
        double morale,
        double scale,
        Map<String, Double> bonusVs,
        List<Ability> abilities,
        Map<EquipmentSlot, ResourceLocation> equipment) {

    public static UnitType fromJson(String id, JsonObject obj) {
        Set<String> tags = new LinkedHashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(obj, "tags", new com.google.gson.JsonArray())) {
            tags.add(e.getAsString());
        }
        Map<String, Double> bonus = new LinkedHashMap<>();
        JsonObject bonusObj = GsonHelper.getAsJsonObject(obj, "bonus_vs", new JsonObject());
        for (Map.Entry<String, JsonElement> e : bonusObj.entrySet()) {
            bonus.put(e.getKey(), e.getValue().getAsDouble());
        }
        List<Ability> abilities = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(obj, "abilities", new com.google.gson.JsonArray())) {
            abilities.add(Ability.byId(e.getAsString()));
        }
        Map<EquipmentSlot, ResourceLocation> equipment = new EnumMap<>(EquipmentSlot.class);
        JsonObject equipObj = GsonHelper.getAsJsonObject(obj, "equipment", new JsonObject());
        for (Map.Entry<String, JsonElement> e : equipObj.entrySet()) {
            equipment.put(EquipmentSlot.byName(e.getKey()), ResourceLocation.parse(e.getValue().getAsString()));
        }
        return new UnitType(
                id,
                GsonHelper.getAsString(obj, "name", id),
                GsonHelper.getAsString(obj, "description", ""),
                ResourceLocation.parse(GsonHelper.getAsString(obj, "entity")),
                Math.max(1, GsonHelper.getAsInt(obj, "squad_size", 1)),
                GsonHelper.getAsInt(obj, "cost", 100),
                GsonHelper.getAsString(obj, "category", "infantry").toLowerCase(Locale.ROOT),
                tags,
                GsonHelper.getAsDouble(obj, "health", 20),
                GsonHelper.getAsDouble(obj, "damage", 3),
                GsonHelper.getAsDouble(obj, "armor", 0),
                GsonHelper.getAsDouble(obj, "speed", 0.25),
                GsonHelper.getAsDouble(obj, "range", 0),
                GsonHelper.getAsInt(obj, "attack_cooldown", 20),
                GsonHelper.getAsString(obj, "projectile", "arrow"),
                GsonHelper.getAsDouble(obj, "morale", 70),
                GsonHelper.getAsDouble(obj, "scale", 1.0),
                bonus,
                abilities,
                equipment);
    }

    public boolean champion() {
        return category.equals("champion");
    }

    public boolean ranged() {
        return range > 0;
    }

    public boolean matches(String key) {
        return id.equals(key) || category.equals(key) || tags.contains(key) || (key.equals("ranged") && ranged());
    }

    /** Damage multiplier when this unit attacks {@code other}. */
    public double bonusAgainst(UnitType other) {
        double mult = 1.0;
        for (Map.Entry<String, Double> e : bonusVs.entrySet()) {
            if (other.matches(e.getKey())) {
                mult *= e.getValue();
            }
        }
        return mult;
    }

    public String categoryLabel() {
        return switch (category) {
            case "infantry" -> "Infanterie";
            case "ranged" -> "Distance";
            case "cavalry" -> "Cavalerie";
            case "monster" -> "Monstre";
            case "champion" -> "Champion";
            default -> category;
        };
    }

    /** Multi-line description shown in tooltips. */
    public String fullDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(categoryLabel()).append(", ").append(squadSize)
                .append(squadSize > 1 ? " unités" : " unité").append(")\n");
        if (!description.isEmpty()) {
            sb.append(description).append('\n');
        }
        sb.append("PV ").append(fmt(health)).append("  Dégâts ").append(fmt(damage))
                .append("  Armure ").append(fmt(armor)).append("  Vitesse ").append(fmt(speed * 100));
        if (ranged()) {
            sb.append("  Portée ").append(fmt(range));
        }
        sb.append("  Moral ").append(fmt(morale));
        List<String> strong = new ArrayList<>();
        List<String> weak = new ArrayList<>();
        for (Map.Entry<String, Double> e : bonusVs.entrySet()) {
            int percent = (int) Math.round((e.getValue() - 1.0) * 100);
            String text = Modifier.label(e.getKey()) + " (" + (percent >= 0 ? "+" : "") + percent + "%)";
            (percent >= 0 ? strong : weak).add(text);
        }
        if (!strong.isEmpty()) {
            sb.append("\nFort contre : ").append(String.join(", ", strong));
        }
        if (!weak.isEmpty()) {
            sb.append("\nFaible contre : ").append(String.join(", ", weak));
        }
        if (!abilities.isEmpty()) {
            sb.append("\nSorts : ");
            sb.append(String.join(", ", abilities.stream().map(Ability::displayName).toList()));
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
