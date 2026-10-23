package com.doctorchewiik.totalcraft.data;

import java.util.Locale;

/** Spells and buffs available to champions. Behaviour lives in battle.Abilities. */
public enum Ability {
    WAR_CRY("Cri de guerre", "Alliés proches : +30% de dégâts pendant 10 s", 600),
    CHARGE("Charge", "Alliés proches : +40% de vitesse et +15% de dégâts pendant 6 s", 500),
    SHIELD_WALL("Mur de boucliers", "Alliés proches : -40% de dégâts subis pendant 10 s", 700),
    FIRE_NOVA("Nova de feu", "Enflamme et blesse les ennemis autour du champion", 400),
    FANGS("Crocs de l'évocateur", "Une ligne de crocs vers l'escouade ennemie la plus proche", 300),
    LIGHTNING("Foudre", "Frappe l'escouade ennemie la plus proche", 600),
    HEAL_AURA("Aura de soin", "Soigne 35% des PV des alliés proches", 700);

    private final String displayName;
    private final String description;
    private final int cooldownTicks;

    Ability(String displayName, String description, int cooldownTicks) {
        this.displayName = displayName;
        this.description = description;
        this.cooldownTicks = cooldownTicks;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public static Ability byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
