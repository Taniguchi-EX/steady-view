package net.onlinepark.steadyview.test;

import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onlinepark.steadyview.AngleMath;
import net.onlinepark.steadyview.CursorPicker;
import net.onlinepark.steadyview.EdgeTurnMode;
import net.onlinepark.steadyview.PlayerOpacityHolder;
import net.onlinepark.steadyview.RecommendedSettings;
import net.onlinepark.steadyview.SteadyViewClient;
import net.onlinepark.steadyview.SteadyViewConfigScreen;
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
	private static final BlockPos RIGHT_GROUND = new BlockPos(-1, -61, 3);

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerConnection connection = singleplayer.getConnection();
			connection.waitForChunksRender();
			testRotation(context, singleplayer.getServer());
			testEdgeTurn(context, singleplayer.getServer());
			testEdgeFollow(context, singleplayer.getServer());
			testStepAngleSetting(context, singleplayer.getServer());
			testPlayerOpacity(context, singleplayer.getServer());
			testKeepViewWhileRiding(context, singleplayer.getServer());
			testCursorPick(context, singleplayer.getServer());
		}

		// Modを入れていないサーバ（本番と同じ構成）で、カーソル方向への操作が受け付けられることを確かめる
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			testInteractionOnDedicatedServer(context, server, connection);
			testProjectileAim(context, server, connection);
		}
	}

	/** 45度単位の視点変更と、マウスで視点が動かないこと */
	private static void testRotation(final ClientGameTestContext context, final TestServerContext server) {
		check(context.computeOnClient(minecraft -> SteadyViewClient.isEnabled()), "起動時に有効になっていない");
		check(context.computeOnClient(minecraft -> minecraft.mouseHandler.isMouseGrabbed()), "ゲーム中の状態（grab済み）になっていない");

		testRecommendedSettings(context);

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

	/**
	 * マイクラ本体のおすすめ設定が1回だけ反映されること。
	 * テストの仕組みが各テストの開始前に設定を既定値へ戻すため、起動時の反映結果ではなく、反映の処理を直接呼んで確かめる。
	 */
	private static void testRecommendedSettings(final ClientGameTestContext context) {
		check(context.computeOnClient(minecraft -> SteadyViewClient.config().recommendedSettingsApplied), "起動時におすすめ設定を反映済みと記録されていない");

		context.runOnClient(minecraft -> {
			SteadyViewClient.config().recommendedSettingsApplied = false;
			RecommendedSettings.applyOnce(minecraft, SteadyViewClient.config());
		});
		check(context.computeOnClient(minecraft -> {
			Options options = minecraft.options;
			return !options.bobView().get()
				&& options.fovEffectScale().get() == 0.0
				&& options.screenEffectScale().get() == 0.0
				&& options.damageTiltStrength().get() == 0.0
				&& options.darknessEffectScale().get() == 0.0
				&& options.hideLightningFlash().get();
		}), "おすすめ設定が反映されていない");

		// 反映済みなら、本人が変えた設定を上書きしない
		context.runOnClient(minecraft -> {
			minecraft.options.bobView().set(true);
			RecommendedSettings.applyOnce(minecraft, SteadyViewClient.config());
		});
		check(context.computeOnClient(minecraft -> minecraft.options.bobView().get()), "反映済みなのに設定が上書きされた");
		context.restoreDefaultGameOptions();
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
		server.runCommand("summon minecraft:pig 2.5 -60 2.5 {NoAI:1b,Rotation:[0f,0f]}");
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
		moveCursorTo(context, new Vec3(2.5, -59.6, 2.5));
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
		assertRotation(context, 0.0F, 0.0F, "ブタを狙う前後で向きが変わった");
		pressSteadyViewKey(context, "turn_right");
		context.waitTicks(5);
		connection.waitForServerboundPackets();
		float serverYaw = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getYRot());
		float clientYaw = context.computeOnClient(minecraft -> minecraft.player.getYRot());
		double[] cursor = context.computeOnClient(minecraft -> new double[]{minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()});
		check(Math.abs(serverYaw - 45.0F) < DELTA,
			"サーバ上のプレイヤーの向きが45度になっていない（サーバ: " + serverYaw + " / クライアント: " + clientYaw + " / カーソル: " + cursor[0] + ", " + cursor[1] + "）");
	}

	/** Modなしのサーバで、雪玉・弓矢がカーソルの方向へ飛ぶこと。画面の視点は動かず、サーバ上の向きもすぐ元に戻ること */
	private static void testProjectileAim(
		final ClientGameTestContext context, final TestDedicatedServerContext server, final TestDedicatedServerConnection connection
	) {
		setUpStage(context, server);
		server.runCommand("kill @e[type=!minecraft:player]");
		Window window = context.computeOnClient(Minecraft::getWindow);

		// 雪玉: 左上の空をカーソルで指して投げる
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:snowball 16");
		context.waitTicks(5);
		context.getInput().setCursorPos(window.getScreenWidth() * 0.2, window.getScreenHeight() * 0.25);
		context.waitTicks(2);
		Vec3 snowballAim = cursorDirection(context);
		context.getInput().pressKey(o -> o.keyUse);
		context.waitTicks(1);
		connection.waitForServerboundPackets();
		Vec3 snowballVelocity = server.computeOnServer(s -> {
			List<? extends Entity> snowballs = s.overworld().getEntities(EntityTypes.SNOWBALL, entity -> true);
			return snowballs.isEmpty() ? null : snowballs.getFirst().getDeltaMovement();
		});
		check(snowballVelocity != null, "雪玉が投げられていない");
		assertFlewToward(snowballAim, snowballVelocity, "雪玉");
		assertRotation(context, 0.0F, 0.0F, "雪玉を投げたときに視点が動いた");
		assertServerRotationRestored(context, server, connection, "雪玉");

		// 弓: 右上の空をカーソルで指し、引き絞って放す
		server.runCommand("kill @e[type=minecraft:snowball]");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
		server.runCommand("give @a minecraft:arrow 16");
		context.waitTicks(5);
		context.getInput().setCursorPos(window.getScreenWidth() * 0.75, window.getScreenHeight() * 0.3);
		context.waitTicks(2);
		// 弓を引き絞ると視野が狭まり、同じカーソル位置でも指す方向が変わるため、放す直前の方向を狙いとする
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(20);
		Vec3 arrowAim = cursorDirection(context);
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(1);
		connection.waitForServerboundPackets();
		Vec3 arrowVelocity = server.computeOnServer(s -> {
			List<? extends Entity> arrows = s.overworld().getEntities(EntityTypes.ARROW, entity -> true);
			return arrows.isEmpty() ? null : arrows.getFirst().getDeltaMovement();
		});
		check(arrowVelocity != null, "矢が放たれていない");
		assertFlewToward(arrowAim, arrowVelocity, "矢");
		assertRotation(context, 0.0F, 0.0F, "弓を放したときに視点が動いた");
		assertServerRotationRestored(context, server, connection, "矢");
		context.takeScreenshot("steadyview-08-projectiles");

		// 設定でオフにすると、通常どおり正面（画面中央）へ飛ぶ
		// （正面のブロックに当たってすぐ消えないよう、ブロックを片付けてから投げる）
		server.runCommand("kill @e[type=minecraft:arrow]");
		server.runCommand("fill -4 -60 -1 4 -56 6 minecraft:air");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:snowball 16");
		context.runOnClient(minecraft -> SteadyViewClient.config().aimItemsAtCursor = false);
		context.waitTicks(5);
		context.getInput().pressKey(o -> o.keyUse);
		context.waitTicks(1);
		connection.waitForServerboundPackets();
		Vec3 straightVelocity = server.computeOnServer(s -> {
			List<? extends Entity> snowballs = s.overworld().getEntities(EntityTypes.SNOWBALL, entity -> true);
			return snowballs.isEmpty() ? null : snowballs.getFirst().getDeltaMovement();
		});
		context.runOnClient(minecraft -> SteadyViewClient.config().aimItemsAtCursor = true);
		check(straightVelocity != null, "設定をオフにしたとき、雪玉が投げられていない");
		assertFlewToward(new Vec3(0.0, 0.0, 1.0), straightVelocity, "設定をオフにしたときの雪玉");
	}

	private static Vec3 cursorDirection(final ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> CursorPicker.cursorDirection(minecraft, minecraft.gameRenderer.mainCamera()));
	}

	/** 飛んでいる物の向きが、狙った向きと合っているか。重力で少し下がるため、左右は3度、上下は8度まで許す */
	private static void assertFlewToward(final Vec3 aim, final Vec3 velocity, final String name) {
		double yawDiff = Math.abs(Mth.wrapDegrees(yawOf(velocity) - yawOf(aim)));
		double pitchDiff = Math.abs(pitchOf(velocity) - pitchOf(aim));
		check(yawDiff < 3.0 && pitchDiff < 8.0, name + "がカーソルの方向へ飛んでいない（狙い: " + String.format("%.1f, %.1f", yawOf(aim), pitchOf(aim))
			+ " / 実際: " + String.format("%.1f, %.1f", yawOf(velocity), pitchOf(velocity)) + "）");
	}

	private static double yawOf(final Vec3 vector) {
		return Math.toDegrees(Math.atan2(vector.z, vector.x)) - 90.0;
	}

	private static double pitchOf(final Vec3 vector) {
		return -Math.toDegrees(Math.atan2(vector.y, Math.sqrt(vector.x * vector.x + vector.z * vector.z)));
	}

	/** 投げた・放した後、サーバ上のプレイヤーの向きが本来の向き（南・水平）に戻っているか */
	private static void assertServerRotationRestored(
		final ClientGameTestContext context, final TestDedicatedServerContext server, final TestDedicatedServerConnection connection, final String name
	) {
		context.waitTicks(3);
		connection.waitForServerboundPackets();
		float[] rotation = server.computeOnServer(s -> {
			var player = s.getPlayerList().getPlayers().getFirst();
			return new float[]{player.getYRot(), player.getXRot()};
		});
		check(Math.abs(Mth.wrapDegrees(rotation[0])) < DELTA && Math.abs(rotation[1]) < DELTA,
			name + "の後、サーバ上の向きが戻っていない（" + rotation[0] + ", " + rotation[1] + "）");
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

	/** カーソルを画面の端まで動かすと、その方向に45度回り、カーソルは同じ方向を指したまま内側へ移ること */
	private static void testEdgeTurn(final ClientGameTestContext context, final TestServerContext server) {
		setUpStage(context, server);
		Window window = context.computeOnClient(Minecraft::getWindow);
		double width = window.getScreenWidth();
		double height = window.getScreenHeight();

		// 左端: 左に45度回る
		Vec3 before = pushCursorToEdge(context, 0.0, height * 0.4);
		assertRotation(context, -45.0F, 0.0F, "左端で左に回っていない");
		assertCursorInside(context, width, height, "左端で回った後、カーソルが内側に移っていない");
		assertSameDirection(context, before, "左端で回った後、カーソルが同じ方向を指していない");
		context.takeScreenshot("steadyview-06-edge-left");

		// 回った後は端から離れるため、続けて回らない
		context.waitTicks(10);
		assertRotation(context, -45.0F, 0.0F, "左端で続けて回った");

		// 右端: 右に45度回る
		before = pushCursorToEdge(context, width - 1.0, height * 0.6);
		assertRotation(context, 0.0F, 0.0F, "右端で右に回っていない");
		assertSameDirection(context, before, "右端で回った後、カーソルが同じ方向を指していない");

		// 上端: 45度上を向く。下端: 45度下を向く
		before = pushCursorToEdge(context, width * 0.3, 0.0);
		assertRotation(context, 0.0F, -45.0F, "上端で上を向いていない");
		assertSameDirection(context, before, "上端で回った後、カーソルが同じ方向を指していない");
		pushCursorToEdge(context, width * 0.5, height - 1.0);
		assertRotation(context, 0.0F, 0.0F, "下端で下を向いていない");

		// 角: 左右と上下の両方に回る
		pushCursorToEdge(context, width - 1.0, height - 1.0);
		assertRotation(context, 45.0F, 45.0F, "右下の角で右下に回っていない");
		pressSteadyViewKey(context, "level_view");
		pressSteadyViewKey(context, "turn_left");

		// 真上を向いているときは、上端でそれ以上上を向かない（左右の端なら回る）
		pressSteadyViewKey(context, "look_up");
		pressSteadyViewKey(context, "look_up");
		pushCursorToEdge(context, width * 0.5, 0.0);
		assertRotation(context, 0.0F, -90.0F, "真上より上を向いた");
		// 端に置いたまま水平に戻しても、端に「来た」わけではないため、上を向き直さない
		pressSteadyViewKey(context, "level_view");
		context.waitTicks(10);
		assertRotation(context, 0.0F, 0.0F, "上端に置いたまま水平に戻したら、上を向き直した");

		// 設定でオフにすると回らない
		context.runOnClient(minecraft -> SteadyViewClient.config().turnAtScreenEdge = false);
		pushCursorToEdge(context, 0.0, height * 0.5);
		assertRotation(context, 0.0F, 0.0F, "設定でオフにしても端で回った");
		context.runOnClient(minecraft -> SteadyViewClient.config().turnAtScreenEdge = true);
		context.getInput().setCursorPos(width / 2.0, height / 2.0);
		context.waitTicks(10);
		assertRotation(context, 0.0F, 0.0F, "画面中央に戻したのに回った");
	}

	/** ボートに乗ったとき・ボートが曲がったときに、視点が自動で変わらないこと。設定でオフにすると通常どおり変わること */
	/** 設定画面のスライダーで「1回で回る角度」を変えると、キー・画面の端・そろえ方に反映され、設定ファイルに保存されること */
	private static void testStepAngleSetting(final ClientGameTestContext context, final TestServerContext server) {
		setUpStage(context, server);

		// Mod Menuの「Mod」一覧から、Steady Viewの設定画面を開ける（Mod Menuに登録した入口から画面が作られる）
		check(FabricLoader.getInstance().isModLoaded("modmenu"), "テスト環境にMod Menuが入っていない");
		boolean openedFromModMenu = context.computeOnClient(minecraft -> FabricLoader.getInstance()
			.getEntrypointContainers("modmenu", ModMenuApi.class).stream()
			.filter(container -> container.getProvider().getMetadata().getId().equals("steadyview"))
			.anyMatch(container -> container.getEntrypoint().getModConfigScreenFactory().create(null) instanceof SteadyViewConfigScreen));
		check(openedFromModMenu, "Mod Menuから設定画面を開けない");

		// キー（F7）で設定画面を開く
		pressSteadyViewKey(context, "open_settings");
		check(context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof SteadyViewConfigScreen), "キーで設定画面が開かない");
		check(context.computeOnClient(minecraft -> SteadyViewClient.config().stepAngle == 45), "角度の既定値が45度ではない");

		// スライダーの30度の位置をクリックする（スライダーは5・6・9・10・15・18・30・45・90度の9段階）
		double[] point = context.computeOnClient(minecraft -> {
			AbstractWidget slider = ((SteadyViewConfigScreen)minecraft.gui.screen()).stepAngleWidget();
			double scale = minecraft.getWindow().getGuiScale();
			double fraction = (double)AngleMath.stepIndex(30) / (AngleMath.ALLOWED_STEPS.length - 1);
			// スライダーのつまみの幅（8）の半分ずつ、両端は値の範囲に入らない
			double x = slider.getX() + 4 + (slider.getWidth() - 8) * fraction;
			double y = slider.getY() + slider.getHeight() / 2.0;
			return new double[]{x * scale, y * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.getInput().pressMouse(1);
		context.waitTicks(2);
		int angle = context.computeOnClient(minecraft -> SteadyViewClient.config().stepAngle);
		check(angle == 30, "スライダーで30度にできない（実際: " + angle + "）");
		context.takeScreenshot("steadyview-09-settings");

		// 閉じると設定ファイルに保存される
		context.runOnClient(minecraft -> minecraft.gui.screen().onClose());
		context.waitTicks(2);
		check(context.computeOnClient(minecraft -> minecraft.gui.screen() == null), "設定画面を閉じてもゲームに戻らない");
		String saved = context.computeOnClient(minecraft -> {
			try {
				return Files.readString(FabricLoader.getInstance().getConfigDir().resolve("steadyview.properties"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		check(saved.contains("stepAngle=30"), "設定ファイルに角度が保存されていない");

		// キーで30度ずつ回る
		context.getInput().setCursorPos(context.computeOnClient(m -> m.getWindow().getScreenWidth()) / 2.0, context.computeOnClient(m -> m.getWindow().getScreenHeight()) / 2.0);
		context.waitTicks(2);
		pressSteadyViewKey(context, "turn_right");
		assertRotation(context, 30.0F, 0.0F, "30度に設定しても右に30度回らない");
		pressSteadyViewKey(context, "look_down");
		pressSteadyViewKey(context, "look_down");
		pressSteadyViewKey(context, "look_down");
		pressSteadyViewKey(context, "look_down");
		assertRotation(context, 30.0F, 90.0F, "30度ずつ下を向いて真下で止まらない");
		pressSteadyViewKey(context, "level_view");

		// 中途半端な向きは30度単位にそろう
		server.runCommand("tp @a 0.5 -60 0.5 40 20");
		context.waitTicks(5);
		assertRotation(context, 30.0F, 30.0F, "30度単位にそろわない");
		pressSteadyViewKey(context, "level_view");

		// 画面の端でも30度ずつ回る
		Window window = context.computeOnClient(Minecraft::getWindow);
		pushCursorToEdge(context, 0.0, window.getScreenHeight() * 0.5);
		assertRotation(context, 0.0F, 0.0F, "30度に設定しても、左端で左に30度回らない");

		// 45度に戻す
		context.runOnClient(minecraft -> SteadyViewClient.config().stepAngle = 45);
		context.getInput().setCursorPos(window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
		setUpStage(context, server);
	}

	/** 三人称視点で、自分の体が設定の不透明度で半透明に描かれること */
	private static void testPlayerOpacity(final ClientGameTestContext context, final TestServerContext server) {
		setUpStage(context, server);
		Window window = context.computeOnClient(Minecraft::getWindow);
		context.getInput().setCursorPos(window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
		context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		Path opaque = context.takeScreenshot("steadyview-10-opacity-100");

		// 設定画面のスライダーで50%にする
		pressSteadyViewKey(context, "open_settings");
		double[] point = context.computeOnClient(minecraft -> {
			AbstractWidget slider = ((SteadyViewConfigScreen)minecraft.gui.screen()).playerOpacityWidget();
			double scale = minecraft.getWindow().getGuiScale();
			double x = slider.getX() + 4 + (slider.getWidth() - 8) * 0.5;
			double y = slider.getY() + slider.getHeight() / 2.0;
			return new double[]{x * scale, y * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.getInput().pressMouse(1);
		context.waitTicks(2);
		int opacity = context.computeOnClient(minecraft -> SteadyViewClient.config().playerOpacity);
		check(opacity == 50, "スライダーで50%にできない（実際: " + opacity + "）");
		context.takeScreenshot("steadyview-11-settings-opacity");
		context.runOnClient(minecraft -> minecraft.gui.screen().onClose());
		context.getInput().setCursorPos(window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
		context.waitTicks(5);
		Path half = context.takeScreenshot("steadyview-12-opacity-50");

		// 0%にすると体が見えなくなる
		context.runOnClient(minecraft -> SteadyViewClient.config().playerOpacity = 0);
		context.waitTicks(5);
		Path hidden = context.takeScreenshot("steadyview-13-opacity-0");
		int stateOpacity = context.computeOnClient(minecraft -> ((PlayerOpacityHolder)minecraft.getEntityRenderDispatcher()
			.getRenderer(minecraft.player).createRenderState(minecraft.player, 1.0F)).steadyview$getOpacity());
		check(stateOpacity == 0, "自分のプレイヤーの描画情報に不透明度が入っていない（実際: " + stateOpacity + "）");

		// 体の中ほどの色を比べる。50%のときは、100%（体の色）と0%（背景の色）のほぼ中間になる
		int[] opaqueColor = averageColor(opaque, 0.5, 0.62);
		int[] halfColor = averageColor(half, 0.5, 0.62);
		int[] hiddenColor = averageColor(hidden, 0.5, 0.62);
		int difference = 0;
		int halfError = 0;
		for (int i = 0; i < 3; i++) {
			difference += Math.abs(opaqueColor[i] - hiddenColor[i]);
			halfError += Math.abs(halfColor[i] - (opaqueColor[i] + hiddenColor[i]) / 2);
		}
		String colors = "（100%: " + Arrays.toString(opaqueColor) + " / 50%: " + Arrays.toString(halfColor) + " / 0%: " + Arrays.toString(hiddenColor) + "）";
		check(difference > 60, "不透明度を0%にしても、体の見え方が変わらない" + colors);
		check(halfError < difference / 4, "50%のとき、体が半透明になっていない" + colors);

		// インベントリ画面に映る自分は、通常どおり（不透明）
		context.setScreen(() -> new InventoryScreen(context.computeOnClient(minecraft -> minecraft.player)));
		context.waitTicks(2);
		context.takeScreenshot("steadyview-14-inventory");
		context.setScreen(() -> null);

		// 元に戻す
		context.runOnClient(minecraft -> {
			SteadyViewClient.config().playerOpacity = 100;
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		});
		context.waitTicks(2);
	}

	/** スクリーンショットの、指定した位置（幅・高さに対する割合）の周り9×9ドットの平均の色（R・G・B） */
	private static int[] averageColor(final Path screenshot, final double xRatio, final double yRatio) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			int centerX = (int)(image.getWidth() * xRatio);
			int centerY = (int)(image.getHeight() * yRatio);
			int[] sum = new int[3];
			int count = 0;
			for (int y = centerY - 4; y <= centerY + 4; y++) {
				for (int x = centerX - 4; x <= centerX + 4; x++) {
					int rgb = image.getRGB(x, y);
					sum[0] += rgb >> 16 & 0xFF;
					sum[1] += rgb >> 8 & 0xFF;
					sum[2] += rgb & 0xFF;
					count++;
				}
			}
			return new int[]{sum[0] / count, sum[1] / count, sum[2] / count};
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 画面の端でなめらかに回る設定（edgeTurnModeがPUSH・SCROLL） */
	private static void testEdgeFollow(final ClientGameTestContext context, final TestServerContext server) {
		Window window = context.computeOnClient(Minecraft::getWindow);
		double width = window.getScreenWidth();
		double height = window.getScreenHeight();

		// PUSH: 端の手前の線（端から16）を越えた分だけ回り、カーソルは線の上に戻る
		context.runOnClient(minecraft -> SteadyViewClient.config().edgeTurnMode = EdgeTurnMode.PUSH);
		setUpStage(context, server);
		context.getInput().setCursorPos(0.0, height * 0.5);
		context.waitTicks(3);
		float yaw = context.computeOnClient(minecraft -> minecraft.player.getYRot());
		// 既定の感度では、1ドットで0.15度。16ドット押し込んだので2.4度
		check(yaw < -1.0F && yaw > -5.0F, "左端に押し込んだ分だけ左に回っていない（実際: " + yaw + "）");
		double cursorX = context.computeOnClient(minecraft -> minecraft.mouseHandler.xpos());
		check(Math.abs(cursorX - 16.0) < 1.0, "押し込んだ後、カーソルが線の上に戻っていない（実際: " + cursorX + "）");
		// マウスを止めれば止まる
		context.waitTicks(10);
		assertRotation(context, yaw, 0.0F, "マウスを止めても回り続けた");
		// 45度単位にそろえない
		context.waitTicks(5);
		assertRotation(context, yaw, 0.0F, "45度単位にそろえた");
		// キーでは、今の向きから45度回る
		pressSteadyViewKey(context, "turn_right");
		assertRotation(context, yaw + 45.0F, 0.0F, "今の向きから45度回っていない");
		pressSteadyViewKey(context, "look_down");
		pressSteadyViewKey(context, "level_view");
		assertRotation(context, yaw + 45.0F, 0.0F, "水平に戻したとき、左右の向きが変わった");
		// 上端に押し込むと上を向く
		context.getInput().setCursorPos(width * 0.5, 0.0);
		context.waitTicks(3);
		float pitch = context.computeOnClient(minecraft -> minecraft.player.getXRot());
		check(pitch < -1.0F, "上端に押し込んでも上を向いていない（実際: " + pitch + "）");
		context.takeScreenshot("steadyview-06b-edge-push");

		// SCROLL: 端にある間、一定の速さ（既定で1秒に90度）で回り続ける
		context.runOnClient(minecraft -> SteadyViewClient.config().edgeTurnMode = EdgeTurnMode.SCROLL);
		context.getInput().setCursorPos(width * 0.5, height * 0.5);
		setUpStage(context, server);
		context.getInput().setCursorPos(width - 1.0, height * 0.5);
		context.waitTicks(10);
		yaw = context.computeOnClient(minecraft -> minecraft.player.getYRot());
		check(yaw > 10.0F, "右端に置いても右に回り続けていない（実際: " + yaw + "）");
		context.getInput().setCursorPos(width * 0.5, height * 0.5);
		context.waitTicks(2);
		yaw = context.computeOnClient(minecraft -> minecraft.player.getYRot());
		context.waitTicks(10);
		assertRotation(context, yaw, 0.0F, "端から離しても回り続けた");
		context.getInput().setCursorPos(width * 0.5, height - 1.0);
		context.waitTicks(10);
		pitch = context.computeOnClient(minecraft -> minecraft.player.getXRot());
		check(pitch > 10.0F, "下端に置いても下を向いていない（実際: " + pitch + "）");
		context.takeScreenshot("steadyview-06c-edge-scroll");

		// 45度切り替えに戻すと、45度単位にそろう
		context.runOnClient(minecraft -> SteadyViewClient.config().edgeTurnMode = EdgeTurnMode.STEP);
		context.getInput().setCursorPos(width * 0.5, height * 0.5);
		setUpStage(context, server);
	}

	private static void testKeepViewWhileRiding(final ClientGameTestContext context, final TestServerContext server) {
		setUpStage(context, server);
		// ボートは曲がるときに少しずつ前へ進むため、ぶつからないよう周りのブロックを片付けてから水を張る
		server.runCommand("fill -6 -60 -6 6 -56 6 minecraft:air");
		server.runCommand("fill -6 -61 -6 6 -61 6 minecraft:water");
		// プレイヤーは南（0度）を向いている。ボートは西（90度）向き
		// 水中に出すと沈んだ扱いになり、数秒で降ろされるため、水面の上に出す
		server.runCommand("summon minecraft:oak_boat 0.5 -60 0.5 {Rotation:[90f,0f]}");
		context.waitTicks(5);
		server.runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1]");
		context.waitTicks(10);
		check(context.computeOnClient(minecraft -> minecraft.player.isPassenger()), "ボートに乗れていない");
		// 通常は、ボートに乗った瞬間にボートの向き（90度）を向く
		assertRotation(context, 0.0F, 0.0F, "ボートに乗ったときに視点が変わった");

		// 左に曲がり続けても、視点は変わらない（ボートだけが曲がる）
		float boatYaw = context.computeOnClient(minecraft -> minecraft.player.getVehicle().getYRot());
		context.getInput().holdKeyFor(o -> o.keyLeft, 40);
		float turnedBoatYaw = context.computeOnClient(minecraft -> minecraft.player.getVehicle().getYRot());
		check(Math.abs(turnedBoatYaw - boatYaw) > 20.0F, "ボートが曲がっていない（" + boatYaw + " → " + turnedBoatYaw + "）");
		assertRotation(context, 0.0F, 0.0F, "ボートが曲がったときに視点が変わった");
		context.takeScreenshot("steadyview-07-boat");

		// 乗っている間も、キーでは通常どおり回れる
		pressSteadyViewKey(context, "turn_left");
		context.waitTicks(5);
		assertRotation(context, -45.0F, 0.0F, "ボートに乗っている間にキーで回れない");
		pressSteadyViewKey(context, "turn_right");

		// 設定でオフにすると、ボートが曲がるのに合わせて、視点も45度単位で変わる
		context.runOnClient(minecraft -> SteadyViewClient.config().keepViewWhileRiding = false);
		float boatYawBefore = context.computeOnClient(minecraft -> minecraft.player.getVehicle().getYRot());
		context.getInput().holdKeyFor(o -> o.keyLeft, 80);
		float boatTurn = context.computeOnClient(minecraft -> minecraft.player.getVehicle().getYRot()) - boatYawBefore;
		float playerYaw = context.computeOnClient(minecraft -> minecraft.player.getYRot());
		check(Math.abs(boatTurn) > 45.0F, "ボートが45度以上曲がっていない（" + boatTurn + "）");
		check(playerYaw != 0.0F && playerYaw % 45.0F == 0.0F && Math.abs(playerYaw - boatTurn) <= 22.5F + DELTA,
			"設定をオフにしたとき、視点がボートに45度単位で追従していない（ボート: " + boatTurn + " / 視点: " + playerYaw + "）");
		context.runOnClient(minecraft -> SteadyViewClient.config().keepViewWhileRiding = true);

		// 降りる
		server.runCommand("ride @p dismount");
		context.waitTicks(10);
		check(!context.computeOnClient(minecraft -> minecraft.player.isPassenger()), "ボートから降りられない");
		server.runCommand("kill @e[type=minecraft:oak_boat]");
		server.runCommand("fill -6 -61 -6 6 -61 6 minecraft:grass_block");
	}

	/** カーソルを画面の端に置き、その時点でカーソルが指している方向を返す（端での処理は次のtickの終わりに行われる） */
	private static Vec3 pushCursorToEdge(final ClientGameTestContext context, final double x, final double y) {
		// 前回の切り替えの後の待ち時間（0.25秒）が終わるのを待つ
		context.waitTicks(8);
		context.getInput().setCursorPos(x, y);
		Vec3 direction = context.computeOnClient(minecraft -> CursorPicker.cursorDirection(minecraft, minecraft.gameRenderer.mainCamera()));
		context.waitTicks(3);
		return direction;
	}

	private static void assertCursorInside(final ClientGameTestContext context, final double width, final double height, final String message) {
		double[] position = context.computeOnClient(minecraft -> new double[]{minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()});
		check(position[0] >= 16.0 && position[0] <= width - 16.0 && position[1] >= 16.0 && position[1] <= height - 16.0,
			message + "（実際: " + position[0] + ", " + position[1] + "）");
	}

	/** 今のカーソルが指す方向が、before とほぼ同じ（1度以内）か */
	private static void assertSameDirection(final ClientGameTestContext context, final Vec3 before, final String message) {
		Vec3 after = context.computeOnClient(minecraft -> CursorPicker.cursorDirection(minecraft, minecraft.gameRenderer.mainCamera()));
		double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, before.dot(after)))));
		check(angle < 1.0, message + "（ずれ: " + angle + "度）");
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
