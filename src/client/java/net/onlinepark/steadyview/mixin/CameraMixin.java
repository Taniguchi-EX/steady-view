package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.onlinepark.steadyview.SeeThrough;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 障害物を透かすとき（SeeThrough）の、三人称視点のカメラの扱い */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private @Nullable Level level;

	@Shadow
	public abstract BlockPos blockPosition();

	/** カメラを障害物の手前に寄せず、距離を固定する */
	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void steadyview$keepDistance(final float cameraDist, final CallbackInfoReturnable<Float> cir) {
		if (SeeThrough.isActive(Minecraft.getInstance())) {
			cir.setReturnValue(cameraDist);
		}
	}

	/**
	 * カメラがブロックの中に入ったときは、見えない塊を省く処理（smartCull）を止める。
	 * この処理はカメラのいる場所から見える塊をたどるため、カメラが閉じた場所にあると周りの塊を描かなくなる。
	 * マイクラ本体も、スペクテイターでブロックの中に入ったときは同じように止めている。
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void steadyview$disableSmartCullInsideBlock(final CameraRenderState cameraState, final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (this.level != null && SeeThrough.isActive(Minecraft.getInstance()) && this.level.getBlockState(this.blockPosition()).isSolidRender()) {
			cameraState.smartCull = false;
		}
	}
}
