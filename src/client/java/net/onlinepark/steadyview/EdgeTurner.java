package net.onlinepark.steadyview;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * カーソルを画面の端まで動かしたとき、その方向に視点を45度切り替える（設定のedgeTurnModeがSTEPのとき）。
 *
 * <p>切り替えた後は、切り替える前にカーソルが指していた方向（ワールド内の向き）が新しい視点で映る位置へ、カーソルを動かす。
 * これにより、狙っていた物を指したまま視点だけが変わる。
 */
public final class EdgeTurner {
	/** 端とみなす範囲（画面の端からの距離。ウィンドウの座標） */
	private static final double EDGE = 2.0;
	/** 切り替えた後のカーソルを、端からこれ以上離す（続けて切り替わらないようにするため） */
	private static final double MARGIN = 16.0;
	/** 1回切り替えた後、次に切り替えられるようになるまでのtick数（0.25秒） */
	private static final int COOLDOWN_TICKS = 5;

	private static int cooldown;
	/** 前回のtickにカーソルがあった端（-1: 左・上、1: 右・下、0: 端ではない） */
	private static int lastYawEdge;
	private static int lastPitchEdge;

	private EdgeTurner() {
	}

	/**
	 * 毎tick呼ぶ。カーソルが画面の端に来たら視点を切り替え、切り替えたらtrueを返す。
	 *
	 * <p>切り替えるのは端に「来た」ときだけで、端に置いたままのときは切り替えない。
	 * 例えば、真上を向いて上端に置いたまま「水平に戻す」を押しても、すぐに上を向き直さない。
	 */
	public static boolean tick(final Minecraft minecraft, final LocalPlayer player) {
		if (!SteadyViewClient.config().turnAtScreenEdge
			|| SteadyViewClient.config().edgeTurnMode != EdgeTurnMode.STEP
			|| minecraft.gui.screen() != null
			|| !minecraft.mouseHandler.isMouseGrabbed()
			|| !minecraft.isWindowActive()) {
			lastYawEdge = 0;
			lastPitchEdge = 0;
			return false;
		}

		Window window = minecraft.getWindow();
		double width = window.getScreenWidth();
		double height = window.getScreenHeight();
		if (width <= MARGIN * 2 || height <= MARGIN * 2) {
			return false;
		}

		double x = minecraft.mouseHandler.xpos();
		double y = minecraft.mouseHandler.ypos();
		int yawEdge = x <= EDGE ? -1 : x >= width - EDGE ? 1 : 0;
		int pitchEdge = y <= EDGE ? -1 : y >= height - EDGE ? 1 : 0;
		boolean yawArrived = yawEdge != lastYawEdge;
		boolean pitchArrived = pitchEdge != lastPitchEdge;
		lastYawEdge = yawEdge;
		lastPitchEdge = pitchEdge;

		if (cooldown > 0) {
			cooldown--;
			return false;
		}

		int yawSteps = yawArrived ? yawEdge : 0;
		int pitchSteps = pitchArrived ? pitchEdge : 0;
		// 真上・真下を向いているときは、それ以上上下には回らない
		if (pitchSteps < 0 && player.getXRot() <= AngleMath.MIN_PITCH || pitchSteps > 0 && player.getXRot() >= AngleMath.MAX_PITCH) {
			pitchSteps = 0;
		}

		if (yawSteps == 0 && pitchSteps == 0) {
			return false;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 direction = camera.isInitialized() ? CursorPicker.cursorDirection(minecraft, camera) : null;
		float oldYaw = player.getYRot();
		float oldPitch = player.getXRot();
		ViewSnapper.turn(player, yawSteps, pitchSteps);

		double[] position = direction == null
			? null
			: projectAfterTurn(minecraft, camera, direction, player.getYRot() - oldYaw, player.getXRot() - oldPitch);
		if (position == null) {
			// 求められなかったときは、端から離すだけにする
			position = new double[]{x, y};
		}

		CursorControl.warp(minecraft, clamp(position[0], width), clamp(position[1], height));
		cooldown = COOLDOWN_TICKS;
		return true;
	}

	/**
	 * 視点をyawDelta・pitchDelta度変えた後に、directionの向きが画面上に映る位置を返す。画面の後ろ側になる等で求められないときはnull。
	 */
	private static double @Nullable [] projectAfterTurn(
		final Minecraft minecraft, final Camera camera, final Vec3 direction, final float yawDelta, final float pitchDelta
	) {
		// 正面から見る三人称視点（F5を2回）では、カメラの上下の向きがプレイヤーと逆になる
		boolean mirrored = minecraft.options.getCameraType().isMirrored();
		float newYaw = camera.yRot() + yawDelta;
		float newPitch = camera.xRot() + (mirrored ? -pitchDelta : pitchDelta);

		// 今の「回転×投影」の行列から回転を取り除いて投影だけにし、新しい向きの回転と組み合わせる
		Matrix4f oldView = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
		Matrix4f projection = camera.getViewRotationProjectionMatrix(new Matrix4f()).mul(oldView.invert());
		// 回転の作り方はCamera.setRotationと同じ
		Quaternionf newRotation = new Quaternionf().rotationYXZ(
			(float)Math.PI - newYaw * ((float)Math.PI / 180.0F), -newPitch * ((float)Math.PI / 180.0F), 0.0F
		);
		Matrix4f newViewProjection = projection.mul(new Matrix4f().rotation(newRotation.conjugate()));

		Vector4f clip = newViewProjection.transform(new Vector4f((float)direction.x, (float)direction.y, (float)direction.z, 1.0F));
		if (clip.w <= 1.0E-6F) {
			return null;
		}

		Window window = minecraft.getWindow();
		double screenX = (clip.x / clip.w + 1.0) / 2.0 * window.getScreenWidth();
		double screenY = (1.0 - clip.y / clip.w) / 2.0 * window.getScreenHeight();
		return new double[]{screenX, screenY};
	}

	private static double clamp(final double value, final double size) {
		return Math.max(MARGIN, Math.min(size - MARGIN, value));
	}
}
