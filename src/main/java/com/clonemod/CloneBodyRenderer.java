package com.clonemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.client.renderer.entity.layers.HeldItemLayer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.util.ResourceLocation;

public class CloneBodyRenderer extends LivingRenderer<CloneBodyEntity, PlayerModel<CloneBodyEntity>> {

    private static final ResourceLocation STEVE =
            new ResourceLocation("textures/entity/steve.png");

    public CloneBodyRenderer(EntityRendererManager manager) {
        super(manager, new PlayerModel<CloneBodyEntity>(0.0F, false), 0.5F);
        this.addLayer(new BipedArmorLayer<>(this,
                new BipedModel<CloneBodyEntity>(0.5F),
                new BipedModel<CloneBodyEntity>(1.0F)));
        this.addLayer(new HeldItemLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CloneBodyEntity entity) {
        ClientPlayerEntity p = Minecraft.getInstance().player;
        if (p != null) {
            return p.getSkinTextureLocation();
        }
        return STEVE;
    }
}
