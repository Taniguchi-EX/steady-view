package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.SeeThrough;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 障害物を透かすとき（SeeThrough）の、三人称視点のカメラの扱い */
@Mixin(Camera.class)
public abstract class CameraMixin {
	/** マイクラ本体のカメラの距離を求めている間（下のgetMaxZoomの中から、元の処理を呼んでいる間）か */
	@Unique
	private static boolean steadyview$computingVanillaZoom;

	@Shadow
	private Vec3 position;

	@Shadow
	@Final
	private Vector3f forwards;

	@Shadow
	private @Nullable Entity entity;

	@Unique
	private boolean steadyview$viewpointSet;

	@Shadow
	private float getMaxZoom(final float cameraDist) {
		throw new AssertionError();
	}

	@Inject(method = "alignWithEntity", at = @At("HEAD"))
	private void steadyview$beginAlign(final float partialTicks, final CallbackInfo ci) {
		this.steadyview$viewpointSet = false;
	}

	/** 一人称視点等で、元のカメラの位置を求めなかったフレームは、透かさない */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void steadyview$endAlign(final float partialTicks, final CallbackInfo ci) {
		if (!this.steadyview$viewpointSet) {
			SeeThrough.setViewpoint(null);
		}
	}

	/**
	 * カメラを障害物の手前に寄せず、距離を固定する。
	 * その前に、マイクラ本体ならカメラを置く位置（元のカメラの位置）を求めて、SeeThroughに渡す（その位置から見える物だけを描くため）。
	 * この時点でのカメラの位置は、プレイヤーの目の位置。
	 */
	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void steadyview$keepDistance(final float cameraDist, final CallbackInfoReturnable<Float> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (steadyview$computingVanillaZoom || !SeeThrough.isActive(minecraft) || this.entity != minecraft.player) {
			return;
		}

		float vanillaDist;
		steadyview$computingVanillaZoom = true;
		try {
			vanillaDist = this.getMaxZoom(cameraDist);
		} finally {
			steadyview$computingVanillaZoom = false;
		}

		Vec3 eye = this.position;
		Vec3 original = eye.add(new Vec3(this.forwards).scale(-vanillaDist));
		SeeThrough.setViewpoint(new SeeThrough.Viewpoint(eye, original, vanillaDist < cameraDist - 1.0E-3F));
		this.steadyview$viewpointSet = true;
		cir.setReturnValue(cameraDist);
	}
}
