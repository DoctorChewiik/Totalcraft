package com.doctorchewiik.totalcraft.network;

/**
 * Latest state received from the server. Plain data with no client-only classes,
 * so the payload handlers can be registered on both sides.
 */
public final class ClientState {
    public static BattleSyncPayload battle = BattleSyncPayload.inactive();
    public static ProfileSyncPayload profile;
    /** Bumped on every profile update so open screens know to rebuild. */
    public static int profileVersion;
    public static boolean openArmyScreen;

    private ClientState() {
    }

    static void onProfile(ProfileSyncPayload payload) {
        profile = payload;
        profileVersion++;
        if (payload.open()) {
            openArmyScreen = true;
        }
    }

    public static void reset() {
        battle = BattleSyncPayload.inactive();
        profile = null;
        openArmyScreen = false;
    }
}
