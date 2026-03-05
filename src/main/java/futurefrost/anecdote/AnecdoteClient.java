package futurefrost.anecdote;

import futurefrost.anecdote.entity.client.LibraryEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AnecdoteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Anecdote.LIBRARY_ENTITY, LibraryEntityRenderer::new);
    }
}