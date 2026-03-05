package futurefrost.anecdote.world.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.stream.Stream;

public class SingleBiomeSource extends BiomeSource {
    public static final Codec<SingleBiomeSource> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Biome.REGISTRY_CODEC.fieldOf("biome").forGetter(source -> source.biome)
            ).apply(instance, instance.stable(SingleBiomeSource::new))
    );

    private final RegistryEntry<Biome> biome;

    public SingleBiomeSource(RegistryEntry<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.empty();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler sampler) {
        return biome;
    }
}