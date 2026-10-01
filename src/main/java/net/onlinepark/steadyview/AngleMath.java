package net.onlinepark.steadyview;

/**
 * 視点の角度を45度単位にそろえる計算。マイクラに依存しないため、単体テストで確かめる。
 *
 * <p>角度はマイクラと同じ向きで扱う。ヨー（左右）は右回りが正、ピッチ（上下）は下向きが正で、-90（真上）〜90（真下）。
 */
public final class AngleMath {
	public static final float STEP = 45.0F;
	public static final float MIN_PITCH = -90.0F;
	public static final float MAX_PITCH = 90.0F;
	private static final float EPSILON = 1.0E-3F;

	private AngleMath() {
	}

	/** 最も近い45度単位のヨーを返す。360度で折り返さない（補間で逆回りに回って見えるのを防ぐため）。 */
	public static float snapYaw(final float yaw) {
		return Math.round(yaw / STEP) * STEP;
	}

	/** 最も近い45度単位のピッチを、真上〜真下の範囲で返す。 */
	public static float snapPitch(final float pitch) {
		return clampPitch(Math.round(pitch / STEP) * STEP);
	}

	/** ヨーを45度単位にそろえてから、steps回分回す。正の値で右回り。 */
	public static float turnYaw(final float yaw, final int steps) {
		return snapYaw(yaw) + steps * STEP;
	}

	/** ピッチを45度単位にそろえてから、steps回分傾ける。正の値で下向き。真上・真下で止まる。 */
	public static float tiltPitch(final float pitch, final int steps) {
		return clampPitch(snapPitch(pitch) + steps * STEP);
	}

	/** ヨー・ピッチがどちらも45度単位になっているか。 */
	public static boolean isSnapped(final float yaw, final float pitch) {
		return Math.abs(yaw - snapYaw(yaw)) < EPSILON && Math.abs(pitch - snapPitch(pitch)) < EPSILON;
	}

	private static float clampPitch(final float pitch) {
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
	}
}
