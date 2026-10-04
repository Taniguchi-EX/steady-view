package net.onlinepark.steadyview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Steady Viewの設定画面。マイクラ本体の設定画面（マウス設定等）と同じ部品・見た目で作る。
 *
 * <p>Mod Menuの「Mod」一覧の設定ボタン、またはキー（既定F7）で開く。変更は画面を閉じたときに設定ファイルへ保存する。
 */
public class SteadyViewConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("steadyview.config.title");

	private final SteadyViewConfig config;
	private final OptionInstance<Integer> stepAngle;
	private final OptionInstance<Integer> playerOpacity;

	public SteadyViewConfigScreen(final Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, TITLE);
		this.config = SteadyViewClient.config();
		// スライダーは選べる角度（90の約数）の位置で動かし、表示と保存は角度で行う
		this.stepAngle = new OptionInstance<>(
			"steadyview.config.stepAngle",
			OptionInstance.cachedConstantTooltip(Component.translatable("steadyview.config.stepAngle.tooltip")),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("steadyview.config.degrees", value)),
			new OptionInstance.IntRange(0, AngleMath.ALLOWED_STEPS.length - 1).xmap(index -> AngleMath.ALLOWED_STEPS[index], AngleMath::stepIndex, true),
			this.config.stepAngle,
			value -> this.config.stepAngle = value
		);
		// 5%刻み
		this.playerOpacity = new OptionInstance<>(
			"steadyview.config.playerOpacity",
			OptionInstance.cachedConstantTooltip(Component.translatable("steadyview.config.playerOpacity.tooltip")),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("steadyview.config.percent", value)),
			new OptionInstance.IntRange(0, 20).xmap(index -> index * 5, value -> Math.round(value / 5.0F), true),
			this.config.playerOpacity,
			value -> this.config.playerOpacity = value
		);
	}

	/** 1回で回る角度のスライダー（ゲーム内テストで、位置を調べてクリックするため公開する） */
	public @Nullable AbstractWidget stepAngleWidget() {
		return this.list == null ? null : this.list.findOption(this.stepAngle);
	}

	/** 自分のプレイヤーの不透明度のスライダー（ゲーム内テスト用） */
	public @Nullable AbstractWidget playerOpacityWidget() {
		return this.list == null ? null : this.list.findOption(this.playerOpacity);
	}

	@Override
	protected void addOptions() {
		this.list.addBig(this.stepAngle);
		this.list.addBig(this.playerOpacity);
	}

	@Override
	public void removed() {
		super.removed();
		this.config.save();
	}
}
