package futurefrost.anecdote.entity;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class LibraryEntity extends PathAwareEntity implements Angerable {

    private static final UniformIntProvider ANGER_TIME_RANGE = TimeHelper.betweenSeconds(20, 39);
    private int angerTime;
    @Nullable
    private UUID angryAt;

    public LibraryEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::shouldAngerAt));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.writeAngerToNbt(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.readAngerFromNbt(this.getWorld(), nbt);
    }

    // Angerable implementation
    @Override
    public int getAngerTime() {
        return this.angerTime;
    }

    @Override
    public void setAngerTime(int angerTime) {
        this.angerTime = angerTime;
    }

    @Override
    @Nullable
    public UUID getAngryAt() {
        return this.angryAt;
    }

    @Override
    public void setAngryAt(@Nullable UUID angryAt) {
        this.angryAt = angryAt;
    }

    @Override
    public void chooseRandomAngerTime() {
        this.setAngerTime(ANGER_TIME_RANGE.get(this.random));
    }

    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ITEM_BOOK_PUT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.ITEM_BOOK_PAGE_TURN;
    }

    @Override
    protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
        super.dropLoot(damageSource, causedByPlayer);

        if (causedByPlayer) {
            // Base amount 1-3
            int paperCount = 1 + this.random.nextInt(3);

            // Get looting level
            int lootingMultiplier = 0;
            if (damageSource.getAttacker() instanceof PlayerEntity player) {
                lootingMultiplier = EnchantmentHelper.getLooting(player);
            }

            // Add looting bonus (each level gives chance for +1)
            for (int i = 0; i < lootingMultiplier; i++) {
                if (this.random.nextBoolean()) {
                    paperCount++;
                }
            }

            for (int i = 0; i < paperCount; i++) {
                ItemEntity itemEntity = getItem();

                this.getWorld().spawnEntity(itemEntity);
            }
        }
    }

    @Override
    public int getXpToDrop() {
        // Zombies drop 2-5 experience when killed by player
        if (this.getAttacker() instanceof PlayerEntity) {
            return 2 + this.random.nextInt(3);
        }
        return 0;
    }

    private @NotNull ItemEntity getItem() {
        ItemStack paper = new ItemStack(Items.PAPER);

        // Use the world's spawnEntity method directly
        ItemEntity itemEntity = new ItemEntity(
                this.getWorld(),
                this.getX(),
                this.getY() + 0.5, // Slightly above ground to prevent clipping
                this.getZ(),
                paper
        );

        // Set pickup delay to 10 ticks
        itemEntity.setPickupDelay(10);
        return itemEntity;
    }
}