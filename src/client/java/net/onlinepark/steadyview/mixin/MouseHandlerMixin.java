package net.onlinepark.steadyview.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.onlinepark.steadyview.CursorControl;
import net.onlinepark.steadyview.EdgeFollower;
import net.onlinepark.steadyview.SteadyViewClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	private double xpos;
	@Shadow
	private double ypos;

	/** 有効なときは、マウスの動きで視点を動かさない。代わりに、画面の端でなめらかに回す設定なら回す（毎フレーム呼ばれる） */
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void steadyview$disableMouseLook(final double mousea, final CallbackInfo ci) {
		if (SteadyViewClient.isEnabled()) {
			EdgeFollower.frame(Minecraft.getInstance(), mousea);
			ci.cancel();
		}
	}

	/**
	 * ゲームに戻るとき（画面を閉じたとき等）、有効なら相対移動モードに入らずカーソルを表示したままにする。
	 * MouseHandler自身は「grab済み」の状態になるため、クリックは通常どおり攻撃・使用として扱われる。
	 */
	@Redirect(
		method = "grabMouse",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V")
	)
	private void steadyview$keepCursorOnGrab(final Window window, final double x, final double y) {
		if (SteadyViewClient.isEnabled()) {
			CursorControl.showCursorInGame(window);
			// 本体はカーソル位置を画面中央にしているため、実際の位置に戻す
			this.steadyview$syncPosition();
		} else {
			InputConstants.grabMouse(window, x, y);
		}
	}

	/** 画面を開くとき、有効ならカーソルを中央へ動かさず、閉じ込めだけを解除する */
	@Redirect(
		method = "releaseMouse",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V")
	)
	private void steadyview$keepCursorOnRelease(final Window window, final double x, final double y) {
		CursorControl.releaseConfinement(window);
		if (SteadyViewClient.isEnabled()) {
			this.steadyview$syncPosition();
		} else {
			InputConstants.releaseMouse(window, x, y);
		}
	}

	private void steadyview$syncPosition() {
		double[] position = CursorControl.currentPosition();
		this.xpos = position[0];
		this.ypos = position[1];
	}
}
