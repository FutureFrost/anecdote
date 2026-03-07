package futurefrost.anecdote.entity;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class LibraryEntity extends PathAwareEntity implements Angerable {

    private static final UniformIntProvider ANGER_TIME_RANGE = TimeHelper.betweenSeconds(20, 39);
    private int angerTime;
    @Nullable
    private UUID angryAt;

    private static final int ANGER_BROADCAST_RANGE = 32; // Blocks to spread anger
    private static final int MAX_ANGER_TIME = 400; // 20 seconds (400 ticks)

    // Flag to track if powers have been added
    private boolean powersAdded = false;

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

        // Use the custom BroadcastRevengeGoal instead of regular RevengeGoal
        this.targetSelector.add(1, new BroadcastRevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::shouldAngerAt));
    }

    // Custom goal that broadcasts anger to nearby entities
    private static class BroadcastRevengeGoal extends RevengeGoal {

        public BroadcastRevengeGoal(LibraryEntity entity) {
            super(entity);
        }

        @Override
        public void start() {
            super.start();

            // Get the attacker that caused this revenge
            LivingEntity attacker = this.mob.getAttacker();
            if (attacker != null && !this.mob.getWorld().isClient) {
                // Broadcast to all nearby LibraryEntities
                ((LibraryEntity)this.mob).broadcastAngerToNearby(attacker);
            }
        }
    }

    private void broadcastAngerToNearby(LivingEntity target) {
        // Get all entities within range
        var entities = this.getWorld().getEntitiesByClass(
                LibraryEntity.class,
                this.getBoundingBox().expand(ANGER_BROADCAST_RANGE),
                (nearbyEntity) -> nearbyEntity != this && nearbyEntity.isAlive()
        );

        // Make each nearby entity angry at the same target
        for (LibraryEntity nearby : entities) {
            // Set revenge target directly
            nearby.setTarget(target);

            // Set anger time (like zombie piglins do)
            nearby.setAngerTime(MAX_ANGER_TIME);

            // If they have an anger UUID system, set that too
            if (target instanceof PlayerEntity) {
                nearby.setAngryAt(target.getUuid());
            }
        }
    }

    // Optional: Add a tick-based propagation system for more reliable spread
    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient && this.age % 40 == 0) { // Check every 2 seconds
            // If this entity is angry, ensure all nearby entities are also angry
            if (this.getAngerTime() > 0 && this.getTarget() != null) {
                LivingEntity target = this.getTarget();

                var nearby = this.getWorld().getEntitiesByClass(
                        LibraryEntity.class,
                        this.getBoundingBox().expand(ANGER_BROADCAST_RANGE),
                        (e) -> e != this && e.isAlive() && e.getAngerTime() <= 0
                );

                for (LibraryEntity entity : nearby) {
                    entity.setTarget(target);
                    entity.setAngerTime(MAX_ANGER_TIME);
                    if (target instanceof PlayerEntity) {
                        entity.setAngryAt(target.getUuid());
                    }
                }
            }
        }
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        // Add powers when the entity is initialized (only on server side)
        if (!this.getWorld().isClient && !powersAdded) {
            addPowersToEntity();
            powersAdded = true;
        }

        return data;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.writeAngerToNbt(nbt);

        // Ensure powers are saved with the entity
        if (powersAdded) {
            ensurePowersInNbt(nbt);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.readAngerFromNbt(this.getWorld(), nbt);

        // When loading from NBT, check if powers exist and add them if missing
        if (!this.getWorld().isClient && !hasPowersInNbt(nbt)) {
            addPowersToNbt(nbt);
            powersAdded = true;
        }
    }

    /**
     * Adds powers directly to the entity when spawned
     */
    private void addPowersToEntity() {
        NbtCompound nbt = new NbtCompound();
        this.writeNbt(nbt);
        addPowersToNbt(nbt);
        this.readNbt(nbt);
    }

    private void addPowersToNbt(NbtCompound nbt) {
        // Create cardinal_components compound if it doesn't exist
        if (!nbt.contains("cardinal_components")) {
            nbt.put("cardinal_components", new NbtCompound());
        }
        NbtCompound cardinalComponents = nbt.getCompound("cardinal_components");

        // Create apoli:powers compound if it doesn't exist
        if (!cardinalComponents.contains("apoli:powers")) {
            cardinalComponents.put("apoli:powers", new NbtCompound());
        }
        NbtCompound apoliPowers = cardinalComponents.getCompound("apoli:powers");

        // Create Powers list
        NbtList powersList = new NbtList();

        // Helper function to add a power with empty data
        java.util.function.BiConsumer<String, NbtCompound> addPower = (type, data) -> {
            NbtCompound power = new NbtCompound();
            power.putString("Type", type);
            power.put("Data", data != null ? data : new NbtCompound());
            NbtList sources = new NbtList();
            sources.add(NbtString.of("anecdote:spawn"));
            power.put("Sources", sources);
            powersList.add(power);
        };

        // Add scale_actor with boolean data
        NbtCompound scaleActorPower = new NbtCompound();
        scaleActorPower.putString("Type", "anecdote:neutral/desecrated_codex_scale_actor");
        scaleActorPower.putBoolean("Data", true);
        NbtList scaleActorSources = new NbtList();
        scaleActorSources.add(NbtString.of("anecdote:spawn"));
        scaleActorPower.put("Sources", scaleActorSources);
        powersList.add(scaleActorPower);

        // Add action_lost
        NbtCompound actionLostPower = new NbtCompound();
        actionLostPower.putString("Type", "anecdote:neutral/desecrated_codex_action_lost");
        actionLostPower.put("Data", new NbtCompound());
        NbtList actionLostSources = new NbtList();
        actionLostSources.add(NbtString.of("anecdote:spawn"));
        actionLostPower.put("Sources", actionLostSources);
        powersList.add(actionLostPower);

        // Add health_change
        NbtCompound healthChangePower = new NbtCompound();
        healthChangePower.putString("Type", "anecdote:negative/skin_of_a_story_health_change");
        healthChangePower.put("Data", new NbtCompound());
        NbtList healthChangeSources = new NbtList();
        healthChangeSources.add(NbtString.of("anecdote:spawn"));
        healthChangePower.put("Sources", healthChangeSources);
        powersList.add(healthChangePower);

        // Add transparency_resource - Data should be an INTEGER (current value)
        NbtCompound resourcePower = new NbtCompound();
        resourcePower.putString("Type", "anecdote:neutral/desecrated_codex_transparency_resource");
        resourcePower.putInt("Data", 0); // Current resource value (0-8)
        NbtList resourceSources = new NbtList();
        resourceSources.add(NbtString.of("anecdote:spawn"));
        resourcePower.put("Sources", resourceSources);
        powersList.add(resourcePower);

        // Add all transparency powers (1-8)
        for (int i = 1; i <= 8; i++) {
            NbtCompound transparencyPower = new NbtCompound();
            transparencyPower.putString("Type", "anecdote:neutral/desecrated_codex_transparency_" + i);
            transparencyPower.put("Data", new NbtCompound());
            NbtList transparencySources = new NbtList();
            transparencySources.add(NbtString.of("anecdote:spawn"));
            transparencyPower.put("Sources", transparencySources);
            powersList.add(transparencyPower);
        }

        // Add shaking
        NbtCompound shakingPower = new NbtCompound();
        shakingPower.putString("Type", "anecdote:neutral/desecrated_codex_shaking");
        shakingPower.put("Data", new NbtCompound());
        NbtList shakingSources = new NbtList();
        shakingSources.add(NbtString.of("anecdote:spawn"));
        shakingPower.put("Sources", shakingSources);
        powersList.add(shakingPower);

        // Add the powers list to apoli:powers
        apoliPowers.put("Powers", powersList);
    }

    /**
     * Ensures powers are present in the NBT when saving
     */
    private void ensurePowersInNbt(NbtCompound nbt) {
        if (!hasPowersInNbt(nbt)) {
            addPowersToNbt(nbt);
        }
    }

    private boolean hasPowersInNbt(NbtCompound nbt) {
        if (!nbt.contains("cardinal_components", NbtCompound.COMPOUND_TYPE)) {
            return false;
        }

        NbtCompound cardinalComponents = nbt.getCompound("cardinal_components");
        if (!cardinalComponents.contains("apoli:powers", NbtCompound.COMPOUND_TYPE)) {
            return false;
        }

        NbtCompound apoliPowers = cardinalComponents.getCompound("apoli:powers");
        if (!apoliPowers.contains("Powers", NbtList.COMPOUND_TYPE)) {
            return false;
        }

        NbtList powersList = apoliPowers.getList("Powers", NbtCompound.COMPOUND_TYPE);

        // Check if we have all 9 powers
        boolean hasScaleActor = false;
        boolean hasActionLost = false;
        boolean hasHealthChange = false;
        boolean hasTransparencyResource = false;
        boolean[] hasTransparency = new boolean[9]; // indices 1-8
        boolean hasShaking = false;

        for (int i = 0; i < powersList.size(); i++) {
            NbtCompound power = powersList.getCompound(i);
            String type = power.getString("Type");

            if ("anecdote:neutral/desecrated_codex_scale_actor".equals(type)) {
                hasScaleActor = true;
            } else if ("anecdote:neutral/desecrated_codex_action_lost".equals(type)) {
                hasActionLost = true;
            } else if ("anecdote:neutral/desecrated_codex_health_change".equals(type)) {
                hasHealthChange = true;
            } else if ("anecdote:neutral/desecrated_codex_transparency_resource".equals(type)) {
                hasTransparencyResource = true;
            } else if ("anecdote:neutral/desecrated_codex_shaking".equals(type)) {
                hasShaking = true;
            } else {
                // Check for transparency powers
                for (int t = 1; t <= 8; t++) {
                    if (("anecdote:neutral/desecrated_codex_transparency_" + t).equals(type)) {
                        hasTransparency[t] = true;
                        break;
                    }
                }
            }
        }

        // Verify all are present
        boolean allTransparencyPresent = true;
        for (int t = 1; t <= 8; t++) {
            if (!hasTransparency[t]) {
                allTransparencyPresent = false;
                break;
            }
        }

        return hasScaleActor && hasActionLost && hasHealthChange &&
                hasTransparencyResource && allTransparencyPresent && hasShaking;
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

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ITEM_BOOK_PUT;
    }

    @Override
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
                ItemEntity itemEntity = createPaperItem();
                this.getWorld().spawnEntity(itemEntity);
            }
        }
    }

    @Override
    public int getXpToDrop() {
        if (this.getAttacker() instanceof PlayerEntity) {
            return 2 + this.random.nextInt(3);
        }
        return 0;
    }

    @NotNull
    private ItemEntity createPaperItem() {
        ItemStack paper = new ItemStack(Items.PAPER);
        ItemEntity itemEntity = new ItemEntity(
                this.getWorld(),
                this.getX(),
                this.getY() + 0.5,
                this.getZ(),
                paper
        );
        itemEntity.setPickupDelay(10);
        return itemEntity;
    }
}