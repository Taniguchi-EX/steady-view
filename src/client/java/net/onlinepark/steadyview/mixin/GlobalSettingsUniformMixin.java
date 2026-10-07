package net.onlinepark.steadyview.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
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
 * 全シェーダー共通のデータ（Globals）の末尾に、障害物を透かすときに見えるマスの結果を足す（SeeThrough参照）。
 *
 * <p>シェーダー側の宣言（assets/minecraft/shaders/include/globals.glsl）にも同じ項目を足している。末尾に足すため、既存の項目の位置は変わらない。
 * 足す大きさは約14KB。どの環境でも使える大きさ（16KB）に収まるようにしている。
 */
@Mixin(GlobalSettingsUniform.class)
public abstract class GlobalSettingsUniformMixin {
	@Shadow
	@Final
	@Mutable
	public static int UBO_SIZE;

	/** データの大きさを、足す分だけ増やす（16バイト単位にそろえてから足す） */
	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void steadyview$enlarge(final CallbackInfo ci) {
		UBO_SIZE = (UBO_SIZE + 15) / 16 * 16 + SeeThrough.SHADER_DATA_SIZE;
	}

	/** データを書き終える直前に、足した項目を書く */
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;get()Ljava/nio/ByteBuffer;"))
	private ByteBuffer steadyview$appendVisibility(final Std140Builder builder) {
		SeeThrough.writeShaderData(builder);
		return builder.get();
	}
}
