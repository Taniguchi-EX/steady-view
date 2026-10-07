package net.onlinepark.steadyview;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 三人称視点で、カメラとプレイヤーの間の障害物を透かして見せる（試作。設定のseeThroughObstaclesで有効にする）。
 *
 * <p>マイクラ本体は、カメラとプレイヤーの間に障害物があるとカメラをプレイヤーへ寄せる。有効なときは寄せずに距離を固定し（CameraMixin）、
 * 代わりに地形用のシェーダー（assets/minecraft/shaders/core/terrain.fsh）で、カメラからプレイヤーへの線の近くにあり、
 * プレイヤーより手前にある面を描かない。ブロックのカメラ側の面を描かなければ、反対側の面はもともと描かれない（カメラに背を向けている）ため、透けて見える。
 *
 * <p>シェーダーへは、全シェーダー共通のデータ（Globals）の末尾に1項目（vec4）を足して渡す（GlobalSettingsUniformMixin）。
 * xyzはカメラから見たプレイヤーの目の位置、wは消す範囲の半径（ブロック）。wが0なら何も消さない（一人称視点、無効のとき）。
 */
public final class SeeThrough {
	private SeeThrough() {
	}

	/** 三人称視点で、障害物を透かす状態か */
	public static boolean isActive(final Minecraft minecraft) {
		return SteadyViewClient.isEnabled()
			&& SteadyViewClient.config().seeThroughObstacles
			&& !minecraft.options.getCameraType().isFirstPerson()
			&& minecraft.player != null;
	}

	/** シェーダーへ渡す値（カメラから見たプレイヤーの目の位置と、消す範囲の半径） */
	public static float[] shaderValue(final Vec3 cameraPos, final float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (!isActive(minecraft) || player == null) {
			return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		// カメラの高さは、しゃがんだとき等になめらかに変わる目の高さを使っているため、カメラの位置から逆算すると食い違わない
		Vec3 eye = player.getEyePosition(partialTicks);
		Vec3 relative = eye.subtract(cameraPos);
		if (camera.entity() != player || relative.lengthSqr() < 1.0) {
			return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
		}

		return new float[]{(float)relative.x, (float)relative.y, (float)relative.z, (float)SteadyViewClient.config().seeThroughRadius};
	}
}
