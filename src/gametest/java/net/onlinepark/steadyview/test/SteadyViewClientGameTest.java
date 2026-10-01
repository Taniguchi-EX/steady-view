package net.onlinepark.steadyview.test;

import com.mojang.blaze3d.platform.Window;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.SteadyViewClient;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * ゲーム内での自動テスト。runClientGameTestで実行する。
 *
 * <p>テスト用のワールドはスーパーフラット（地表の草ブロックがy=-61）で、プレイヤーはy=-60に立つ。
 * 南（+Z）を向いたとき、左手側が東（+X）になる。
 */
@SuppressWarnings("UnstableApiUsage")
public class SteadyViewClientGameTest implements FabricClientGameTest {
	private static final float DELTA = 1.0E-3F;

	/** 正面（照準の先）に置くブロック */
	private static final BlockPos CENTER_BLOCK = new BlockPos(0, -59, 3);
	/** 左前に置くブロック。カーソルで狙う */
	private static final BlockPos LEFT_BLOCK = new BlockPos(2, -59, 3);
	/** 右前の地面。カーソルで狙って上にブロックを置く */
	private static final BlockPos RIGHT_GROUND = new BlockPos(-2, -61, 2);

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerConnection connection = singleplayer.getConnection();
			connection.waitForChunksRender();
			testRotation(context, singleplayer.getServer());
			testCursorPick(context, singleplayer.getServer());
		}

		// Modを入れていないサーバ（本番と同じ構成）で、カーソル方向への操作が受け付けられることを確かめる
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			testInteractionOnDedicatedServer(context, server, connection);
		}
	}

	/** 45度単位の視点変更と、マウスで視点が動かないこと */
	private static void testRotation(final ClientGameTestContext context, final TestServerContext server) {
		check(context.computeOnClient(minecraft -> SteadyViewClient.isEnabled()), "起動時に有効になっていない");
		check(context.computeOnClient(minecraft -> minecraft.mouseHandler.isMouseGrabbed()), "ゲーム中の状態（grab済み）になっていない");

		// 中途半端な向きにテレポートすると、45度単位にそろう
		server.runCommand("tp @a 0.5 -60 0.5 30 20");
		context.waitTicks(5);
		assertRotation(context, 45.0F, 0.0F, "テレポート後に45度単位へそろっていない");
		context.takeScreenshot("steadyview-01-snapped");

		pressSteadyViewKey(context, "turn_right");
		assertRotation(context, 90.0F, 0.0F, "右に45度回っていない");
		pressSteadyViewKey(context, "turn_left");
		pressSteadyViewKey(context, "turn_left");
		assertRotation(context, 0.0F, 0.0F, "左に45度ずつ回っていない");

		pressSteadyViewKey(context, "look_down");
		assertRotation(context, 0.0F, 45.0F, "45度下を向いていない");
		context.takeScreenshot("steadyview-02-look-down");
		pressSteadyViewKey(context, "look_down");
		pressSteadyViewKey(context, "look_down");
		assertRotation(context, 0.0F, 90.0F, "真下で止まっていない");
		pressSteadyViewKey(context, "look_up");
		assertRotation(context, 0.0F, 45.0F, "45度上を向いていない");
		pressSteadyViewKey(context, "level_view");
		assertRotation(context, 0.0F, 0.0F, "水平に戻っていない");

		// 補間されず、その場で切り替わる（前回の値も同じになっている）
		pressSteadyViewKey(context, "turn_right");
		check(context.computeOnClient(minecraft -> Math.abs(minecraft.player.yRotO - minecraft.player.getYRot()) < DELTA), "回転が補間されている");
		pressSteadyViewKey(context, "turn_left");

		// マウスを動かしても視点が動かない
		context.getInput().setCursorPos(100, 100);
		context.getInput().moveCursor(300, 150);
		context.waitTicks(2);
		assertRotation(context, 0.0F, 0.0F, "マウスで視点が動いた");

		// 無効にすると、通常どおりマウスで視点が動く
		pressSteadyViewKey(context, "toggle");
		check(!context.computeOnClient(minecraft -> SteadyViewClient.isEnabled()), "無効にならない");
		context.getInput().moveCursor(300, 0);
		context.waitTicks(2);
		check(context.computeOnClient(minecraft -> Math.abs(minecraft.player.getYRot()) > 1.0F), "無効にしてもマウスで視点が動かない");

		// 有効に戻すと、45度単位にそろう
		pressSteadyViewKey(context, "toggle");
		check(context.computeOnClient(minecraft -> SteadyViewClient.isEnabled()), "有効に戻らない");
		context.waitTicks(2);
		float[] rotation = context.computeOnClient(minecraft -> new float[]{minecraft.player.getYRot(), minecraft.player.getXRot()});
		check(rotation[0] % 45.0F == 0.0F && rotation[1] % 45.0F == 0.0F,
			"有効に戻したときに45度単位へそろっていない（実際: " + rotation[0] + ", " + rotation[1] + "）");
	}

	/** カーソルの位置で狙う物が決まること、照準の先の物ではないこと */
	private static void testCursorPick(final ClientGameTestContext context, final TestServerContext server) {
		setUpStage(context, server);

		// 照準（画面中央）の先は正面のブロック。カーソルは左前のブロックに合わせる
		moveCursorTo(context, Vec3.atCenterOf(LEFT_BLOCK).add(0.0, 0.0, -0.5));
		context.waitTicks(2);
		assertHitBlock(context, LEFT_BLOCK, "カーソルの先のブロックを狙っていない");
		context.takeScreenshot("steadyview-03-cursor-left-block");

		// カーソルを画面中央に置くと、正面のブロックを狙う
		Window window = context.computeOnClient(Minecraft::getWindow);
		context.getInput().setCursorPos(window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
		context.waitTicks(2);
		assertHitBlock(context, CENTER_BLOCK, "画面中央のカーソルで正面のブロックを狙っていない");

		// 何もない空にカーソルを置くと、何も狙わない
		context.getInput().setCursorPos(window.getScreenWidth() / 2.0, 5.0);
		context.waitTicks(2);
		check(context.computeOnClient(minecraft -> minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.MISS), "空を指しているのに何かを狙っている");

		// 無効にすると、カーソルの位置に関係なく照準の先（正面のブロック）を狙う
		// （カーソルの移動量が、無効にした後の視点移動に使われないよう、処理されるのを待ってから切り替える）
		moveCursorTo(context, Vec3.atCenterOf(LEFT_BLOCK).add(0.0, 0.0, -0.5));
		context.waitTicks(2);
		pressSteadyViewKey(context, "toggle");
		context.waitTicks(2);
		assertHitBlock(context, CENTER_BLOCK, "無効にしても照準の先を狙わない");
		context.takeScreenshot("steadyview-04-disabled");
		pressSteadyViewKey(context, "toggle");
		context.waitTicks(2);
	}

	/** Modなしのサーバで、カーソルの位置に対する破壊・設置・攻撃が受け付けられること */
	private static void testInteractionOnDedicatedServer(
		final ClientGameTestContext context, final TestDedicatedServerContext server, final TestDedicatedServerConnection connection
	) {
		setUpStage(context, server);
		server.runCommand("gamemode survival @a");
		server.runCommand("summon minecraft:pig 3.5 -60 1.5 {NoAI:1b,Rotation:[0f,0f]}");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:cobblestone 16");
		context.waitTicks(10);

		// 左前の土ブロックをカーソルで狙い、左クリックを押し続けて掘る（サバイバル）
		moveCursorTo(context, Vec3.atCenterOf(LEFT_BLOCK).add(0.0, 0.0, -0.5));
		context.waitTicks(2);
		assertHitBlock(context, LEFT_BLOCK, "サーバ上で、カーソルの先のブロックを狙っていない");
		context.getInput().holdKeyFor(options(o -> o.keyAttack), 60);
		connection.waitForServerboundPackets();
		check(server.computeOnServer(s -> s.overworld().getBlockState(LEFT_BLOCK).isAir()), "カーソルの先のブロックがサーバで壊れていない");
		check(server.computeOnServer(s -> s.overworld().getBlockState(CENTER_BLOCK).is(Blocks.STONE)), "照準の先のブロックが壊れた");

		// 右前の地面をカーソルで狙い、右クリックで丸石を置く
		moveCursorTo(context, Vec3.atCenterOf(RIGHT_GROUND).add(0.0, 0.5, 0.0));
		context.waitTicks(2);
		assertHitBlock(context, RIGHT_GROUND, "サーバ上で、カーソルの先の地面を狙っていない");
		context.getInput().pressKey(options(o -> o.keyUse));
		context.waitTicks(2);
		connection.waitForServerboundPackets();
		check(server.computeOnServer(s -> s.overworld().getBlockState(RIGHT_GROUND.above()).is(Blocks.COBBLESTONE)), "カーソルの先に丸石が置かれていない");
		context.takeScreenshot("steadyview-05-dedicated-after-interaction");

		// 左前のブタをカーソルで狙い、左クリックで攻撃する
		moveCursorTo(context, new Vec3(3.5, -59.6, 1.5));
		context.waitTicks(2);
		check(context.computeOnClient(minecraft -> minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Pig), "カーソルの先のブタを狙っていない");
		context.getInput().pressKey(options(o -> o.keyAttack));
		context.waitTicks(2);
		connection.waitForServerboundPackets();
		check(server.computeOnServer(s -> {
			List<? extends Pig> pigs = s.overworld().getEntities(EntityTypes.PIG, pig -> true);
			return pigs.size() == 1 && pigs.getFirst().getHealth() < pigs.getFirst().getMaxHealth();
		}), "カーソルの先のブタにダメージが入っていない");

		// サーバにもプレイヤーの向き（45度単位）が伝わっている
		pressSteadyViewKey(context, "turn_right");
		context.waitTicks(5);
		connection.waitForServerboundPackets();
		check(server.computeOnServer(s -> {
			float yaw = s.getPlayerList().getPlayers().getFirst().getYRot();
			return Math.abs(yaw - 45.0F) < DELTA;
		}), "サーバ上のプレイヤーの向きが45度になっていない");
	}

	/** 南を向いて立ち、正面・左前にブロックを置く（右前は地面のまま） */
	private static void setUpStage(final ClientGameTestContext context, final TestServerContext server) {
		server.runCommand("fill -4 -60 -1 4 -56 6 minecraft:air");
		server.runCommand("setblock " + CENTER_BLOCK.getX() + " " + CENTER_BLOCK.getY() + " " + CENTER_BLOCK.getZ() + " minecraft:stone");
		server.runCommand("setblock " + LEFT_BLOCK.getX() + " " + LEFT_BLOCK.getY() + " " + LEFT_BLOCK.getZ() + " minecraft:dirt");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.waitTicks(10);
		assertRotation(context, 0.0F, 0.0F, "南を向いていない");
	}

	/** ワールド内の点が画面上に映る位置へ、カーソルを動かす */
	private static void moveCursorTo(final ClientGameTestContext context, final Vec3 target) {
		double[] screen = context.computeOnClient(minecraft -> {
			Camera camera = minecraft.gameRenderer.mainCamera();
			Vec3 relative = target.subtract(camera.position());
			Vector4f clip = camera.getViewRotationProjectionMatrix(new Matrix4f())
				.transform(new Vector4f((float)relative.x, (float)relative.y, (float)relative.z, 1.0F));
			Window window = minecraft.getWindow();
			double x = (clip.x / clip.w + 1.0) / 2.0 * window.getScreenWidth();
			double y = (1.0 - clip.y / clip.w) / 2.0 * window.getScreenHeight();
			return new double[]{x, y};
		});
		context.getInput().setCursorPos(screen[0], screen[1]);
	}

	private static void pressSteadyViewKey(final ClientGameTestContext context, final String name) {
		String keyName = "key.steadyview." + name;
		context.getInput().pressKey(options -> Arrays.stream(options.keyMappings)
			.filter(key -> key.getName().equals(keyName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("キーが登録されていない: " + keyName)));
		context.waitTicks(2);
	}

	private static Function<Options, KeyMapping> options(final Function<Options, KeyMapping> getter) {
		return getter;
	}

	private static void assertRotation(final ClientGameTestContext context, final float yaw, final float pitch, final String message) {
		float[] actual = context.computeOnClient(minecraft -> new float[]{minecraft.player.getYRot(), minecraft.player.getXRot()});
		check(Math.abs(actual[0] - yaw) < DELTA && Math.abs(actual[1] - pitch) < DELTA,
			message + "（期待: " + yaw + ", " + pitch + " / 実際: " + actual[0] + ", " + actual[1] + "）");
	}

	private static void assertHitBlock(final ClientGameTestContext context, final BlockPos expected, final String message) {
		String actual = context.computeOnClient(minecraft -> describe(minecraft.hitResult));
		check(context.computeOnClient(minecraft -> minecraft.hitResult instanceof BlockHitResult hit
			&& hit.getType() == HitResult.Type.BLOCK
			&& hit.getBlockPos().equals(expected)), message + "（期待: " + expected + " / 実際: " + actual + "）");
	}

	private static String describe(final HitResult hitResult) {
		if (hitResult instanceof BlockHitResult hit) {
			return hit.getType() + " " + hit.getBlockPos();
		}

		if (hitResult instanceof EntityHitResult hit) {
			return "ENTITY " + hit.getEntity().getType();
		}

		return String.valueOf(hitResult);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
