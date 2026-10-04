package net.onlinepark.steadyview.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.onlinepark.steadyview.PlayerOpacityHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自分のプレイヤーの体を、設定の不透明度で半透明に描く。
 *
 * <p>マイクラ本体が「透明化したプレイヤーを、見える人には半透明で描く」ときと同じ描き方（entityTranslucentCull と色のアルファ）を使う。
 * 防具・手に持ったアイテム・マント等（レイヤー）は対象外で、通常どおり描く。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Shadow
	public abstract Identifier getTextureLocation(LivingEntityRenderState state);

	/** 半透明で描ける描き方にする（体が見える状態のときだけ。透明化の効果中等は通常どおり） */
	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void steadyview$useTranslucentRenderType(
		final LivingEntityRenderState state, final boolean isBodyVisible, final boolean forceTransparent, final boolean appearGlowing,
		final CallbackInfoReturnable<RenderType> cir
	) {
		if (isBodyVisible && !forceTransparent && opacity(state) < 100) {
			cir.setReturnValue(RenderTypes.entityTranslucentCull(this.getTextureLocation(state)));
		}
	}

	/** 色のアルファに不透明度を掛ける */
	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void steadyview$applyOpacity(final LivingEntityRenderState state, final CallbackInfoReturnable<Integer> cir) {
		int opacity = opacity(state);
		if (opacity < 100) {
			cir.setReturnValue(ARGB.multiply(cir.getReturnValue(), ARGB.color(Math.round(opacity * 255 / 100.0F), 255, 255, 255)));
		}
	}

	private static int opacity(final LivingEntityRenderState state) {
		return state instanceof PlayerOpacityHolder holder ? holder.steadyview$getOpacity() : 100;
	}
}
