package futurefrost.anecdote.entity.client;

import futurefrost.anecdote.Anecdote;
import futurefrost.anecdote.entity.LibraryEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public class LibraryEntityRenderer extends BipedEntityRenderer<LibraryEntity, PlayerEntityModel<LibraryEntity>> {

    private static final Identifier TEXTURE = new Identifier(Anecdote.MOD_ID, "textures/entity/lesser_anecdote.png");

    public LibraryEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true), 0.5f);
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public Identifier getTexture(LibraryEntity entity) {
        return TEXTURE;
    }
}