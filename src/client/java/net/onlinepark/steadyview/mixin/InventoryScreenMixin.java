package net.onlinepark.steadyview.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.onlinepark.steadyview.PlayerOpacityHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** インベントリ画面に映る自分のプレイヤーは、半透明にせず通常どおり描く */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	// 同じ名前の画面描画用のメソッドがあるため、引数と戻り値で指定する
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
		at = @At("RETURN")
	)
	private static void steadyview$keepOpaque(final LivingEntity entity, final CallbackInfoReturnable<EntityRenderState> cir) {
		if (cir.getReturnValue() instanceof PlayerOpacityHolder holder) {
			holder.steadyview$setOpacity(100);
		}
	}
}
