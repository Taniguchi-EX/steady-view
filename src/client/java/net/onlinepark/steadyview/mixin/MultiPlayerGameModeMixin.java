package net.onlinepark.steadyview.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.onlinepark.steadyview.ItemAim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** アイテムを使う・放すときに、カーソルの方向へ向ける（ItemAim参照） */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	@Final
	private ClientPacketListener connection;

	/** 右クリックでアイテムを使うとき（雪玉・エンダーパール・クロスボウの発射・釣り竿等）。向きはパケットに入ってサーバへ送られる */
	@Inject(method = "useItem", at = @At("HEAD"))
	private void steadyview$aimBeforeUse(final Player player, final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof LocalPlayer localPlayer) {
			ItemAim.begin(localPlayer);
		}
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void steadyview$restoreAfterUse(final Player player, final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof LocalPlayer localPlayer) {
			ItemAim.end(localPlayer);
		}
	}

	/** 弓・トライデント等を放すとき。サーバはその時点の向きを使うため、放す前にカーソルの方向の向きを送る */
	@Inject(method = "releaseUsingItem", at = @At("HEAD"))
	private void steadyview$aimBeforeRelease(final Player player, final CallbackInfo ci) {
		if (player instanceof LocalPlayer localPlayer && ItemAim.begin(localPlayer)) {
			this.connection.send(new ServerboundMovePlayerPacket.Rot(
				localPlayer.getYRot(), localPlayer.getXRot(), localPlayer.onGround(), localPlayer.horizontalCollision
			));
		}
	}

	@Inject(method = "releaseUsingItem", at = @At("RETURN"))
	private void steadyview$restoreAfterRelease(final Player player, final CallbackInfo ci) {
		if (player instanceof LocalPlayer localPlayer) {
			ItemAim.end(localPlayer);
		}
	}
}
