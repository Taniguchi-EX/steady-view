package net.onlinepark.steadyview.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** LocalPlayerが覚えている「前回サーバへ送った向き」を書き換えるためのアクセサ */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
	@Accessor("yRotLast")
	void steadyview$setYRotLast(float yRotLast);

	@Accessor("xRotLast")
	void steadyview$setXRotLast(float xRotLast);
}
