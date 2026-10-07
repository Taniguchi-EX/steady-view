package net.onlinepark.steadyview.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.onlinepark.steadyview.SeeThrough;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 障害物を透かすとき（SeeThrough）、元のカメラの位置からも目からも見えないマスにあるチェスト・看板等（ブロックエンティティ）は描かない。
 * 遠くからでも見える物（ビーコンの光等）は、そのまま描く。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void steadyview$hideUnseenBlockEntity(
		final BlockEntity blockEntity, final float partialTicks, final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
		final boolean isGloballyRendered, final CallbackInfoReturnable<BlockEntityRenderState> cir
	) {
		BlockPos pos = blockEntity.getBlockPos();
		if (!isGloballyRendered && SeeThrough.shouldHide(Minecraft.getInstance(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
			cir.setReturnValue(null);
		}
	}
}
