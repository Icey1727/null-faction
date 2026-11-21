package com.nullfaction.entity;

import com.nullfaction.NullLeader;
import com.nullfaction.NullUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.item.equipment.trim.ArmorTrimPatterns;

import java.util.UUID;

/**
 * The Null soldier. Tier 1 = Soldier, Tier 2 = Commander (Red Flow trim), Tier 3 = Wraith (White Flow trim).
 * All runtime state (order, bounty, totems) is intentionally server-side, non-persistent: soldiers are
 * short-lived faction units and re-derive their tier from equipment after a reload.
 */
public class NullSoldierEntity extends PathAwareEntity {
    public enum Order { FOLLOW, GUARD, HOLD, ATTACK, RECALL, BUILD }

    private static final TrackedData<Integer> TIER =
            DataTracker.registerData(NullSoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public Order order = Order.FOLLOW;
    public net.minecraft.util.math.BlockPos guardPos = null;
    public UUID bounty = null;
    public int totemsLeft = 1;
    public int lastHitTick = -1000;
    public int lastCarrotTick = -1000;
    private boolean tierChecked = false;

    public NullSoldierEntity(EntityType<? extends NullSoldierEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 40.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TIER, 1);
    }

    public int getTier() { return this.dataTracker.get(TIER); }
    public void setTier(int t) { this.dataTracker.set(TIER, t); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new NullGoals.MlgClutchGoal(this));
        this.goalSelector.add(1, new NullBuilderGoal(this));
        this.goalSelector.add(2, new NullGoals.CombatGoal(this));
        this.goalSelector.add(3, new NullGoals.CobwebTrapGoal(this));
        this.goalSelector.add(3, new NullGoals.SmashGoal(this));
        this.goalSelector.add(3, new NullGoals.AerialGoal(this));
        this.goalSelector.add(4, new NullGoals.FollowLeaderGoal(this));
        this.goalSelector.add(5, new NullGoals.GuardGoal(this));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new NullGoals.TargetGoal(this));
    }

    public ServerPlayerEntity getLeader() {
        if (this.getEntityWorld().isClient()) return null;
        return NullLeader.get(NullUtil.server(this));
    }

    public boolean hasLeader() { return getLeader() != null; }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) return;
        ServerWorld world = NullUtil.world(this);

        if (!tierChecked && this.age > 1) {
            tierChecked = true;
            if (getMainHandStack().isOf(Items.MACE)) setTier(3);
            else if (getEquippedStack(EquipmentSlot.HEAD).get(DataComponentTypes.TRIM) != null) setTier(2);
            totemsLeft = getTier() >= 2 ? 2 : 1;
        }

        // No leader -> the army halts.
        if (!hasLeader()) {
            this.getNavigation().stop();
            this.setTarget(null);
            return;
        }

        if (this.age % 20 == 0) applyBuffs();

        // golden carrot snack when hurt
        if (getHealth() < getMaxHealth() * 0.5f && this.age - lastCarrotTick > 100) {
            lastCarrotTick = this.age;
            heal(6.0f);
            this.swingHand(net.minecraft.util.Hand.OFF_HAND);
            world.playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EAT.value(),
                    net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1.0f);
        }
    }

    public void applyBuffs() {
        addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, false, false));
        addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, false, false));
    }

    // --------------------------------------------------------- ghost totems

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        var attacker = source.getAttacker();
        if (attacker instanceof NullSoldierEntity) return false;
        if (attacker instanceof ServerPlayerEntity sp && NullLeader.isLeader(sp)) return false;

        boolean fatal = getHealth() - amount <= 0.0f;
        if (fatal && totemsLeft > 0 && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            int previousHit = this.lastHitTick;
            this.lastHitTick = this.age;
            GhostTotemEscape.pop(this, world, this.age - previousHit <= 10);
            return false;
        }
        this.lastHitTick = this.age;
        return super.damage(world, source, amount);
    }

    // ------------------------------------------------------------- spawning

    public static NullSoldierEntity spawn(ServerWorld world, Vec3d pos, int tier) {
        NullSoldierEntity e = new NullSoldierEntity(com.nullfaction.entity.NullEntities.NULL_SOLDIER, world);
        e.refreshPositionAndAngles(pos.x, pos.y, pos.z, world.random.nextFloat() * 360f, 0f);
        e.equipForTier(world, tier);
        e.setTier(tier);
        e.tierChecked = true;
        e.totemsLeft = tier >= 2 ? 2 : 1;
        e.setCustomName(Text.literal(NullUtil.gibberish(world.random, 8 + world.random.nextInt(5)))
                .formatted(Formatting.DARK_PURPLE, Formatting.OBFUSCATED));
        e.setCustomNameVisible(true);
        e.applyBuffs();
        world.spawnEntity(e);
        return e;
    }

    private <T> RegistryEntry<T> entry(ServerWorld w, RegistryKey<? extends net.minecraft.registry.Registry<T>> reg, RegistryKey<T> key) {
        return w.getRegistryManager().getOrThrow(reg).getOrThrow(key);
    }

    private ItemStack enchanted(ServerWorld w, ItemStack s, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            RegistryKey<Enchantment> k = (RegistryKey<Enchantment>) pairs[i];
            s.addEnchantment(entry(w, RegistryKeys.ENCHANTMENT, k), (Integer) pairs[i + 1]);
        }
        return s;
    }

    private ItemStack armor(ServerWorld w, net.minecraft.item.Item item, int tier) {
        ItemStack s = enchanted(w, new ItemStack(item),
                Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
        if (tier >= 2) {
            RegistryEntry<ArmorTrimPattern> pattern = entry(w, RegistryKeys.TRIM_PATTERN, ArmorTrimPatterns.FLOW);
            RegistryEntry<ArmorTrimMaterial> mat = entry(w, RegistryKeys.TRIM_MATERIAL,
                    tier == 2 ? ArmorTrimMaterials.REDSTONE : ArmorTrimMaterials.QUARTZ);
            s.set(DataComponentTypes.TRIM, new ArmorTrim(mat, pattern));
        }
        return s;
    }

    public void equipForTier(ServerWorld w, int tier) {
        equipStack(EquipmentSlot.HEAD, armor(w, Items.NETHERITE_HELMET, tier));
        equipStack(EquipmentSlot.CHEST, armor(w, Items.NETHERITE_CHESTPLATE, tier));
        equipStack(EquipmentSlot.LEGS, armor(w, Items.NETHERITE_LEGGINGS, tier));
        equipStack(EquipmentSlot.FEET, armor(w, Items.NETHERITE_BOOTS, tier));
        if (tier >= 3) {
            equipStack(EquipmentSlot.MAINHAND, enchanted(w, new ItemStack(Items.MACE),
                    Enchantments.DENSITY, 5, Enchantments.UNBREAKING, 3));
        } else {
            equipStack(EquipmentSlot.MAINHAND, enchanted(w, new ItemStack(Items.NETHERITE_SWORD),
                    Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.FIRE_ASPECT, 2));
        }
        // Off hand shows the (ghost) Totem of Undying; the bow/crossbow is "swapped in" via shootArrow().
        equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    }

    /** Stack used purely for arrow origin/enchant data when shooting. */
    public ItemStack rangedWeapon(ServerWorld w) {
        if (getTier() >= 2) {
            return enchanted(w, new ItemStack(Items.CROSSBOW), Enchantments.QUICK_CHARGE, 3, Enchantments.PIERCING, 4);
        }
        return enchanted(w, new ItemStack(Items.BOW), Enchantments.POWER, 5, Enchantments.INFINITY, 1);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
    }

    public static boolean isNull(net.minecraft.entity.Entity e) { return e instanceof NullSoldierEntity; }
}
