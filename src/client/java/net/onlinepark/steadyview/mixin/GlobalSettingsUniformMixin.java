package net.onlinepark.steadyview.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.SeeThrough;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 全シェーダー共通のデータ（Globals）の末尾に、障害物を透かすための1項目（vec4 SteadyViewCutout）を足す（SeeThrough参照）。
 *
 * <p>シェーダー側の宣言（assets/minecraft/shaders/include/globals.glsl）にも同じ項目を足している。末尾に足すため、既存の項目の位置は変わらない。
 */
@Mixin(GlobalSettingsUniform.class)
public abstract class GlobalSettingsUniformMixin {
	@Shadow
	@Final
	@Mutable
	public static int UBO_SIZE;

	/** データの大きさを、vec4（16バイト）1つ分増やす */
	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void steadyview$enlarge(final CallbackInfo ci) {
		UBO_SIZE = (UBO_SIZE + 15) / 16 * 16 + 16;
	}

	/** データを書き終える直前に、足した項目を書く */
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;get()Ljava/nio/ByteBuffer;"))
	private ByteBuffer steadyview$appendCutout(
		final Std140Builder builder, final int width, final int height, final double glintAlpha, final long gameTime, final float worldPartialTicks,
		final int menuBlurRadius, final Vec3 cameraPos, final boolean useRgss
	) {
		float[] cutout = SeeThrough.shaderValue(cameraPos, worldPartialTicks);
		return builder.putVec4(cutout[0], cutout[1], cutout[2], cutout[3]).get();
	}
}
