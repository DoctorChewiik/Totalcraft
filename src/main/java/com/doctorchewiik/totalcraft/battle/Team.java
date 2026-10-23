package com.doctorchewiik.totalcraft.battle;

public enum Team {
    PLAYERS,
    ENEMY;

    public Team opponent() {
        return this == PLAYERS ? ENEMY : PLAYERS;
    }

    /** +1 when the team's deployment zone is on the north side (negative z), -1 otherwise. */
    public int side() {
        return this == PLAYERS ? -1 : 1;
    }
}
