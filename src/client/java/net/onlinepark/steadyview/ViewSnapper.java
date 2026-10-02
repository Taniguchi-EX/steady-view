package net.onlinepark.steadyview;

import net.minecraft.client.player.LocalPlayer;

/**
 * プレイヤーの向きを、補間（アニメーション）なしで切り替える。
 */
public final class ViewSnapper {
	private ViewSnapper() {
	}

	/** 左右にyawSteps回、上下にpitchSteps回分、45度単位で向きを変える。 */
	public static void turn(final LocalPlayer player, final int yawSteps, final int pitchSteps) {
		setRotation(player, AngleMath.turnYaw(player.getYRot(), yawSteps), AngleMath.tiltPitch(player.getXRot(), pitchSteps));
	}

	/** 上下の向きを水平に戻す（左右の向きは45度単位にそろえるだけ）。 */
	public static void level(final LocalPlayer player) {
		setRotation(player, AngleMath.snapYaw(player.getYRot()), 0.0F);
	}

	/** 向きが45度単位からずれていれば、最も近い45度単位にそろえる。 */
	public static void snapIfNeeded(final LocalPlayer player) {
		if (!AngleMath.isSnapped(player.getYRot(), player.getXRot())) {
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
