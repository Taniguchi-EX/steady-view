package net.onlinepark.steadyview.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.SeeThrough;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 障害物を透かすとき（SeeThrough）は、見えない塊（16ブロック四方）を省く処理を、カメラの位置ではなく元のカメラの位置
 * （マイクラ本体なら障害物の手前に寄せた位置）から行う。
 *
 * <p>この処理は、カメラのいる場所から空気等を通ってたどれる塊だけを描く。カメラが岩の中や閉じた洞窟の中にあると、
 * プレイヤーの周りの塊をたどれず描かなくなるため。元のカメラの位置から見えない物はもともと描かない（SeeThrough）ので、
 * そこから省いても見た目は変わらない。全体を求め直す処理は別のスレッドで動くため、位置はSeeThroughの値（volatile）から読む。
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
	@ModifyExpressionValue(
		method = {"invalidateIfNeeded", "runPartialUpdate", "lambda$scheduleFullUpdate$0"},
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraRenderState;pos:Lnet/minecraft/world/phys/Vec3;")
	)
	private Vec3 steadyview$originalCameraPos(final Vec3 pos) {
		Vec3 origin = SeeThrough.occlusionOrigin();
		return origin != null ? origin : pos;
	}

	@ModifyExpressionValue(
		method = "lambda$scheduleFullUpdate$0",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraRenderState;blockPos:Lnet/minecraft/core/BlockPos;")
	)
	private BlockPos steadyview$originalCameraBlockPos(final BlockPos pos) {
		Vec3 origin = SeeThrough.occlusionOrigin();
		return origin != null ? BlockPos.containing(origin) : pos;
	}
}
