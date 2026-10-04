package net.onlinepark.steadyview.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.onlinepark.steadyview.PlayerOpacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 自分のプレイヤーのマントを、体と同じ不透明度で半透明に描く */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	/** 半透明で描ける描き方にする */
	@WrapOperation(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
		)
	)
	private RenderType steadyview$translucentCape(
		final Identifier texture, final Operation<RenderType> original, @Local(argsOnly = true) final AvatarRenderState state
	) {
		return PlayerOpacity.isTranslucent(state) ? RenderTypes.entityTranslucent(texture) : original.call(texture);
	}

	/** 色のアルファに不透明度を掛ける（色を指定できる描き方で描き直す）。0%なら描かない */
	@WrapOperation(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"
		)
	)
	private void steadyview$applyOpacity(
		final SubmitNodeCollector collector, final Model<?> model, final Object state, final PoseStack poseStack, final RenderType renderType,
		final int lightCoords, final int overlayCoords, final int outlineColor, final Operation<Void> original
	) {
		int opacity = PlayerOpacity.of(state);
		if (opacity >= 100) {
			original.call(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, outlineColor);
			return;
		}

		if (opacity > 0) {
			submitTinted(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, PlayerOpacity.applyTo(-1, opacity), outlineColor);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S> void submitTinted(
		final SubmitNodeCollector collector, final Model<?> model, final Object state, final PoseStack poseStack, final RenderType renderType,
		final int lightCoords, final int overlayCoords, final int tintedColor, final int outlineColor
	) {
		collector.submitModel((Model<? super S>)model, (S)state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, null, outlineColor);
	}
}
