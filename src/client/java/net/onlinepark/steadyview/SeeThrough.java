package net.onlinepark.steadyview;

import com.mojang.blaze3d.buffers.Std140Builder;
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
 * 三人称視点で、カメラとプレイヤーの間に障害物があってもカメラを寄せず、障害物を透かして見せる（設定のseeThroughObstacles）。
 *
 * <p>マイクラ本体は、カメラとプレイヤーの間に障害物があるとカメラをプレイヤーへ寄せる。有効なときは寄せずに距離を固定し（CameraMixin）、
 * 次の2つの処理で、間にある物を透かす。
 * <ul>
 *   <li>透かす範囲（{@link Region}）: カメラからプレイヤーの目への線の近くにあり、プレイヤーより手前にある物を描かない。
 *       葉・ガラス等の向こうが見える物や、線のすぐ横にある物も透ける。地形はassets/minecraft/shaders/core/terrain.fsh、
 *       モブ・チェスト等はcore/entity.fshのシェーダーで行い、範囲の計算は共通のassets/steadyview/shaders/include/cutout.glsl</li>
 *   <li>見えない物を隠す: マイクラ本体なら寄せた位置（元のカメラの位置。障害物がなければカメラの位置と同じ）と、プレイヤーの目の位置
 *       （一人称視点の位置）のどちらからも見えない物は描かない。どちらも通常のマイクラで見える位置なので、透かした先から、
 *       壁や岩の中の洞窟等の本来見えない物が見えることはない。障害物のカメラ側の面も、どちらからも見えないため描かれない。
 *       見えるかどうかはマスごとに{@link VisibilityGrid}で求め、地形はシェーダー（assets/steadyview/shaders/include/visibility.glsl）、
 *       モブ・チェスト等・パーティクルはLevelExtractorMixin・BlockEntityRenderDispatcherMixin・QuadParticleGroupMixinで描かない。
 *       見えない塊を省く処理も元のカメラの位置から行う（SectionOcclusionGraphMixin）</li>
 * </ul>
 * 透かす範囲は、見えない物を隠す処理と必ず一緒に使う（見えるマスをまだ求めていないときは、どちらも行わない）。
 *
 * <p>シェーダーへは、全シェーダー共通のデータ（Globals）の末尾に足して渡す（GlobalSettingsUniformMixin）。
 * 描かれていない物をカーソルで狙えないよう、狙いの計算（CursorPicker・ItemAim）でも同じ判断をする。
 */
public final class SeeThrough {
	/** プレイヤーの目の手前、この距離（ブロック）より目に近い所は透かさない。シェーダーのSTEADYVIEW_KEEP_BEFORE_EYEと同じ値にする */
	public static final double KEEP_BEFORE_EYE = 0.7;
	/** 透かす範囲の半径の、プレイヤー側の倍率（カメラ側は1）。シェーダーのSTEADYVIEW_RADIUS_NEAR_EYEと同じ値にする */
	public static final double RADIUS_NEAR_EYE = 0.5;
	/**
	 * シェーダーへ渡すデータの大きさ（バイト）。透かす範囲（vec4）・見えるマスの範囲の角（ivec4）・元のカメラの位置（vec4）・
	 * 目の位置（vec4）・見えるマスのビット列
	 */
	public static final int SHADER_DATA_SIZE = 16 * 4 + VisibilityGrid.WORDS * 4;

	/** 最後のフレームでの、目と元のカメラの位置（CameraMixinが毎フレーム設定する）。透かさないときはnull */
	private static volatile @Nullable Viewpoint viewpoint;

	private SeeThrough() {
	}

	/** 三人称視点で、障害物を透かす状態か */
	public static boolean isActive(final Minecraft minecraft) {
		return SteadyViewClient.isEnabled()
			&& SteadyViewClient.config().seeThroughObstacles
			&& !minecraft.options.getCameraType().isFirstPerson()
			&& minecraft.player != null;
	}

	public static void setViewpoint(final @Nullable Viewpoint value) {
		viewpoint = value;
	}

	public static @Nullable Viewpoint viewpoint() {
		return viewpoint;
	}

	/** 見えない塊を省く処理の起点にする位置（元のカメラの位置）。透かさないときはnull（カメラの位置のまま） */
	public static @Nullable Vec3 occlusionOrigin() {
		Viewpoint current = viewpoint;
		return current == null ? null : current.original();
	}

	/**
	 * 今、見えない物を隠すか。隠すときは見えるマスの結果を返す。
	 * 透かさないとき、まだ求めていないとき、求めてから大きく動いた（テレポート等）ときはnull（何も隠さず、透かさない）。
	 */
	public static VisibilityGrid.@Nullable Result hiding(final Minecraft minecraft) {
		Viewpoint current = viewpoint;
		if (current == null || !isActive(minecraft)) {
			return null;
		}

		VisibilityGrid.Result result = VisibilityGrid.latest();
		if (result == null || result.level() != minecraft.level || !result.isCloseTo(current.original())) {
			return null;
		}

		return result;
	}

