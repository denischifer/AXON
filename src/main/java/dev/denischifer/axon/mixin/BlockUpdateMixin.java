package dev.denischifer.axon.mixin;

import dev.denischifer.axon.data.SpatialGrid;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class BlockUpdateMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void axon$onBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            World world = (World) (Object) this;
            var shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) {
                SpatialGrid.setBlock(pos.getX(), pos.getY(), pos.getZ(), false, false);
            } else {
                boolean isFull = net.minecraft.block.Block.isShapeFullCube(shape);
                SpatialGrid.setBlock(pos.getX(), pos.getY(), pos.getZ(), isFull, !isFull);
            }
        }
    }
}