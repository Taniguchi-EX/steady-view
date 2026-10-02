package net.onlinepark.steadyview.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** MouseHandlerが持つカーソル位置を書き換えるためのアクセサ */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void steadyview$setXpos(double xpos);

	@Accessor("ypos")
	void steadyview$setYpos(double ypos);
}
