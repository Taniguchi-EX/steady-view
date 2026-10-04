package net.onlinepark.steadyview.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.onlinepark.steadyview.PlayerOpacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 自分のプレイヤーが体に装備した防具・エリトラを、体と同じ不透明度で半透明に描く。
 *
 * <p>防具・エリトラはどちらもこのクラスで描かれる（防具はHumanoidArmorLayer、エリトラはWingsLayerから呼ばれる）。
 * 手に持った物は別のクラスで描かれるため、対象にならない（不透明のまま）。
 * 半透明の間は、エンチャントの光沢は描かない（光沢は不透明の描き方と組み合わさっているため）。
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
	/** 同じ名前で引数の少ないメソッド（こちらを呼ぶだけ）もあるため、実際に描く方を引数で指定する */
	private static final String RENDER_LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
		+ "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
		+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

	/** 防具の本体: 半透明で描ける描き方にする */
	@WrapOperation(
		method = RENDER_LAYERS,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
		)
	)
	private RenderType steadyview$translucentArmor(final Identifier texture, final Operation<RenderType> original, @Local(argsOnly = true) final Object state) {
		return PlayerOpacity.isTranslucent(state) ? RenderTypes.entityTranslucent(texture) : original.call(texture);
	}

	/** エンチャントされた防具の本体: 同じく半透明で描ける描き方にする（光沢はなくなる） */
	@WrapOperation(
		method = RENDER_LAYERS,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
		)
	)
	private RenderType steadyview$translucentGlintArmor(final Identifier texture, final Operation<RenderType> original, @Local(argsOnly = true) final Object state) {
		return PlayerOpacity.isTranslucent(state) ? RenderTypes.entityTranslucent(texture) : original.call(texture);
	}

	/** 防具の装飾（トリム）: 同じく半透明で描ける描き方にする */
	@WrapOperation(
		method = RENDER_LAYERS,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"
		)
	)
	private RenderType steadyview$translucentTrim(
		final Identifier texture, final boolean decal, final Operation<RenderType> original, @Local(argsOnly = true) final Object state
	) {
		return PlayerOpacity.isTranslucent(state) ? RenderTypes.entityTranslucent(texture) : original.call(texture, decal);
	}

	/** 色のアルファに不透明度を掛ける。0%なら描かない。装飾の光沢も、半透明の間は描かない */
	@WrapOperation(
		method = RENDER_LAYERS,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
		)
	)
	private void steadyview$applyOpacity(
		final OrderedSubmitNodeCollector collector, final Model<?> model, final Object state, final PoseStack poseStack, final RenderType renderType,
		final int lightCoords, final int overlayCoords, final int color, final UvMapping uvMapping, final int outlineColor, final Operation<Void> original
	) {
		int opacity = PlayerOpacity.of(state);
		if (opacity >= 100) {
			original.call(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, color, uvMapping, outlineColor);
			return;
		}

		if (opacity <= 0 || renderType == RenderTypes.trimmedArmorGlint()) {
			return;
		}

		original.call(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, PlayerOpacity.applyTo(color, opacity), uvMapping, outlineColor);
	}
}
