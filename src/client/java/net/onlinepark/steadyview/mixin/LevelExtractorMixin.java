package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.onlinepark.steadyview.SeeThrough;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 障害物を透かすとき（SeeThrough）、元のカメラの位置からも目からも見えないマスにだけいるエンティティ（モブ・落ちているアイテム等）は描かない */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
	private void steadyview$hideUnseenEntity(
		final Entity entity, final Frustum frustum, final double camX, final double camY, final double camZ, final float partialTicks,
		final long chunkFadeDuration, final CallbackInfoReturnable<Boolean> cir
	) {
		if (SeeThrough.shouldHide(Minecraft.getInstance(), entity)) {
			cir.setReturnValue(false);
		}
	}
}
