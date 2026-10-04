package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.onlinepark.steadyview.PlayerOpacityHolder;
import net.onlinepark.steadyview.SteadyViewClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** プレイヤーの描画情報を作るとき、自分のプレイヤーなら設定の不透明度を入れる（他のプレイヤーは通常どおり） */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void steadyview$setOpacity(final Avatar entity, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		boolean isLocalPlayer = entity == Minecraft.getInstance().player;
		int opacity = isLocalPlayer && SteadyViewClient.isEnabled() ? SteadyViewClient.config().playerOpacity : 100;
		((PlayerOpacityHolder)state).steadyview$setOpacity(opacity);
	}
}
