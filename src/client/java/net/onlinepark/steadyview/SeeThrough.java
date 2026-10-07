package net.onlinepark.steadyview;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Minecraft;
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
 * <p>マイクラ本体は、カメラとプレイヤーの間に障害物があるとカメラをプレイヤーへ寄せる。有効なときは寄せずに距離を固定する（CameraMixin）。
 * そのままでは障害物（壁・岩等）の向こうや中にある物（洞窟等）まで見えてしまうため、マイクラ本体なら寄せた位置（元のカメラの位置）と、
 * プレイヤーの目の位置（一人称視点の位置）のどちらからも見えない物は描かない。どちらも、通常のマイクラで見える位置なので、
 * 本来見えない物が見えることはない。見えるかどうかは、マスごとに{@link VisibilityGrid}で求める。
 * <ul>
 *   <li>地形: シェーダー（assets/minecraft/shaders/core/terrain.fsh。判断はassets/steadyview/shaders/include/visibility.glsl）で、
 *       手前のマスが見えない面を描かない。障害物のカメラ側の面は、手前のマスが見えないため描かれず、障害物が透けて見える</li>
 *   <li>モブ・チェスト等・パーティクル: 見えないマスにだけあるものは描かない（EntityVisibilityMixin・BlockEntityRenderDispatcherMixin・
 *       QuadParticleGroupMixin）</li>
 *   <li>見えない塊（16ブロック四方）を省く処理も、元のカメラの位置から行う（SectionOcclusionGraphMixin）</li>
 * </ul>
 * 障害物がない（カメラが寄らない）ときは、何も隠さない（通常と同じ見た目）。
 *
 * <p>シェーダーへは、全シェーダー共通のデータ（Globals）の末尾に、見えるマスの結果を足して渡す（GlobalSettingsUniformMixin）。
 * 描かれていない物をカーソルで狙えないよう、狙いの計算（CursorPicker・ItemAim）でも同じ判断をする。
 */
public final class SeeThrough {
	/** シェーダーへ渡すデータの大きさ（バイト）。範囲の角（ivec4）・元のカメラの位置（vec4）・目の位置（vec4）・見えるマスのビット列 */
	public static final int SHADER_DATA_SIZE = 16 * 3 + VisibilityGrid.WORDS * 4;

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
	 * 障害物がない、まだ求めていない、求めてから大きく動いた（テレポート等）ときはnull（何も隠さない）。
	 */
	public static VisibilityGrid.@Nullable Result hiding(final Minecraft minecraft) {
		Viewpoint current = viewpoint;
		if (current == null || !current.obstructed() || !isActive(minecraft)) {
			return null;
		}

		VisibilityGrid.Result result = VisibilityGrid.latest();
		if (result == null || result.level() != minecraft.level || !result.isCloseTo(current.original())) {
			return null;
		}

		return result;
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

	/**
	 * ブロックへの当たりを調べる（{@link Level#clip}と同じ）。ただし、画面に描かれていない面への当たりは飛ばし、その先を調べる。
	 * <ul>
	 *   <li>手前のマスが見えない面（シェーダーで描かない）</li>
	 *   <li>隣のブロックに覆われていて、もともと描かれない面（マイクラ本体が地形を作るときの判断（Block.shouldRenderFace）と同じ）。
	 *       カメラがブロックの中に入ると、周りのブロックの面がこの状態で見えなくなる</li>
	 * </ul>
	 * visibilityがnullなら{@link Level#clip}と同じ。液体は調べない（ClipContext.Fluid.NONEのときだけ使う）。
	 */
	public static BlockHitResult clip(final Level level, final ClipContext context, final VisibilityGrid.@Nullable Result visibility) {
		if (visibility == null) {
			return level.clip(context);
		}

		return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (ctx, pos) -> {
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = ctx.getBlockShape(state, level, pos);
			BlockHitResult hit = level.clipWithInteractionOverride(ctx.getFrom(), ctx.getTo(), pos, shape, state);
			if (hit == null || !visibility.isFaceVisible(hit.getLocation(), hit.getDirection())) {
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
	 * 隠さないときは先頭（範囲の角のw=0）だけ書く。シェーダーはw=0ならビット列を読まない。
	 */
	public static void writeShaderData(final Std140Builder builder) {
		VisibilityGrid.Result result = hiding(Minecraft.getInstance());
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
}
