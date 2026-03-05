package futurefrost.anecdote;

import futurefrost.anecdote.dimension.LibraryChunkGenerator;
import futurefrost.anecdote.world.biome.SingleBiomeSource;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

public class Anecdote implements ModInitializer {
	public static final String MOD_ID = "anecdote";

	// Identifiers
	public static final Identifier LIBRARY_CHUNK_GENERATOR_ID = new Identifier(MOD_ID, "library");
	public static final Identifier SINGLE_BIOME_SOURCE_ID = new Identifier(MOD_ID, "single_biome");

	@Override
	public void onInitialize() {
		// Register chunk generator
		Registry.register(Registries.CHUNK_GENERATOR, LIBRARY_CHUNK_GENERATOR_ID, LibraryChunkGenerator.CODEC);

		// Register biome source
		Registry.register(Registries.BIOME_SOURCE, SINGLE_BIOME_SOURCE_ID, SingleBiomeSource.CODEC);

		System.out.println("Anecdote mod initialized!");
	}
}