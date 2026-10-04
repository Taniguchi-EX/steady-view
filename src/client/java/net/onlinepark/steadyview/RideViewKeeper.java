package net.onlinepark.steadyview;

import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * 乗り物（ボート・トロッコ・馬等）に乗っている間、マイクラ本体が自動で視点を変えるのを打ち消す。
 *
 * <p>マイクラ本体は、ボートが曲がるとその分だけ乗っている人の視点も回し、ボートの向きから105度以上ずれないようにする。
 * ボートに乗った瞬間にはボートの向きに視点を合わせ、トロッコ（設定によって）もカーブで視点を回す。
 * これらはいずれもtickの処理の中で行われるため、tickの終わり（描画の前）に、前のtickの終わりの向きへ戻す。
 * Mod自身による変更（キー・画面の端での切り替え）は、戻した後に行うため打ち消されない。
 *
 * <p>設定でオフにした場合は、乗り物による変更を少しずつ貯め、半分（22.5度）を超えるごとに45度ずつ視点を変える。
 * 毎tickの変更は1〜2度程度と小さく、そのまま45度単位にそろえると打ち消されてしまうため。
 * 画面の端でなめらかに回る設定（SteadyViewConfig.freeAngle）のときは、45度単位にそろえず、通常のマイクラと同じく乗り物に合わせて変える。
 */
public final class RideViewKeeper {
	private static @Nullable LocalPlayer lastPlayer;
	private static float lastYaw;
	private static float lastPitch;
	/** 設定がオフのとき、乗り物によって変わった向きのうち、まだ視点に反映していない分 */
	private static float pendingYaw;

	private RideViewKeeper() {
	}

	/**
	 * 乗り物に乗っていて、前のtickの終わりから向きが変わっていれば戻す（設定がオフなら45度単位で追従させる）。
	 * tickの終わり、Modによる向きの変更の前に呼ぶ。
	 */
	public static void restoreIfChanged(final LocalPlayer player) {
		if (player != lastPlayer || !player.isPassenger()) {
			pendingYaw = 0.0F;
			return;
		}

		float yawDelta = player.getYRot() - lastYaw;
		if (yawDelta == 0.0F && player.getXRot() == lastPitch) {
			return;
		}

		if (SteadyViewClient.config().keepViewWhileRiding) {
			pendingYaw = 0.0F;
			ViewSnapper.setRotation(player, lastYaw, lastPitch);
			return;
		}

		if (SteadyViewClient.config().freeAngle()) {
			pendingYaw = 0.0F;
			return;
		}

		pendingYaw += yawDelta;
		int steps = Math.round(pendingYaw / AngleMath.STEP);
		pendingYaw -= steps * AngleMath.STEP;
		ViewSnapper.setRotation(player, lastYaw + steps * AngleMath.STEP, AngleMath.snapPitch(player.getXRot()));
	}

	/** 今の向きを覚える。tickの終わり、Modによる向きの変更の後に呼ぶ。 */
	public static void record(final LocalPlayer player) {
		lastPlayer = player;
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
	}

	/** 覚えた向きを捨てる（Modが無効な間等）。次に有効になったとき、古い向きに戻さないようにするため。 */
	public static void reset() {
		lastPlayer = null;
		pendingYaw = 0.0F;
	}
}
