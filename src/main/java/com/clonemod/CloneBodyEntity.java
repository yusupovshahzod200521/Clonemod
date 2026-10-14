package com.clonemod;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public class CloneBodyEntity extends MobEntity {

    public static final DataParameter<Optional<UUID>> OWNER =
            EntityDataManager.defineId(CloneBodyEntity.class, DataSerializers.OPTIONAL_UUID);
    public static final DataParameter<Integer> INDEX =
            EntityDataManager.defineId(CloneBodyEntity.class, DataSerializers.INT);

    public CloneBodyEntity(EntityType<? extends CloneBodyEntity> type, World world) {
        super(type, world);
        this.setPersistenceRequired();
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MobEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(INDEX, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtGoal(this, PlayerEntity.class, 16.0F));
        this.goalSelector.addGoal(2, new LookRandomlyGoal(this));
    }

    public UUID getOwnerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwnerId(UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    public int getBodyIndex() {
        return this.entityData.get(INDEX);
    }

    public void setBodyIndex(int i) {
        this.entityData.set(INDEX, i);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        UUID id = getOwnerId();
        if (id != null) tag.putUUID("CloneOwner", id);
        tag.putInt("CloneIndex", getBodyIndex());
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("CloneOwner")) setOwnerId(tag.getUUID("CloneOwner"));
        setBodyIndex(tag.getInt("CloneIndex"));
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