	/** 今の透かす範囲（カメラの今の位置で求める）。透かさないときはnull */
	public static @Nullable Region currentRegion(final Minecraft minecraft) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return null;
		}

		return region(minecraft, camera.position(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true), hiding(minecraft));
	}

	/** 透かす範囲。見えない物を隠さないとき（hidingがnull）は、透かした先から洞窟等が見えないよう、透かさない */
	private static @Nullable Region region(
		final Minecraft minecraft, final Vec3 cameraPos, final float partialTicks, final VisibilityGrid.@Nullable Result hiding
	) {
		LocalPlayer player = minecraft.player;
		if (hiding == null || player == null || minecraft.gameRenderer.mainCamera().entity() != player) {
			return null;
		}

		Vec3 toEye = player.getEyePosition(partialTicks).subtract(cameraPos);
		// カメラが目のすぐ近くにあるとき（距離が0に近い設定等）は、透かす物がない
		if (toEye.lengthSqr() < 1.0) {
			return null;
		}

		return new Region(cameraPos, toEye, SteadyViewClient.config().seeThroughRadius);
	}

	/** エンティティを隠すか（見えないマスにだけいる）。自分と、自分が乗っている物・自分に乗っている物はいつも描く */
	public static boolean shouldHide(final Minecraft minecraft, final Entity entity) {
		VisibilityGrid.Result result = hiding(minecraft);
		if (result == null || minecraft.player == null) {
			return false;
		}

		if (entity == minecraft.player || entity.hasIndirectPassenger(minecraft.player) || minecraft.player.hasIndirectPassenger(entity)) {
			return false;
		}

		return !result.isBoxVisible(entity.getBoundingBox());
	}

	/** ワールド内の点（ブロックエンティティ・パーティクルの位置）を隠すか */
	public static boolean shouldHide(final Minecraft minecraft, final double x, final double y, final double z) {
		VisibilityGrid.Result result = hiding(minecraft);
		return result != null && !result.isPointVisible(x, y, z);
	}

	/** カーソルで狙えないエンティティか（透かす範囲の中にいるか、見えないマスにだけいる） */
	public static boolean hidesFromCursor(final Minecraft minecraft, final @Nullable Region region, final Entity entity) {
		return region != null && region.contains(entity) || shouldHide(minecraft, entity);
	}

	/**
	 * ブロックへの当たりを調べる（{@link Level#clip}と同じ）。ただし、画面に描かれていない面への当たりは飛ばし、その先を調べる。
	 * <ul>
	 *   <li>透かす範囲の中の点（シェーダーで描かない）</li>
	 *   <li>手前のマスが見えない面（シェーダーで描かない）</li>
	 *   <li>隣のブロックに覆われていて、もともと描かれない面（マイクラ本体が地形を作るときの判断（Block.shouldRenderFace）と同じ）。
	 *       厚い壁の手前を透かすと奥のブロックの面が、カメラがブロックの中に入ると周りのブロックの面が、この状態で見えなくなる</li>
	 * </ul>
	 * visibilityがnullなら{@link Level#clip}と同じ（visibilityがnullのときは透かす範囲もない）。液体は調べない（ClipContext.Fluid.NONEのときだけ使う）。
	 */
	public static BlockHitResult clip(
		final Level level, final ClipContext context, final VisibilityGrid.@Nullable Result visibility, final @Nullable Region region
	) {
		if (visibility == null) {
			return level.clip(context);
		}

		return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = ctx.getBlockShape(state, level, pos);
			BlockHitResult hit = level.clipWithInteractionOverride(ctx.getFrom(), ctx.getTo(), pos, shape, state);
			if (hit == null || region != null && region.contains(hit.getLocation()) || !visibility.isFaceVisible(hit.getLocation(), hit.getDirection())) {
				return null;
			}

			Direction face = hit.getDirection();
			return Block.shouldRenderFace(state, level.getBlockState(pos.relative(face)), face) ? hit : null;
		}, ctx -> {
			Vec3 delta = ctx.getFrom().subtract(ctx.getTo());
			return BlockHitResult.miss(ctx.getTo(), Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(ctx.getTo()));
		});
	}

	/**
	 * シェーダーへ渡すデータ（Globalsの末尾、{@link #SHADER_DATA_SIZE}バイト）を書く。
	 * 透かさないときは、透かす範囲の半径（w）と見えるマスの範囲の角のwを0にし、ビット列は書かない（シェーダーは読まない）。
	 */
	public static void writeShaderData(final Std140Builder builder, final Vec3 cameraPos, final float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		VisibilityGrid.Result result = hiding(minecraft);
		Region region = region(minecraft, cameraPos, partialTicks, result);
		if (region == null) {
			builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
		} else {
			builder.putVec4((float)region.toEye().x, (float)region.toEye().y, (float)region.toEye().z, (float)region.radius());
		}

		if (result == null) {
			builder.putIVec4(0, 0, 0, 0);
			return;
		}

		Vec3 eye = result.eye();
		builder.putIVec4(result.minX(), result.minY(), result.minZ(), 1)
			.putVec4((float)result.viewpoint().x, (float)result.viewpoint().y, (float)result.viewpoint().z, 0.0F)
			.putVec4(eye == null ? 0.0F : (float)eye.x, eye == null ? 0.0F : (float)eye.y, eye == null ? 0.0F : (float)eye.z, eye == null ? 0.0F : 1.0F);
		int[] bits = result.bits();
		for (int i = 0; i < bits.length; i += 4) {
			builder.putIVec4(bits[i], bits[i + 1], bits[i + 2], bits[i + 3]);
		}
	}

	/**
	 * あるフレームでの、プレイヤーの目の位置（eye）と、マイクラ本体ならカメラを置く位置（original）。
	 * obstructedは、障害物のためにカメラが寄る（originalが本来の距離より目に近い）か。
	 */
	public record Viewpoint(Vec3 eye, Vec3 original, boolean obstructed) {
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
