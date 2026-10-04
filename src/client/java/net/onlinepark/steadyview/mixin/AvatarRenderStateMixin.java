package net.onlinepark.steadyview.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.onlinepark.steadyview.PlayerOpacityHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** プレイヤーの描画情報に、不透明度を持たせる */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements PlayerOpacityHolder {
	@Unique
	private int steadyview$opacity = 100;

	@Override
	public int steadyview$getOpacity() {
		return this.steadyview$opacity;
	}

	@Override
	public void steadyview$setOpacity(final int opacity) {
		this.steadyview$opacity = opacity;
	}
}
