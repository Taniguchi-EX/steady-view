package net.onlinepark.steadyview;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class SteadyViewClient implements ClientModInitializer {
	public static final String MOD_ID = "steadyview";
	public static final Logger LOGGER = LogUtils.getLogger();

	// キーの番号はSDLのスキャンコード（26.3から入力処理がSDLになったため）
	private static final int KEY_Z = 29;
	private static final int KEY_X = 27;
	private static final int KEY_R = 21;
	private static final int KEY_V = 25;
	private static final int KEY_B = 5;
	private static final int KEY_F8 = 65;

	private static SteadyViewConfig config = new SteadyViewConfig();
	private static boolean enabled;

	private static KeyMapping toggleKey;
	private static KeyMapping turnLeftKey;
	private static KeyMapping turnRightKey;
	private static KeyMapping lookUpKey;
	private static KeyMapping lookDownKey;
	private static KeyMapping levelViewKey;

	@Override
	public void onInitializeClient() {
		config = SteadyViewConfig.load();
		enabled = config.enabledOnStartup;

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID));
		toggleKey = register("toggle", KEY_F8, category);
		turnLeftKey = register("turn_left", KEY_Z, category);
		turnRightKey = register("turn_right", KEY_X, category);
		lookUpKey = register("look_up", KEY_R, category);
		lookDownKey = register("look_down", KEY_V, category);
		levelViewKey = register("level_view", KEY_B, category);

		ClientTickEvents.END_CLIENT_TICK.register(SteadyViewClient::onEndTick);
		// マイクラ本体の設定（options.txt）は起動処理の中で読み込まれるため、起動が終わってから反映する
		ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> RecommendedSettings.applyOnce(minecraft, config));
	}

	public static SteadyViewConfig config() {
		return config;
	}

	/** Modが有効か（マウスで視点が動かず、カーソルの位置で操作する状態か） */
	public static boolean isEnabled() {
		return enabled;
	}

	private static KeyMapping register(final String name, final int key, final KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYBOARD, key, category));
	}

	private static void onEndTick(final Minecraft minecraft) {
		while (toggleKey.consumeClick()) {
			setEnabled(minecraft, !enabled);
		}

		int yawSteps = 0;
		int pitchSteps = 0;
		boolean level = false;
		while (turnLeftKey.consumeClick()) {
			yawSteps--;
		}
		while (turnRightKey.consumeClick()) {
			yawSteps++;
		}
		while (lookUpKey.consumeClick()) {
			pitchSteps--;
		}
		while (lookDownKey.consumeClick()) {
			pitchSteps++;
		}
		while (levelViewKey.consumeClick()) {
			level = true;
		}

		LocalPlayer player = minecraft.player;
		if (!enabled || player == null) {
			RideViewKeeper.reset();
			return;
		}

		// 乗り物によって自動で変わった向きを戻す（Mod自身による変更より先に行う）
		RideViewKeeper.restoreIfChanged(player);

		if (level) {
			ViewSnapper.level(player);
		}

		if (yawSteps != 0 || pitchSteps != 0) {
			ViewSnapper.turn(player, yawSteps, pitchSteps);
		}

		// カーソルが画面の端にあれば、その方向に回る
		EdgeTurner.tick(minecraft, player);

		// ワールドに入った直後やテレポートの後等、向きがずれていたらそろえる
		ViewSnapper.snapIfNeeded(player);

		RideViewKeeper.record(player);
	}

	private static void setEnabled(final Minecraft minecraft, final boolean value) {
		enabled = value;
		Window window = minecraft.getWindow();
		// ゲーム中（画面を開いていないとき）は、その場でカーソルの表示を切り替える。画面を開いているときは、閉じたときに切り替わる
		if (minecraft.mouseHandler.isMouseGrabbed()) {
			if (enabled) {
				CursorControl.showCursorInGame(window);
			} else {
				CursorControl.hideCursorInGame(window);
			}
		}

		if (minecraft.player != null) {
			if (enabled) {
				ViewSnapper.snapIfNeeded(minecraft.player);
			}

			minecraft.player.sendOverlayMessage(Component.translatable(enabled ? "message.steadyview.enabled" : "message.steadyview.disabled"));
		}
	}
}
