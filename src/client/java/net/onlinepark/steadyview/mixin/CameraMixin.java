package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.onlinepark.steadyview.SeeThrough;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 障害物を透かすとき（SeeThrough）は、三人称視点のカメラを障害物の手前へ寄せず、距離を固定する */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void steadyview$keepDistance(final float cameraDist, final CallbackInfoReturnable<Float> cir) {
		if (SeeThrough.isActive(Minecraft.getInstance())) {
			cir.setReturnValue(cameraDist);
		}
	}
}
