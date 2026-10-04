package net.onlinepark.steadyview;

import net.minecraft.client.player.LocalPlayer;

/**
 * プレイヤーの向きを、補間（アニメーション）なしで切り替える。
 *
 * <p>1回で回る角度（step）は設定のstepAngle（既定45度）。向きはstep単位にそろえる。
 * 画面の端でなめらかに回る設定（SteadyViewConfig.freeAngle）のときは、step単位にそろえず、今の向きからstepずつ変える。
 */
public final class ViewSnapper {
	private ViewSnapper() {
	}

	/** 設定の「1回で回る角度」 */
	public static float step() {
		return SteadyViewClient.config().stepAngle;
	}

	/** 左右にyawSteps回、上下にpitchSteps回分、stepずつ向きを変える。 */
	public static void turn(final LocalPlayer player, final int yawSteps, final int pitchSteps) {
		float step = step();
		if (SteadyViewClient.config().freeAngle()) {
			setRotation(player, AngleMath.turnYawFree(player.getYRot(), yawSteps, step), AngleMath.tiltPitchFree(player.getXRot(), pitchSteps, step));
			return;
		}

		setRotation(player, AngleMath.turnYaw(player.getYRot(), yawSteps, step), AngleMath.tiltPitch(player.getXRot(), pitchSteps, step));
	}

	/** 上下の向きを水平に戻す（左右の向きはstep単位にそろえるだけ。そろえない設定のときはそのまま）。 */
	public static void level(final LocalPlayer player) {
		float yaw = player.getYRot();
		setRotation(player, SteadyViewClient.config().freeAngle() ? yaw : AngleMath.snapYaw(yaw, step()), 0.0F);
	}

	/** 向きがstep単位からずれていれば、最も近いstep単位にそろえる。そろえない設定のときは何もしない。 */
	public static void snapIfNeeded(final LocalPlayer player) {
		float step = step();
		if (!SteadyViewClient.config().freeAngle() && !AngleMath.isSnapped(player.getYRot(), player.getXRot(), step)) {
			setRotation(player, AngleMath.snapYaw(player.getYRot(), step), AngleMath.snapPitch(player.getXRot(), step));
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
