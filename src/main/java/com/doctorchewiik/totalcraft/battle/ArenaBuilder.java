package com.doctorchewiik.totalcraft.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the fixed-size battlefield for a map's terrain preset. */
public final class ArenaBuilder {
    /** Half the side of the playable square. */
    public static final int HALF = 48;
    /** Y of the ground surface block; units stand at GROUND_Y + 1. */
    public static final int GROUND_Y = 63;
    /** Rows nearest each edge reserved for deployment, kept free of obstacles. */
    public static final int DEPLOY_DEPTH = 22;
    private static final int CLEAR_HEIGHT = 16;
    private static final int WALL_HEIGHT = 6;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private ArenaBuilder() {
    }

    public static void forceChunks(ServerLevel level, BlockPos center, boolean force) {
        ChunkPos min = new ChunkPos(center.offset(-HALF - 2, 0, -HALF - 2));
        ChunkPos max = new ChunkPos(center.offset(HALF + 2, 0, HALF + 2));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                level.setChunkForced(x, z, force);
            }
        }
    }

    public static void build(ServerLevel level, BlockPos center, String terrain, RandomSource random) {
        Palette p = Palette.of(terrain);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = HALF + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
                for (int y = GROUND_Y - 3; y < GROUND_Y; y++) {
                    level.setBlock(pos.set(x, y, z), p.subsurface, FLAGS);
                }
                BlockState surface = wall ? p.border : p.surface;
                if (!wall && p.patch != null && random.nextFloat() < p.patchChance) {
                    surface = p.patch;
                }
                level.setBlock(pos.set(x, GROUND_Y, z), surface, FLAGS);
                for (int y = GROUND_Y + 1; y <= GROUND_Y + CLEAR_HEIGHT; y++) {
                    BlockState state = wall && y <= GROUND_Y + WALL_HEIGHT ? Blocks.BARRIER.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos.set(x, y, z), state, FLAGS);
                }
            }
        }
        decorate(level, center, p, random);
    }

    private static void decorate(ServerLevel level, BlockPos center, Palette p, RandomSource random) {
        int inner = HALF - 3;
        int midDepth = HALF - DEPLOY_DEPTH;
        for (int i = 0; i < p.features; i++) {
            int dx = random.nextIntBetweenInclusive(-inner, inner);
            int dz = random.nextIntBetweenInclusive(-midDepth, midDepth);
            BlockPos base = center.offset(dx, 0, dz);
            switch (p.pick(random)) {
                case TREE -> tree(level, base, p.log, p.leaves, random);
                case SPRUCE -> spruce(level, base, random);
                case ROCK -> rock(level, base, p.rock, random);
                case RUIN -> ruin(level, base, p.rock, random);
                case CACTUS -> column(level, base, Blocks.CACTUS.defaultBlockState(), 1 + random.nextInt(3));
                case PILLAR -> column(level, base, p.rock, 2 + random.nextInt(4));
                case POOL -> pool(level, base, random);
                case BUSH -> level.setBlock(base.above(), p.leaves, FLAGS);
            }
        }
        // Small plants everywhere, including the deployment zones: they do not block movement.
        if (p.plant != null) {
            for (int i = 0; i < p.plants; i++) {
                int dx = random.nextIntBetweenInclusive(-inner, inner);
                int dz = random.nextIntBetweenInclusive(-inner, inner);
                BlockPos at = center.offset(dx, 1, dz);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(p.surface.getBlock())) {
                    level.setBlock(at, random.nextBoolean() || p.plant2 == null ? p.plant : p.plant2, FLAGS);
                }
            }
        }
    }

    private static void column(ServerLevel level, BlockPos base, BlockState state, int height) {
        for (int y = 1; y <= height; y++) {
            level.setBlock(base.above(y), state, FLAGS);
        }
    }

    private static void tree(ServerLevel level, BlockPos base, BlockState log, BlockState leaves, RandomSource random) {
        int height = 4 + random.nextInt(3);
        for (int y = height - 2; y <= height + 1; y++) {
            int radius = y > height ? 1 : 2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius && Math.abs(z) == radius && (radius == 1 || random.nextBoolean())) {
                        continue;
                    }
                    BlockPos at = base.offset(x, y, z);
                    if (level.getBlockState(at).isAir()) {
                        level.setBlock(at, leaves, FLAGS);
                    }
                }
            }
        }
        column(level, base, log, height);
    }

    private static void spruce(ServerLevel level, BlockPos base, RandomSource random) {
        int height = 6 + random.nextInt(3);
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 2; y <= height + 1; y++) {
            int radius = y > height - 1 ? 0 : ((height - y) % 2 == 0 ? 2 : 1);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (radius == 2 && Math.abs(x) == 2 && Math.abs(z) == 2) {
                        continue;
                    }
                    BlockPos at = base.offset(x, y, z);
                    if (level.getBlockState(at).isAir()) {
                        level.setBlock(at, leaves, FLAGS);
                    }
                }
            }
        }
        column(level, base, Blocks.SPRUCE_LOG.defaultBlockState(), height);
    }

    private static void rock(ServerLevel level, BlockPos base, BlockState rock, RandomSource random) {
        int size = 1 + random.nextInt(2);
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = 1; y <= size; y++) {
                    if (x * x + z * z + (y - 1) * (y - 1) <= size * size + random.nextInt(2)) {
                        level.setBlock(base.offset(x, y, z), rock, FLAGS);
                    }
                }
            }
        }
    }

    private static void ruin(ServerLevel level, BlockPos base, BlockState rock, RandomSource random) {
        boolean alongX = random.nextBoolean();
        int length = 4 + random.nextInt(4);
        for (int i = 0; i < length; i++) {
            int h = 1 + random.nextInt(3);
            BlockPos at = alongX ? base.offset(i, 0, 0) : base.offset(0, 0, i);
            column(level, at, rock, h);
        }
    }

    private static void pool(ServerLevel level, BlockPos base, RandomSource random) {
        int radius = 2 + random.nextInt(3);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    level.setBlock(base.offset(x, 0, z), Blocks.WATER.defaultBlockState(), FLAGS);
                    if (random.nextInt(6) == 0) {
                        level.setBlock(base.offset(x, 1, z), Blocks.LILY_PAD.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    private enum Feature { TREE, SPRUCE, ROCK, RUIN, CACTUS, PILLAR, POOL, BUSH }

    private record Palette(BlockState surface, BlockState subsurface, BlockState border, BlockState patch,
                           float patchChance, BlockState log, BlockState leaves, BlockState rock, BlockState plant,
                           BlockState plant2, int plants, int features, Feature[] featureWeights) {
        Feature pick(RandomSource random) {
            return featureWeights[random.nextInt(featureWeights.length)];
        }

        static BlockState leaves(Block block) {
            return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        }

        static Palette of(String terrain) {
            return switch (terrain) {
                case "forest" -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                        Blocks.STONE_BRICKS.defaultBlockState(), Blocks.PODZOL.defaultBlockState(), 0.15f,
                        Blocks.OAK_LOG.defaultBlockState(), leaves(Blocks.OAK_LEAVES),
                        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.FERN.defaultBlockState(),
                        Blocks.SHORT_GRASS.defaultBlockState(), 500, 70,
                        new Feature[]{Feature.TREE, Feature.TREE, Feature.TREE, Feature.BUSH, Feature.ROCK});
                case "desert" -> new Palette(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                        Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.RED_SAND.defaultBlockState(), 0.05f,
                        Blocks.OAK_LOG.defaultBlockState(), leaves(Blocks.OAK_LEAVES),
                        Blocks.SANDSTONE.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState(), null, 120, 30,
                        new Feature[]{Feature.CACTUS, Feature.CACTUS, Feature.RUIN, Feature.PILLAR});
                case "tundra" -> new Palette(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                        Blocks.PACKED_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), 0.06f,
                        Blocks.SPRUCE_LOG.defaultBlockState(), leaves(Blocks.SPRUCE_LEAVES),
                        Blocks.STONE.defaultBlockState(), Blocks.SNOW.defaultBlockState(), null, 300, 35,
                        new Feature[]{Feature.SPRUCE, Feature.SPRUCE, Feature.ROCK});
                case "wasteland" -> new Palette(Blocks.NETHERRACK.defaultBlockState(),
                        Blocks.NETHERRACK.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                        Blocks.SOUL_SOIL.defaultBlockState(), 0.2f, Blocks.BONE_BLOCK.defaultBlockState(),
                        leaves(Blocks.OAK_LEAVES), Blocks.BLACKSTONE.defaultBlockState(),
                        Blocks.CRIMSON_ROOTS.defaultBlockState(), null, 0, 30,
                        new Feature[]{Feature.PILLAR, Feature.ROCK, Feature.RUIN});
                case "swamp" -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.MUD.defaultBlockState(),
                        Blocks.MUD_BRICKS.defaultBlockState(), Blocks.MUD.defaultBlockState(), 0.2f,
                        Blocks.MANGROVE_LOG.defaultBlockState(), leaves(Blocks.MANGROVE_LEAVES),
                        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                        Blocks.BLUE_ORCHID.defaultBlockState(), 300, 40,
                        new Feature[]{Feature.POOL, Feature.POOL, Feature.TREE, Feature.BUSH});
                default -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                        Blocks.STONE_BRICKS.defaultBlockState(), null, 0f, Blocks.OAK_LOG.defaultBlockState(),
                        leaves(Blocks.OAK_LEAVES), Blocks.COBBLESTONE.defaultBlockState(),
                        Blocks.SHORT_GRASS.defaultBlockState(), Blocks.POPPY.defaultBlockState(), 400, 12,
                        new Feature[]{Feature.TREE, Feature.ROCK, Feature.RUIN});
            };
        }
    }
}
