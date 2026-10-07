package net.onlinepark.steadyview;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Steady Viewの設定画面。マイクラ本体の設定画面（マウス設定等）と同じ部品・見た目で作る。
 *
 * <p>項目は、1回で回る角度・画面の端での回り方・回る速さ（押し込んだ分だけ・一定の速さで）・自分の体の不透明度・
 * 障害物を透かすか・透かす範囲。
 * Mod Menuの「Mod」一覧の設定ボタン、またはキー（既定F7）で開く。変更は画面を閉じたときに設定ファイルへ保存する。
 */
public class SteadyViewConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("steadyview.config.title");

	private final SteadyViewConfig config;
	private final OptionInstance<Integer> stepAngle;
	private final OptionInstance<EdgeChoice> edgeTurn;
	private final OptionInstance<Integer> edgeScrollSpeed;
	private final OptionInstance<Integer> edgePushSpeed;
	private final OptionInstance<Integer> playerOpacity;
	private final OptionInstance<Boolean> seeThroughObstacles;

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
		// 「画面の端で回るか（turnAtScreenEdge）」と「回り方（edgeTurnMode）」を、1つのボタンで切り替える
		this.edgeTurn = new OptionInstance<>(
			"steadyview.config.edgeTurn",
			value -> Tooltip.create(Component.translatable("steadyview.config.edgeTurn." + value.key() + ".tooltip")),
			(caption, value) -> Component.translatable("steadyview.config.edgeTurn." + value.key()),
			new OptionInstance.Enum<>(List.of(EdgeChoice.values()), EdgeChoice.CODEC),
			EdgeChoice.of(this.config),
			value -> {
				value.applyTo(this.config);
				this.updateSpeedActive();
			}
		);
		// 10度刻みで、1秒に10〜360度
		this.edgeScrollSpeed = new OptionInstance<>(
			"steadyview.config.edgeScrollSpeed",
			OptionInstance.cachedConstantTooltip(Component.translatable("steadyview.config.edgeScrollSpeed.tooltip")),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("steadyview.config.degreesPerSecond", value)),
			new OptionInstance.IntRange(1, 36).xmap(index -> index * 10, value -> Math.max(1, Math.min(36, Math.round(value / 10.0F))), true),
			(int)Math.round(this.config.edgeScrollSpeed),
			value -> this.config.edgeScrollSpeed = value
		);
		// 10%刻みで、標準の10〜300%
		this.edgePushSpeed = new OptionInstance<>(
			"steadyview.config.edgePushSpeed",
			OptionInstance.cachedConstantTooltip(Component.translatable("steadyview.config.edgePushSpeed.tooltip")),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("steadyview.config.percent", value)),
			new OptionInstance.IntRange(1, 30).xmap(index -> index * 10, value -> Math.max(1, Math.min(30, Math.round(value / 10.0F))), true),
			this.config.edgePushSpeed,
			value -> this.config.edgePushSpeed = value
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
		this.seeThroughObstacles = OptionInstance.createBoolean(
			"steadyview.config.seeThroughObstacles",
			OptionInstance.cachedConstantTooltip(Component.translatable("steadyview.config.seeThroughObstacles.tooltip")),
			this.config.seeThroughObstacles,
			value -> this.config.seeThroughObstacles = value
		);
	}

	/** 1回で回る角度のスライダー（ゲーム内テストで、位置を調べてクリックするため公開する） */
	public @Nullable AbstractWidget stepAngleWidget() {
		return this.list == null ? null : this.list.findOption(this.stepAngle);
	}

	/** 画面の端での回り方のボタン（ゲーム内テスト用） */
	public @Nullable AbstractWidget edgeTurnWidget() {
		return this.list == null ? null : this.list.findOption(this.edgeTurn);
	}

	/** 一定の速さで回るときの速さのスライダー（ゲーム内テスト用） */
	public @Nullable AbstractWidget edgeScrollSpeedWidget() {
		return this.list == null ? null : this.list.findOption(this.edgeScrollSpeed);
	}

	/** 押し込んだ分だけ回るときの速さのスライダー（ゲーム内テスト用） */
	public @Nullable AbstractWidget edgePushSpeedWidget() {
		return this.list == null ? null : this.list.findOption(this.edgePushSpeed);
	}

	/** 自分のプレイヤーの不透明度のスライダー（ゲーム内テスト用） */
	public @Nullable AbstractWidget playerOpacityWidget() {
		return this.list == null ? null : this.list.findOption(this.playerOpacity);
	}

	@Override
	protected void addOptions() {
		this.list.addBig(this.stepAngle);
		this.list.addBig(this.edgeTurn);
		this.list.addBig(this.edgePushSpeed);
		this.list.addBig(this.edgeScrollSpeed);
		this.list.addBig(this.playerOpacity);
		this.list.addBig(this.seeThroughObstacles);
	}

	@Override
	protected void init() {
		super.init();
		this.updateSpeedActive();
	}

	/** 障害物を透かすかのボタン（ゲーム内テスト用） */
	public @Nullable AbstractWidget seeThroughObstaclesWidget() {
		return this.list == null ? null : this.list.findOption(this.seeThroughObstacles);
	}

	/** 速さのスライダーは、それぞれの回り方のときだけ使うため、それ以外では押せないようにする */
	private void updateSpeedActive() {
		EdgeChoice choice = EdgeChoice.of(this.config);
		AbstractWidget pushSpeed = this.edgePushSpeedWidget();
		if (pushSpeed != null) {
			pushSpeed.active = choice == EdgeChoice.PUSH;
		}

		AbstractWidget scrollSpeed = this.edgeScrollSpeedWidget();
		if (scrollSpeed != null) {
			scrollSpeed.active = choice == EdgeChoice.SCROLL;
		}
	}

	@Override
	public void removed() {
		super.removed();
		this.config.save();
	}

	/** 画面の端での回り方の選択肢。設定ファイルでは turnAtScreenEdge と edgeTurnMode の組み合わせで表す */
	public enum EdgeChoice {
		OFF("off"),
		STEP("step"),
		PUSH("push"),
		SCROLL("scroll");

		static final Codec<EdgeChoice> CODEC = Codec.STRING.xmap(EdgeChoice::valueOf, EdgeChoice::name);

		private final String key;

		EdgeChoice(final String key) {
			this.key = key;
		}

		String key() {
			return this.key;
		}

		static EdgeChoice of(final SteadyViewConfig config) {
			if (!config.turnAtScreenEdge) {
				return OFF;
			}

			return switch (config.edgeTurnMode) {
				case STEP -> STEP;
				case PUSH -> PUSH;
				case SCROLL -> SCROLL;
			};
		}

		void applyTo(final SteadyViewConfig config) {
			config.turnAtScreenEdge = this != OFF;
			// オフのときは、回り方は前の値のまま残す（オンに戻したときに同じ回り方になるように）
			switch (this) {
				case STEP -> config.edgeTurnMode = EdgeTurnMode.STEP;
				case PUSH -> config.edgeTurnMode = EdgeTurnMode.PUSH;
				case SCROLL -> config.edgeTurnMode = EdgeTurnMode.SCROLL;
				case OFF -> {
				}
			}
		}
	}
}
