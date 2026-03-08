package futurefrost.anecdote.condition;

import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.Registry;
import futurefrost.anecdote.Anecdote;

public class TotalXpCondition {

    public static void register() {
        Registry.register(
            ApoliRegistries.ENTITY_CONDITION,
            new Identifier(Anecdote.MOD_ID, "total_xp"),
            new ConditionFactory<>(
                new Identifier(Anecdote.MOD_ID, "total_xp"),
                new SerializableData()
                        .add("comparison", SerializableDataTypes.STRING)
                        .add("compare_to", SerializableDataTypes.INT),
                (data, player) -> {
                    if (!(player instanceof PlayerEntity)) return false;

                    int totalXp = getTotalXp((PlayerEntity) player);
                    int compareTo = data.getInt("compare_to");
                    String comparison = data.getString("comparison");

                    return switch (comparison) {
                        case "==" -> totalXp == compareTo;
                        case "!=" -> totalXp != compareTo;
                        case "<" -> totalXp < compareTo;
                        case "<=" -> totalXp <= compareTo;
                        case ">" -> totalXp > compareTo;
                        case ">=" -> totalXp >= compareTo;
                        default -> false;
                    };
                }
            )
        );
    }

    private static int getTotalXp(PlayerEntity player) {
        int level = player.experienceLevel;
        int xpForCurrentLevel = getXpForLevel(level);
        int currentProgress = MathHelper.floor(player.experienceProgress * player.getNextLevelExperience());
        return xpForCurrentLevel + currentProgress;
    }

    private static int getXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int)(2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int)(4.5 * level * level - 162.5 * level + 2220);
        }
    }
}