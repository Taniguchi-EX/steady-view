package net.onlinepark.steadyview;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.mixin.LocalPlayerAccessor;
import org.jspecify.annotations.Nullable;

/**
 * 弓・雪玉等の飛ばすアイテムを、カーソルの方向へ飛ばす。
 *
 * <p>飛ぶ方向は、サーバがプレイヤーの向きから決める。アイテムを使う（右クリック）ときはパケットに入れた向き、
 * 弓・トライデントを放すときはその時点のサーバ上の向きが使われる。そこで、使う・放す処理の間だけプレイヤーの向きを
 * カーソルの指す点へ向け、終わったらすぐに戻す。描画の前に戻すため、画面の視点は動かない。
 * 戻した後、次のtickで本来の向きをサーバへ送り直す。
 */
public final class ItemAim {
	/** カーソルの指す点を探す距離 */
	private static final double AIM_RANGE = 128.0;

	private static float @Nullable [] savedRotation;

	private ItemAim() {
	}

	/** アイテムを使う・放す処理の前に呼ぶ。向きを変えたらtrue（処理の後にendを呼ぶ）。 */
	public static boolean begin(final LocalPlayer player) {
		float[] aim = aimRotation(Minecraft.getInstance(), player);
		if (aim == null) {
			return false;
		}

		savedRotation = new float[]{player.getYRot(), player.getXRot()};
		player.setYRot(aim[0]);
		player.setXRot(aim[1]);
		return true;
	}

	/** beginで変えた向きを戻し、次のtickで本来の向きをサーバへ送り直させる。 */
	public static void end(final LocalPlayer player) {
		if (savedRotation == null) {
			return;
		}

		player.setYRot(savedRotation[0]);
		player.setXRot(savedRotation[1]);
		savedRotation = null;
		// 前回送った向きを不明（NaN）にして、次の位置の送信で必ず向きも送らせる
		LocalPlayerAccessor accessor = (LocalPlayerAccessor)player;
		accessor.steadyview$setYRotLast(Float.NaN);
		accessor.steadyview$setXRotLast(Float.NaN);
	}

	/** カーソルの指す点へ向くためのヨー・ピッチ。向きを変えないとき（無効・設定がオフ・求められない）はnull。 */
	public static float @Nullable [] aimRotation(final Minecraft minecraft, final LocalPlayer player) {
		if (!SteadyViewClient.isEnabled() || !SteadyViewClient.config().aimItemsAtCursor || minecraft.gui.screen() != null) {
			return null;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return null;
		}

		Vec3 direction = CursorPicker.cursorDirection(minecraft, camera);
		if (direction == null) {
			return null;
		}

		Vec3 target = findTarget(player, camera.position(), direction);
		// 目の位置から、カーソルの指す点への向き（三人称視点ではカメラと目の位置が違うため、点を介して求める）
		Vec3 aim = target.subtract(player.getEyePosition());
		if (aim.lengthSqr() < 1.0E-4) {
			aim = direction;
		}

		double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		float yaw = (float)(Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90.0);
		float pitch = (float)-Math.toDegrees(Math.atan2(aim.y, horizontal));
		// 今の向きから近い側の角度にする（360度ずれた値にしない）
		yaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot());
		return new float[]{yaw, Mth.clamp(pitch, AngleMath.MIN_PITCH, AngleMath.MAX_PITCH)};
	}

	/** カーソルの方向にある一番手前のブロック・エンティティの点。何もなければ遠くの点。エンティティは中心を狙う。 */
	private static Vec3 findTarget(final LocalPlayer player, final Vec3 from, final Vec3 direction) {
		Vec3 to = from.add(direction.scale(AIM_RANGE));
		// 障害物を透かしているとき（SeeThrough）は、描かれていないブロック・エンティティを飛ばす
		Minecraft minecraft = Minecraft.getInstance();
		BlockHitResult blockHit = SeeThrough.clip(
			player.level(), new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player), SeeThrough.hiding(minecraft)
		);
		Vec3 target = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

		AABB box = new AABB(from, target).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			player, from, target, box,
			EntitySelector.CAN_BE_PICKED.and(entity -> entity != player && !SeeThrough.shouldHide(minecraft, entity)), from.distanceToSqr(target)
		);
		return entityHit != null ? entityHit.getEntity().getBoundingBox().getCenter() : target;
	}
}
