package futurefrost.anecdote.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LibraryChunkGenerator extends ChunkGenerator {

    public static final Codec<LibraryChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(LibraryChunkGenerator::getSettings),
                    Codec.LONG.fieldOf("seed").forGetter(generator -> generator.seed)
            ).apply(instance, instance.stable(LibraryChunkGenerator::new))
    );

    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final Random random;
    private final long seed;

    // Define blocks as constants
    private static final BlockState FLOOR_BLOCK = Blocks.STONE_BRICKS.getDefaultState();
    private static final BlockState CEILING_BLOCK = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState WALL_BLOCK = Blocks.OAK_PLANKS.getDefaultState();
    private static final BlockState BOOKSHELF_BLOCK = Blocks.BOOKSHELF.getDefaultState();
    private static final BlockState AIR_BLOCK = Blocks.AIR.getDefaultState();
    private static final BlockState FILLER_BLOCK = Blocks.STONE.getDefaultState();
    private static final BlockState BEDROCK_BLOCK = Blocks.BEDROCK.getDefaultState();

    // Maze dimensions
    private static final int CORRIDOR_WIDTH = 5;      // 5 blocks wide
    private static final int CORRIDOR_HEIGHT = 5;     // 5 blocks tall
    private static final int WALL_THICKNESS = 1;      // Walls are 1 block thick

    // Each cell is wall + corridor + wall = 7 blocks
    private static final int CELL_SIZE = WALL_THICKNESS + CORRIDOR_WIDTH + WALL_THICKNESS; // 1 + 5 + 1 = 7

    // Y-levels - corridor space only
    private static final int FLOOR_Y = 10;
    private static final int CEILING_Y = FLOOR_Y + CORRIDOR_HEIGHT + 1; // 64 + 4 = 68 (64,65,66,67,68 = 5 blocks)

    // Wall vertical range - walls only exist within the corridor vertical space
    private static final int WALL_MIN_Y = FLOOR_Y + 1;
    private static final int WALL_MAX_Y = CEILING_Y - 1;

    // Filler for below/above the maze
    private static final int FILLER_MIN_Y = FLOOR_Y - 10;
    private static final int FILLER_MAX_Y = CEILING_Y + 10;

    // Bookshelf generation parameters - adjust these to control frequency
    private static final int BOOKSHELF_CLUSTERS_MIN = 32;  // Minimum clusters per chunk
    private static final int BOOKSHELF_CLUSTERS_MAX = 96;  // Maximum clusters per chunk
    private static final int BOOKSHELF_VEIN_MIN = 8;       // Minimum blocks per vein
    private static final int BOOKSHELF_VEIN_MAX = 32;      // Maximum blocks per vein

    public LibraryChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, long seed) {
        super(biomeSource);
        this.settings = settings;
        this.seed = seed;
        this.random = new Xoroshiro128PlusPlusRandom(seed);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<ChunkGeneratorSettings> getSettings() {
        return settings;
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
        // No carving needed
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        // Get a random for this chunk
        Random chunkRandom = new Xoroshiro128PlusPlusRandom(seed + chunkPos.x * 34129L + chunkPos.z * 53951L);

        // First pass: build the basic maze structure
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                // Determine if this position is a wall or corridor based on grid
                boolean isWall = isGridWall(worldX, worldZ);

                for (int y = FILLER_MIN_Y; y <= FILLER_MAX_Y; y++) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    Random bedrockRandom = new Xoroshiro128PlusPlusRandom(seed + worldX * 49632L + worldZ * 325176L + y);

                    // Bottom bedrock layers (Nether floor style)
                    if (shouldPlaceBedrock(y, worldX, worldZ, bedrockRandom, true)) {
                        chunk.setBlockState(pos, BEDROCK_BLOCK, false);
                        continue;
                    }

                    // Top bedrock layers (Nether ceiling style)
                    if (shouldPlaceBedrock(y, worldX, worldZ, bedrockRandom, false)) {
                        chunk.setBlockState(pos, BEDROCK_BLOCK, false);
                        continue;
                    }

                    // Fill below the maze with stone
                    if (y < FLOOR_Y) {
                        chunk.setBlockState(pos, FILLER_BLOCK, false);
                        continue;
                    }

                    // Fill above the maze with stone
                    if (y > CEILING_Y) {
                        chunk.setBlockState(pos, FILLER_BLOCK, false);
                        continue;
                    }

                    // Inside the maze vertical space
                    if (y == FLOOR_Y) {
                        // Floor level - always stone bricks
                        chunk.setBlockState(pos, FLOOR_BLOCK, false);
                    } else if (y == CEILING_Y) {
                        // Ceiling level - always spruce planks
                        chunk.setBlockState(pos, CEILING_BLOCK, false);
                    } else if (isWall) {
                        // Wall interior levels (between floor and ceiling)
                        chunk.setBlockState(pos, WALL_BLOCK, false);
                    } else {
                        // Corridor interior levels (between floor and ceiling)
                        chunk.setBlockState(pos, AIR_BLOCK, false);
                    }
                }
            }
        }

        // Second pass: add bookshelves to the walls (like ore generation)
        generateBookshelves(chunk, chunkRandom, startX, startZ);
    }

    private void generateBookshelves(Chunk chunk, Random random, int startX, int startZ) {
        int bookshelfCount = BOOKSHELF_CLUSTERS_MIN + random.nextInt(BOOKSHELF_CLUSTERS_MAX - BOOKSHELF_CLUSTERS_MIN + 1);

        for (int cluster = 0; cluster < bookshelfCount; cluster++) {
            // Pick a random position in the chunk
            int x = startX + random.nextInt(16);
            int z = startZ + random.nextInt(16);
            int y = WALL_MIN_Y + random.nextInt(WALL_MAX_Y - WALL_MIN_Y + 1);

            BlockPos centerPos = new BlockPos(x, y, z);

            // Check if this is a wall block
            if (chunk.getBlockState(centerPos).isOf(Blocks.OAK_PLANKS)) {
                // Generate a vein of bookshelves
                int veinSize = BOOKSHELF_VEIN_MIN + random.nextInt(BOOKSHELF_VEIN_MAX - BOOKSHELF_VEIN_MIN + 1);

                for (int i = 0; i < veinSize; i++) {
                    // Random offset within area
                    BlockPos veinPos = centerPos.add(
                            random.nextInt(7) - 3,
                            random.nextInt(5) - 2,
                            random.nextInt(7) - 3
                    );

                    // Make sure we're still in the chunk and within wall Y-range
                    if (veinPos.getX() >= startX && veinPos.getX() < startX + 16 &&
                            veinPos.getZ() >= startZ && veinPos.getZ() < startZ + 16 &&
                            veinPos.getY() >= WALL_MIN_Y && veinPos.getY() <= WALL_MAX_Y) {

                        // Only replace oak planks with bookshelves
                        if (chunk.getBlockState(veinPos).isOf(Blocks.OAK_PLANKS)) {
                            chunk.setBlockState(veinPos, BOOKSHELF_BLOCK, false);
                        }
                    }
                }
            }
        }
    }

    private boolean isGridWall(int x, int z) {
        // Each cell is 7 blocks: [Wall][5 Corridor][Wall]
        // Get the cell coordinates and position within cell
        int cellX = Math.floorDiv(x, CELL_SIZE);
        int cellZ = Math.floorDiv(z, CELL_SIZE);
        int inCellX = Math.floorMod(x, CELL_SIZE);
        int inCellZ = Math.floorMod(z, CELL_SIZE);

        // Make sure modulo works correctly for negative numbers
        if (inCellX < 0) inCellX += CELL_SIZE;
        if (inCellZ < 0) inCellZ += CELL_SIZE;

        // In a 7-block pattern, walls are at positions 0 and 6
        boolean isOnXWall = inCellX == 0 || inCellX == 6;
        boolean isOnZWall = inCellZ == 0 || inCellZ == 6;

        // Create a deterministic random for this cell
        Random cellRandom = new Xoroshiro128PlusPlusRandom(seed + cellX * 49632L + cellZ * 325176L);

        // If we're at a corner (both X and Z walls), always wall
        if (isOnXWall && isOnZWall) {
            return true;
        }

        // If we're not on any wall, definitely corridor (positions 1-5 in both axes)
        if (!isOnXWall && !isOnZWall) {
            return false;
        }

        // We're on a wall in one direction only
        if (isOnXWall && !isOnZWall) {
            // Vertical wall segment (wall running along Z direction)
            // Doorways should be in the middle of the corridor (positions 2-4)
            boolean isDoorwayZ = inCellZ >= 2 && inCellZ <= 4;

            if (isDoorwayZ) {
                // Decide if this wall segment is open
                if (inCellX == 0) {
                    // Left wall of cell - connects to cell on left
                    Random leftCellRandom = new Xoroshiro128PlusPlusRandom(seed + (cellX - 1) * 49632L + cellZ * 325176L);
                    return !leftCellRandom.nextBoolean(); // false = open passage (not wall)
                } else { // inCellX == 6
                    // Right wall of cell - connects to cell on right
                    return !cellRandom.nextBoolean(); // false = open passage (not wall)
                }
            } else {
                return true; // Solid wall
            }
        }

        if (!isOnXWall && isOnZWall) {
            // Horizontal wall segment (wall running along X direction)
            boolean isDoorwayX = inCellX >= 2 && inCellX <= 4;

            if (isDoorwayX) {
                if (inCellZ == 0) {
                    // Bottom wall - connects to cell below
                    Random downCellRandom = new Xoroshiro128PlusPlusRandom(seed + cellX * 49632L + (cellZ - 1) * 325176L);
                    return !downCellRandom.nextBoolean(); // false = open passage
                } else { // inCellZ == 6
                    // Top wall - connects to cell above
                    return !cellRandom.nextBoolean(); // false = open passage
                }
            } else {
                return true; // Solid wall
            }
        }

        return true;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig,
                                                  StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        // Return appropriate height for the heightmap
        if (isGridWall(x, z)) {
            return CEILING_Y;
        } else {
            return FLOOR_Y + 1;
        }
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] states = new BlockState[world.getHeight()];
        boolean isWall = isGridWall(x, z);

        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            int index = y - world.getBottomY();

            // Fill below maze
            if (y < FLOOR_Y) {
                states[index] = FILLER_BLOCK;
                continue;
            }

            // Fill above maze
            if (y > CEILING_Y) {
                states[index] = FILLER_BLOCK;
                continue;
            }

            // Inside maze
            if (isWall) {
                states[index] = WALL_BLOCK;
            } else {
                if (y == FLOOR_Y) {
                    states[index] = FLOOR_BLOCK;
                } else if (y == CEILING_Y) {
                    states[index] = CEILING_BLOCK;
                } else {
                    states[index] = AIR_BLOCK;
                }
            }
        }

        return new VerticalBlockSample(world.getBottomY(), states);
    }

    private boolean shouldPlaceBedrock(int y, int x, int z, Random random, boolean isBottom) {
        if (isBottom) {
            if (y == FILLER_MIN_Y) return true;
            if (y == FILLER_MIN_Y + 1) return random.nextInt(3) == 0; // 33% chance for second layer
            if (y == FILLER_MIN_Y + 2) return random.nextInt(10) == 0; // 10% chance for third layer
        } else {
            if (y == FILLER_MAX_Y) return true;
            if (y == FILLER_MAX_Y - 1) return random.nextInt(3) == 0; // 33% chance for second layer
            if (y == FILLER_MAX_Y - 2) return random.nextInt(10) == 0; // 10% chance for third layer
        }
        return false;
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        int cellX = Math.floorDiv(pos.getX(), CELL_SIZE);
        int cellZ = Math.floorDiv(pos.getZ(), CELL_SIZE);
        int inCellX = Math.floorMod(pos.getX(), CELL_SIZE);
        int inCellZ = Math.floorMod(pos.getZ(), CELL_SIZE);

        // Fix negative modulo
        if (inCellX < 0) inCellX += CELL_SIZE;
        if (inCellZ < 0) inCellZ += CELL_SIZE;

        text.add("Maze Grid Generator");
        text.add(String.format("Cell: [%d, %d]", cellX, cellZ));
        text.add(String.format("In Cell: [%d, %d]", inCellX, inCellZ));
        text.add("Wall: " + isGridWall(pos.getX(), pos.getZ()));

        // Show if this is a doorway position
        boolean isDoorwayX = (inCellX == 0 || inCellX == 6) && (inCellZ >= 2 && inCellZ <= 4);
        boolean isDoorwayZ = (inCellZ == 0 || inCellZ == 6) && (inCellX >= 2 && inCellX <= 4);
        if (isDoorwayX || isDoorwayZ) {
            text.add("Potential Doorway");
        }
    }

    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, Blender blender,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carver) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }
}