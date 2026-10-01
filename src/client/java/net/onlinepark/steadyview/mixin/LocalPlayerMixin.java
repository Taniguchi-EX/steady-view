package net.onlinepark.steadyview.mixin;

import net.onlinepark.steadyview.CursorPicker;
import net.onlinepark.steadyview.SteadyViewClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	/**
	 * 有効なときは、画面中央ではなくカーソルの方向で狙っている物を求める。
	 * 結果はMinecraft.hitResultに入り、攻撃・採掘・使用・ブロックの選択・枠線の表示に使われる。
	 */
	@Inject(method = "raycastHitResult", at = @At("HEAD"), cancellable = true)
	private void steadyview$pickAtCursor(final float a, final Entity cameraEntity, final CallbackInfoReturnable<HitResult> cir) {
		if (!SteadyViewClient.isEnabled()) {
			return;
		}

		HitResult hitResult = CursorPicker.pick((LocalPlayer)(Object)this, cameraEntity, a);
		if (hitResult != null) {
			cir.setReturnValue(hitResult);
		}
	}
}
