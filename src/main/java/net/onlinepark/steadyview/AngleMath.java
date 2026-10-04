package net.onlinepark.steadyview;

/**
 * 視点の角度を、決まった角度（step。既定45度）単位にそろえる計算。マイクラに依存しないため、単体テストで確かめる。
 *
 * <p>角度はマイクラと同じ向きで扱う。ヨー（左右）は右回りが正、ピッチ（上下）は下向きが正で、-90（真上）〜90（真下）。
 */
public final class AngleMath {
	/** 1回で回る角度の既定値 */
	public static final int DEFAULT_STEP = 45;
	/**
	 * 1回で回る角度として選べる値。90の約数に限る。
	 * どの値でも、東西南北・水平・真上・真下をちょうど向けるようにするため。
	 */
	public static final int[] ALLOWED_STEPS = {5, 6, 9, 10, 15, 18, 30, 45, 90};
	public static final float MIN_PITCH = -90.0F;
	public static final float MAX_PITCH = 90.0F;

	private AngleMath() {
	}

	/** 選べる値のうち、最も近いものを返す（設定ファイルに選べない値が書かれていた場合に使う）。同じ近さなら小さい方。 */
	public static int nearestAllowedStep(final int step) {
		int nearest = ALLOWED_STEPS[0];
		for (int allowed : ALLOWED_STEPS) {
			if (Math.abs(allowed - step) < Math.abs(nearest - step)) {
				nearest = allowed;
			}
		}

		return nearest;
	}

	/** 選べる値の中での位置（設定画面のスライダーの位置）。選べない値なら、最も近い値の位置。 */
	public static int stepIndex(final int step) {
		int nearest = nearestAllowedStep(step);
		for (int i = 0; i < ALLOWED_STEPS.length; i++) {
			if (ALLOWED_STEPS[i] == nearest) {
				return i;
			}
		}

		throw new IllegalStateException("unreachable");
	}

	/** 最も近いstep単位のヨーを返す。360度で折り返さない（補間で逆回りに回って見えるのを防ぐため）。 */
	public static float snapYaw(final float yaw, final float step) {
		return Math.round(yaw / step) * step;
	}

	/** 最も近いstep単位のピッチを、真上〜真下の範囲で返す。 */
	public static float snapPitch(final float pitch, final float step) {
		return clampPitch(Math.round(pitch / step) * step);
	}

	/** ヨーをstep単位にそろえてから、steps回分回す。正の値で右回り。 */
	public static float turnYaw(final float yaw, final int steps, final float step) {
		return snapYaw(yaw, step) + steps * step;
	}

	/** ピッチをstep単位にそろえてから、steps回分傾ける。正の値で下向き。真上・真下で止まる。 */
	public static float tiltPitch(final float pitch, final int steps, final float step) {
		return clampPitch(snapPitch(pitch, step) + steps * step);
	}

	/** 今の向きから、steps回分（stepずつ）回す。step単位にはそろえない。正の値で右回り。 */
	public static float turnYawFree(final float yaw, final int steps, final float step) {
		return yaw + steps * step;
	}

	/** 今の向きから、steps回分（stepずつ）傾ける。step単位にはそろえない。正の値で下向き。真上・真下で止まる。 */
	public static float tiltPitchFree(final float pitch, final int steps, final float step) {
		return clampPitch(pitch + steps * step);
	}

	/** ヨー・ピッチがどちらもstep単位ちょうどになっているか。わずかな誤差もずれとして扱う（そろえた値は誤差なく表せるため）。 */
	public static boolean isSnapped(final float yaw, final float pitch, final float step) {
		return yaw == snapYaw(yaw, step) && pitch == snapPitch(pitch, step);
	}

	private static float clampPitch(final float pitch) {
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
	}
}
