package com.doctorchewiik.totalcraft.battle;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Rank-and-file layout of a squad around its anchor. */
final class Formation {
    private Formation() {
    }

    static Vec3 slot(Squad squad, int index, int count, Vec3 anchor, Vec3 facing) {
        int cols = Math.max(1, Math.min(count, (int) Math.ceil(Math.sqrt(count) * 1.5)));
        double spacing = Math.max(1.6, squad.type.scale() * 1.8);
        int col = index % cols;
        int row = index / cols;
        Vec3 right = new Vec3(-facing.z, 0, facing.x);
        double side = (col - (cols - 1) / 2.0) * spacing;
        double back = -row * spacing;
        return anchor.add(right.scale(side)).add(facing.scale(back));
    }

    static float yaw(Vec3 facing) {
        return (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90f;
    }

    static void face(Mob mob, Vec3 facing) {
        float yaw = yaw(facing);
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }
}
