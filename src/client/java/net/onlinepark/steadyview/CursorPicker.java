package net.onlinepark.steadyview;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * マウスカーソルの位置から、狙っているブロック・エンティティを求める。
 *
 * <p>手順はマイクラ本体の{@code LocalPlayer.pick}と同じ。違いは、視線の方向の代わりにカーソルの方向を使うことだけ。
 */
public final class CursorPicker {
	private CursorPicker() {
	}

	/**
	 * カーソルの方向で狙っている物を返す。カメラの準備ができていない等で求められないときはnull（通常の処理に任せる）。
	 */
	public static @Nullable HitResult pick(final LocalPlayer player, final Entity cameraEntity, final float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (!camera.isInitialized() || camera.entity() != cameraEntity) {
			return null;
		}

		Vec3 direction = cursorDirection(minecraft, camera);
		if (direction == null) {
			return null;
		}

		return pick(cameraEntity, camera.position(), direction, player.blockInteractionRange(), player.entityInteractionRange(), partialTicks);
	}

	/** カーソルの位置を、ワールド内の向き（長さ1）に変換する。 */
	private static @Nullable Vec3 cursorDirection(final Minecraft minecraft, final Camera camera) {
		Window window = minecraft.getWindow();
		int width = window.getScreenWidth();
		int height = window.getScreenHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}

		// 画面上の座標（左上が原点、下向きが正）を、-1〜1の座標（中央が原点、上向きが正）に直す
		float ndcX = (float)(2.0 * minecraft.mouseHandler.xpos() / width - 1.0);
		float ndcY = (float)(1.0 - 2.0 * minecraft.mouseHandler.ypos() / height);

		// 「回転×投影」の行列はカメラの位置を原点とするため、逆行列で戻した点の方向がそのまま視線の方向になる
		Matrix4f inverse = camera.getViewRotationProjectionMatrix(new Matrix4f()).invert();
		Vector4f point = inverse.transform(new Vector4f(ndcX, ndcY, 0.5F, 1.0F));
		if (Math.abs(point.w) < 1.0E-12F) {
			return null;
		}

		Vector3f direction = new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
		if (direction.lengthSquared() < 1.0E-12F) {
			return null;
		}

		direction.normalize();
		// 投影の方式によっては後ろ向きの点が返るため、カメラの正面側にそろえる
		if (direction.dot(camera.forwardVector()) < 0.0F) {
			direction.negate();
		}

		return new Vec3(direction.x, direction.y, direction.z);
	}

	private static HitResult pick(
		final Entity cameraEntity, final Vec3 from, final Vec3 direction, final double blockRange, final double entityRange, final float partialTicks
	) {
		// 届く距離はマイクラ本体と同じく目の位置から測る。三人称視点ではカメラが目から離れているため、その分だけ遠くまで調べる
		Vec3 eye = cameraEntity.getEyePosition(partialTicks);
		double maxDistance = Math.max(blockRange, entityRange) + from.distanceTo(eye);
		Vec3 to = from.add(direction.scale(maxDistance));

		BlockHitResult blockHitResult = cameraEntity.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
		double blockDistanceSq = blockHitResult.getLocation().distanceToSqr(from);
		double maxDistanceSq = maxDistance * maxDistance;
		if (blockHitResult.getType() != HitResult.Type.MISS) {
			maxDistanceSq = blockDistanceSq;
			to = blockHitResult.getLocation();
		}

		AABB box = new AABB(from, to).inflate(1.0);
		EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
			cameraEntity, from, to, box, EntitySelector.CAN_BE_PICKED.and(entity -> entity != cameraEntity), maxDistanceSq
		);
		return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(from) < blockDistanceSq
			? filterHitResult(entityHitResult, eye, entityRange)
			: filterHitResult(blockHitResult, eye, blockRange);
	}

	/** 届かない物は「何も狙っていない」扱いにする（マイクラ本体の{@code LocalPlayer.filterHitResult}と同じ）。 */
	private static HitResult filterHitResult(final HitResult hitResult, final Vec3 eye, final double maxRange) {
		Vec3 location = hitResult.getLocation();
		if (location.closerThan(eye, maxRange)) {
			return hitResult;
		}

		Direction direction = Direction.getApproximateNearest(location.x - eye.x, location.y - eye.y, location.z - eye.z);
		return BlockHitResult.miss(location, direction, BlockPos.containing(location));
	}
}
