package dev.denischifer.axon.mixin;

import dev.denischifer.axon.data.SpatialGrid;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class ChunkUnloadMixin {
    @Inject(method = "setLoadedToWorld", at = @At("HEAD"))
    private void axon$onUnload(boolean loaded, CallbackInfo ci) {
        if (!loaded) {
            WorldChunk chunk = (WorldChunk) (Object) this;
            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;
            int minY = chunk.getWorld().getBottomSectionCoord();
            int maxY = chunk.getWorld().getTopSectionCoord();

            for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                SpatialGrid.unloadChunkSection(chunkX, sectionY, chunkZ);
            }
        }
    }
}