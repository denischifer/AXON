package dev.denischifer.axon.mixin;

import dev.denischifer.axon.data.SpatialGrid;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collections;

@Mixin(CollisionView.class)
public interface CollisionViewMixin {
    @Inject(method = "getBlockCollisions", at = @At("HEAD"), cancellable = true)
    default void axon$fastBlockCollision(Entity entity, Box box, CallbackInfoReturnable<Iterable<?>> cir) {
        if (SpatialGrid.isAreaEmpty(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
            cir.setReturnValue(Collections.emptyList());
            return;
        }

        if (SpatialGrid.isComplexCollision(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
            // Передаем управление ванилле для полублоков/заборов
        }
    }
}