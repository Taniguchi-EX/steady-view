package net.onlinepark.steadyview;

import net.minecraft.client.player.LocalPlayer;

/**
 * プレイヤーの向きを、補間（アニメーション）なしで切り替える。
 *
 * <p>画面の端でなめらかに回る設定（SteadyViewConfig.freeAngle）のときは、向きを45度単位にそろえず、今の向きから45度ずつ変える。
 */
public final class ViewSnapper {
	private ViewSnapper() {
	}

	/** 左右にyawSteps回、上下にpitchSteps回分、45度ずつ向きを変える。 */
	public static void turn(final LocalPlayer player, final int yawSteps, final int pitchSteps) {
		if (SteadyViewClient.config().freeAngle()) {
			setRotation(player, AngleMath.turnYawFree(player.getYRot(), yawSteps), AngleMath.tiltPitchFree(player.getXRot(), pitchSteps));
			return;
		}

		setRotation(player, AngleMath.turnYaw(player.getYRot(), yawSteps), AngleMath.tiltPitch(player.getXRot(), pitchSteps));
	}

	/** 上下の向きを水平に戻す（左右の向きは45度単位にそろえるだけ。そろえない設定のときはそのまま）。 */
	public static void level(final LocalPlayer player) {
		float yaw = player.getYRot();
		setRotation(player, SteadyViewClient.config().freeAngle() ? yaw : AngleMath.snapYaw(yaw), 0.0F);
	}

	/** 向きが45度単位からずれていれば、最も近い45度単位にそろえる。そろえない設定のときは何もしない。 */
	public static void snapIfNeeded(final LocalPlayer player) {
		if (!SteadyViewClient.config().freeAngle() && !AngleMath.isSnapped(player.getYRot(), player.getXRot())) {
			setRotation(player, AngleMath.snapYaw(player.getYRot()), AngleMath.snapPitch(player.getXRot()));
		}
	}

	/** 向きを補間なしで切り替える。 */
	public static void setRotation(final LocalPlayer player, final float yaw, final float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		// 前回の値も同じにして、描画時の補間で回って見えないようにする
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.yHeadRot = yaw;
		player.yHeadRotO = yaw;
		// 手に持ったアイテムが遅れて追従する揺れもなくす
		player.yBob = yaw;
		player.yBobO = yaw;
		player.xBob = pitch;
		player.xBobO = pitch;
	}
}
