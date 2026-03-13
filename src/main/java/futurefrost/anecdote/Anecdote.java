package futurefrost.anecdote;

import futurefrost.anecdote.dimension.LibraryChunkGenerator;
import futurefrost.anecdote.entity.LibraryEntity;
import futurefrost.anecdote.world.biome.SingleBiomeSource;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import net.minecraft.world.Heightmap;
import org.slf4j.LoggerFactory;

import static futurefrost.anecdote.dimension.LibraryChunkGenerator.CEILING_Y;
import static futurefrost.anecdote.dimension.LibraryChunkGenerator.FLOOR_Y;

public class Anecdote implements ModInitializer {
	public static final String MOD_ID = "anecdote";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Identifiers
	public static final Identifier LIBRARY_CHUNK_GENERATOR_ID = new Identifier(MOD_ID, "library");
	public static final Identifier SINGLE_BIOME_SOURCE_ID = new Identifier(MOD_ID, "single_biome");
	public static final Identifier LIBRARY_ENTITY_ID = new Identifier(MOD_ID, "lesser_anecdote");

	// Entity Type
	public static final EntityType<LibraryEntity> LIBRARY_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			LIBRARY_ENTITY_ID,
			FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, LibraryEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.8f))
					.build()
	);

	// Entity Egg
	public static final Item LIBRARY_ENTITY_SPAWN_EGG = new SpawnEggItem(LIBRARY_ENTITY, 0xd1ccc7, 0x4d4741, new Item.Settings());

	@Override
	public void onInitialize() {
		// Register chunk generator
		Registry.register(Registries.CHUNK_GENERATOR, LIBRARY_CHUNK_GENERATOR_ID, LibraryChunkGenerator.CODEC);

		// Register biome source
		Registry.register(Registries.BIOME_SOURCE, SINGLE_BIOME_SOURCE_ID, SingleBiomeSource.CODEC);

		// Register entity attributes
		FabricDefaultAttributeRegistry.register(LIBRARY_ENTITY, LibraryEntity.createAttributes());

		// Register entity spawn restrictions
		SpawnRestriction.register(LIBRARY_ENTITY, SpawnRestriction.Location.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				(entityType, world, reason, pos, random) -> {
					// Only spawn between floor and ceiling (Y=11 to Y=42)
					if (pos.getY() < FLOOR_Y || pos.getY() > CEILING_Y) {
						return false;
					}

					// Check for dark conditions
					if (world.getLightLevel(pos) == 0) {
						return false;
					}

					// Check for solid ground.
					return world.getBlockState(pos.down()).isFullCube(world, pos.down());
				});

		// Register Lesser Anecdote spawn egg
		Registry.register(Registries.ITEM, new Identifier(MOD_ID, "lesser_anecdote_spawn_egg"), LIBRARY_ENTITY_SPAWN_EGG);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(content -> content.add(LIBRARY_ENTITY_SPAWN_EGG));

		LOGGER.info("Anecdote mod initialized!");
	}
}