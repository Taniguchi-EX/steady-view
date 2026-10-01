package net.onlinepark.steadyview.mixin;

import net.onlinepark.steadyview.SteadyViewClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	/** 有効なときは、狙う位置と紛らわしいため画面中央の照準を描かない */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void steadyview$hideCrosshair(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (SteadyViewClient.isEnabled() && SteadyViewClient.config().hideCrosshair) {
			ci.cancel();
		}
	}
}
