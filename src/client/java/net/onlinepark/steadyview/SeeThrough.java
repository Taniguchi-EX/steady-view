package net.onlinepark.steadyview;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * 三人称視点で、カメラとプレイヤーの間の障害物を透かして見せる（設定のseeThroughObstacles）。
 *
 * <p>マイクラ本体は、カメラとプレイヤーの間に障害物があるとカメラをプレイヤーへ寄せる。有効なときは寄せずに距離を固定し（CameraMixin）、
 * 代わりにシェーダー（地形: assets/minecraft/shaders/core/terrain.fsh、モブ・チェスト等: core/entity.fsh。共通の処理は
 * assets/steadyview/shaders/include/cutout.glsl）で、カメラからプレイヤーへの線の近くにあり、プレイヤーより手前にある面を描かない。
 * ブロックのカメラ側の面を描かなければ、反対側の面はもともと描かれない（カメラに背を向けている）ため、透けて見える。
 *
 * <p>シェーダーへは、全シェーダー共通のデータ（Globals）の末尾に1項目（vec4）を足して渡す（GlobalSettingsUniformMixin）。
 * xyzはカメラから見たプレイヤーの目の位置、wは消す範囲の半径（ブロック）。wが0なら何も消さない（一人称視点、無効のとき）。
 *
 * <p>消えて見える物をカーソルで狙えないよう、狙いの計算（CursorPicker・ItemAim）でも同じ範囲（{@link Region}）を飛ばす。
 */
public final class SeeThrough {
	/** プレイヤーの目の手前、この距離（ブロック）より目に近い所は消さない。シェーダーのSTEADYVIEW_KEEP_BEFORE_EYEと同じ値にする */
	public static final double KEEP_BEFORE_EYE = 0.7;
	/** 消す範囲の半径の、プレイヤー側の倍率（カメラ側は1）。シェーダーのSTEADYVIEW_RADIUS_NEAR_EYEと同じ値にする */
	public static final double RADIUS_NEAR_EYE = 0.5;

	private SeeThrough() {
	}

	/** 三人称視点で、障害物を透かす状態か */
	public static boolean isActive(final Minecraft minecraft) {
		return SteadyViewClient.isEnabled()
			&& SteadyViewClient.config().seeThroughObstacles
			&& !minecraft.options.getCameraType().isFirstPerson()
			&& minecraft.player != null;
	}

	/** シェーダーへ渡す値（カメラから見たプレイヤーの目の位置と、消す範囲の半径） */
	public static float[] shaderValue(final Vec3 cameraPos, final float partialTicks) {
		Region region = region(Minecraft.getInstance(), cameraPos, partialTicks);
		if (region == null) {
			return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
		}

		return new float[]{(float)region.toEye.x, (float)region.toEye.y, (float)region.toEye.z, (float)region.radius};
	}

	/** 今の透かす範囲（カメラの今の位置で求める）。透かさないときはnull */
	public static @Nullable Region currentRegion(final Minecraft minecraft) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return null;
		}

		return region(minecraft, camera.position(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
	}

	private static @Nullable Region region(final Minecraft minecraft, final Vec3 cameraPos, final float partialTicks) {
		LocalPlayer player = minecraft.player;
		if (!isActive(minecraft) || player == null || minecraft.gameRenderer.mainCamera().entity() != player) {
			return null;
		}

		Vec3 toEye = player.getEyePosition(partialTicks).subtract(cameraPos);
		// カメラが目のすぐ近くにあるとき（障害物がなく、距離が0に近い設定等）は、消す物がない
		if (toEye.lengthSqr() < 1.0) {
			return null;
		}

		return new Region(cameraPos, toEye, SteadyViewClient.config().seeThroughRadius);
	}

	/**
	 * ブロックへの当たりを調べる（{@link Level#clip}と同じ）。ただし、画面に描かれていない面への当たりは飛ばし、その先を調べる。
	 * <ul>
	 *   <li>透かす範囲の中の点（シェーダーで描かない）</li>
	 *   <li>隣のブロックに覆われていて、もともと描かれない面（マイクラ本体が地形を作るときの判断（Block.shouldRenderFace）と同じ）。
	 *       厚い壁の手前を透かすと奥のブロックの面が、カメラがブロックの中に入ると周りのブロックの面が、この状態で見えなくなる</li>
	 * </ul>
	 * regionがnullなら{@link Level#clip}と同じ。液体は調べない（ClipContext.Fluid.NONEのときだけ使う）。
	 */
	public static BlockHitResult clip(final Level level, final ClipContext context, final @Nullable Region region) {
		if (region == null) {
			return level.clip(context);
		}

		return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = ctx.getBlockShape(state, level, pos);
			BlockHitResult hit = level.clipWithInteractionOverride(ctx.getFrom(), ctx.getTo(), pos, shape, state);
			if (hit == null || region.contains(hit.getLocation())) {
				return null;
			}

			Direction face = hit.getDirection();
			return Block.shouldRenderFace(state, level.getBlockState(pos.relative(face)), face) ? hit : null;
		}, ctx -> {
			Vec3 delta = ctx.getFrom().subtract(ctx.getTo());
			return BlockHitResult.miss(ctx.getTo(), Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(ctx.getTo()));
		});
	}

	/** 透かす範囲。カメラからプレイヤーの目への線を軸にした円すい台（カメラ側で半径radius、目の側でその半分） */
	public record Region(Vec3 camera, Vec3 toEye, double radius) {
		/** ワールド内の点が、透かす範囲（描かない範囲）の中か。シェーダーと同じ計算（境目のぼかしは含めない） */
		public boolean contains(final Vec3 point) {
			if (this.radius <= 0.0) {
				return false;
			}

			Vec3 relative = point.subtract(this.camera);
			double length2 = this.toEye.lengthSqr();
			double t = relative.dot(this.toEye) / length2;
			double stop = 1.0 - KEEP_BEFORE_EYE / Math.sqrt(length2);
			if (t <= 0.0 || t >= stop) {
				return false;
			}

			double distanceFromLine = relative.subtract(this.toEye.scale(t)).length();
			double localRadius = this.radius * (1.0 + (RADIUS_NEAR_EYE - 1.0) * t / stop);
			return distanceFromLine < localRadius;
		}

		/** エンティティが透かす範囲の中にいるか（体の中心で判断する） */
		public boolean contains(final Entity entity) {
			return this.contains(entity.getBoundingBox().getCenter());
		}
	}
}
