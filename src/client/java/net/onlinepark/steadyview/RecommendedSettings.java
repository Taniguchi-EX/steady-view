package net.onlinepark.steadyview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * 酔いの原因になる画面の動きを減らす、マイクラ本体のおすすめ設定。
 *
 * <p>Modを初めて入れて起動したときに1回だけ反映する。その後に本人が設定を変えた場合は、その値を優先する（毎回上書きしない）。
 */
public final class RecommendedSettings {
	private RecommendedSettings() {
	}

	/** まだ反映していなければ反映し、設定ファイルに反映済みと記録する。 */
	public static void applyOnce(final Minecraft minecraft, final SteadyViewConfig config) {
		if (config.recommendedSettingsApplied) {
			return;
		}

		apply(minecraft.options);
		minecraft.options.save();
		config.recommendedSettingsApplied = true;
		config.save();
		SteadyViewClient.LOGGER.info("Applied recommended video and accessibility settings.");
	}

	private static void apply(final Options options) {
		// 画面の揺れ（歩くときの画面の上下動）
		options.bobView().set(false);
		// 視野の変化（ダッシュや移動速度の効果で視野が変わる）
		options.fovEffectScale().set(0.0);
		// 画面の歪み（吐き気・ネザーポータルの効果）
		options.screenEffectScale().set(0.0);
		// 被ダメージ時の画面の揺れ
		options.damageTiltStrength().set(0.0);
		// 暗闇の脈動
		options.darknessEffectScale().set(0.0);
		// 空の明滅（雷の光）を表示しない
		options.hideLightningFlash().set(true);
	}
}
