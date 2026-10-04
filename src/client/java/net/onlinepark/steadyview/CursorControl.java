package net.onlinepark.steadyview;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.onlinepark.steadyview.mixin.MouseHandlerAccessor;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/**
 * ゲーム中のマウスカーソルの扱い（SDLの呼び出し）をまとめたもの。
 *
 * <p>通常のマイクラは、ゲーム中はSDLの相対移動モード（カーソルを隠し、移動量だけを受け取るモード）にする。
 * Steady Viewが有効なときは相対移動モードにせず、カーソルを表示したままにする。
 */
public final class CursorControl {
	private CursorControl() {
	}

	/** カーソルを表示したまま、ゲーム中の状態にする。設定に応じてウィンドウ内に閉じ込める。 */
	public static void showCursorInGame(final Window window) {
		SDLMouse.SDL_SetWindowRelativeMouseMode(window.handle(), false);
		SDLVideo.SDL_SetWindowMouseGrab(window.handle(), SteadyViewClient.config().confineCursor);
	}

	/** カーソルの閉じ込めを解除する。 */
	public static void releaseConfinement(final Window window) {
		SDLVideo.SDL_SetWindowMouseGrab(window.handle(), false);
	}

	/** 通常のマイクラと同じく、カーソルを隠して中央に固定する。 */
	public static void hideCursorInGame(final Window window) {
		releaseConfinement(window);
		InputConstants.grabMouse(window, window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
	}

	/** カーソルを動かす（ウィンドウ内の座標）。MouseHandlerが持つ位置もすぐに書き換える。 */
	public static void warp(final Minecraft minecraft, final double x, final double y) {
		MouseHandlerAccessor accessor = (MouseHandlerAccessor)minecraft.mouseHandler;
		accessor.steadyview$setXpos(x);
		accessor.steadyview$setYpos(y);
		SDLMouse.SDL_WarpMouseInWindow(minecraft.getWindow().handle(), (float)x, (float)y);
	}

	/** カーソルの現在位置（ウィンドウ内の座標）を返す。 */
	public static double[] currentPosition() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer x = stack.mallocFloat(1);
			FloatBuffer y = stack.mallocFloat(1);
			SDLMouse.SDL_GetMouseState(x, y);
			return new double[]{x.get(0), y.get(0)};
		}
	}
}
