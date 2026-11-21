package com.nullfaction.client;

import com.nullfaction.NullUtil;
import com.nullfaction.entity.NullSoldierEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EquipmentModelData;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Pure void-black biped. Armor, trims (Red/White Flow) and held items render through the vanilla
 * feature renderers. Eyes are baked into the skin texture. Obfuscated nametags come from the entity's
 * custom name (set server-side), so no extra rendering is required.
 */
public class NullSoldierRenderer extends BipedEntityRenderer<NullSoldierEntity, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {
    private static final Identifier TEXTURE = NullUtil.id("textures/entity/null_soldier.png");

    public NullSoldierRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        // 1.21.5+ armor renderer takes model data for the inner/outer layers + the equipment renderer.
        this.addFeature(new ArmorFeatureRenderer<>(this,
                EquipmentModelData.mapToEntityModel(EntityModelLayers.PLAYER_EQUIPMENT, ctx.getEntityModels(), BipedEntityModel::new),
                ctx.getEquipmentRenderer()));
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public BipedEntityRenderState createRenderState() { return new BipedEntityRenderState(); }

    @Override
    public Identifier getTexture(BipedEntityRenderState state) { return TEXTURE; }
}
