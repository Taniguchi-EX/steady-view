package net.onlinepark.steadyview.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.onlinepark.steadyview.SeeThrough;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 障害物を透かすとき（SeeThrough）、元のカメラの位置からも目からも見えないマスにあるパーティクル（煙・溶岩のしずく等）は描かない */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
	@WrapOperation(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z")
	)
	private boolean steadyview$hideUnseenParticle(final Frustum frustum, final double x, final double y, final double z, final Operation<Boolean> original) {
		return original.call(frustum, x, y, z) && !SeeThrough.shouldHide(Minecraft.getInstance(), x, y, z);
	}
}
