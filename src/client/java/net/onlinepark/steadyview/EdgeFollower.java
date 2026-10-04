package net.onlinepark.steadyview;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 画面の端で、視点をなめらかに回す（設定のedgeTurnModeがPUSH・SCROLLのとき）。
 *
 * <p>マイクラ本体がマウスで視点を動かす処理（MouseHandler.turnPlayer）の代わりに、描画のたび（毎フレーム）呼ぶ。
 * tickごと（1秒に20回）に回すとカクついて見えるため。
 *
 * <ul>
 *   <li>PUSH: カーソルが端の手前の線を越えたら、越えた分だけ回し、カーソルは線の上へ戻す。
 *       ウィンドウ内に閉じ込めたカーソルは端より外へは動かないため、線を端から少し内側に置き、外側へ動かした量を測れるようにしている。
 *       回る速さは設定のedgePushSpeed（標準の何%か）</li>
 *   <li>SCROLL: カーソルが端にある間、設定の速さで回り続ける</li>
 * </ul>
 */
public final class EdgeFollower {
	/** PUSH: カーソルを止めておく線（画面の端からの距離。ウィンドウの座標）。1フレームに測れる押し込みの量の上限にもなる */
	private static final double PUSH_LINE = 16.0;
	/** SCROLL: 端とみなす範囲（画面の端からの距離。ウィンドウの座標） */
	private static final double EDGE = 2.0;
	/** PUSH: 速さが標準（100%）のとき、1ドット押し込むごとに回る角度（度）。マイクラの既定のマウス感度で、マウスを1ドット動かしたときと同じ */
	private static final double PUSH_DEGREES_PER_DOT = 0.15;
	/** Entity.turnが受け取った値に掛ける係数。角度（度）からEntity.turnに渡す値へ直すのに使う */
	private static final double TURN_SCALE = 0.15;
	/** 1フレームの経過時間の上限（秒）。処理が止まっていた後に、大きく回らないようにする */
	private static final double MAX_FRAME_SECONDS = 0.1;

	private EdgeFollower() {
	}

	/** 毎フレーム呼ぶ。secondsは前のフレームからの経過時間（秒）。 */
	public static void frame(final Minecraft minecraft, final double seconds) {
		SteadyViewConfig config = SteadyViewClient.config();
		LocalPlayer player = minecraft.player;
		if (!config.freeAngle()
			|| player == null
			|| minecraft.gui.screen() != null
			|| !minecraft.mouseHandler.isMouseGrabbed()
			|| !minecraft.isWindowActive()) {
			return;
		}

		Window window = minecraft.getWindow();
		double width = window.getScreenWidth();
		double height = window.getScreenHeight();
		if (width <= PUSH_LINE * 2 || height <= PUSH_LINE * 2) {
			return;
		}

		boolean turned = switch (config.edgeTurnMode) {
			case PUSH -> push(minecraft, player, width, height);
			case SCROLL -> scroll(minecraft, player, config.edgeScrollSpeed * Math.min(Math.max(seconds, 0.0), MAX_FRAME_SECONDS), width, height);
			case STEP -> false;
		};

		if (turned) {
			// 乗り物に乗っている間、この向きの変更を乗り物によるものとして戻さないようにする
			RideViewKeeper.record(player);
		}
	}

	private static boolean push(final Minecraft minecraft, final LocalPlayer player, final double width, final double height) {
		double x = minecraft.mouseHandler.xpos();
		double y = minecraft.mouseHandler.ypos();
		double dx = beyondLine(x, width);
		double dy = beyondLine(y, height);
		if (dx == 0.0 && dy == 0.0) {
			return false;
		}

		CursorControl.warp(minecraft, x - dx, y - dy);
		// 速さは設定（標準の何%か）に従い、上下・左右の反転はマイクラ本体のマウスの設定に従う
		double degreesPerDot = PUSH_DEGREES_PER_DOT * SteadyViewClient.config().edgePushSpeed / 100.0;
		double xo = dx * degreesPerDot / TURN_SCALE;
		double yo = dy * degreesPerDot / TURN_SCALE;
		player.turn(minecraft.options.invertMouseX().get() ? -xo : xo, minecraft.options.invertMouseY().get() ? -yo : yo);
		return true;
	}

	/** 線を越えた量を返す。左・上へ越えたときは負、右・下へ越えたときは正、越えていなければ0。 */
	private static double beyondLine(final double position, final double size) {
		if (position < PUSH_LINE) {
			return position - PUSH_LINE;
		}

		if (position > size - PUSH_LINE) {
			return position - (size - PUSH_LINE);
		}

		return 0.0;
	}

	private static boolean scroll(final Minecraft minecraft, final LocalPlayer player, final double degrees, final double width, final double height) {
		double x = minecraft.mouseHandler.xpos();
		double y = minecraft.mouseHandler.ypos();
		int yawDirection = x <= EDGE ? -1 : x >= width - EDGE ? 1 : 0;
		int pitchDirection = y <= EDGE ? -1 : y >= height - EDGE ? 1 : 0;
		if (yawDirection == 0 && pitchDirection == 0 || degrees <= 0.0) {
			return false;
		}

		player.turn(yawDirection * degrees / TURN_SCALE, pitchDirection * degrees / TURN_SCALE);
		return true;
	}
}
