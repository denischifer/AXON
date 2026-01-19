package dev.denischifer.axon.mixin;

import dev.denischifer.axon.data.SpatialGrid;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(WorldChunk.class)
public abstract class ChunkLoadMixin {
    @Inject(method = "loadFromPacket", at = @At("RETURN"))
    private void axon$onChunkPacketLoad(PacketByteBuf buf, NbtCompound nbt, Consumer<net.minecraft.network.packet.s2c.play.ChunkData.BlockEntityVisitor> visitor, CallbackInfo ci) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        var world = chunk.getWorld();
        if (world == null) return;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;

            int bottomY = world.getBottomY() + (s << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.isAir()) {
                            mutable.set(chunkX + x, bottomY + y, chunkZ + z);
                            var shape = state.getCollisionShape(world, mutable);
                            if (!shape.isEmpty()) {
                                boolean isFull = net.minecraft.block.Block.isShapeFullCube(shape);
                                SpatialGrid.setBlock(mutable.getX(), mutable.getY(), mutable.getZ(), isFull, !isFull);
                            }
                        }
                    }
                }
            }
        }
    }
}