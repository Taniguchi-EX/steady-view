package net.onlinepark.steadyview;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 設定ファイル（config/steadyview.properties）。起動時に1回読み、ファイルがなければ既定値で作る。
 */
public final class SteadyViewConfig {
	private static final String FILE_NAME = "steadyview.properties";

	/** 起動時に有効にするか */
	public boolean enabledOnStartup = true;
	/** ゲーム中、カーソルをウィンドウ内に閉じ込めるか */
	public boolean confineCursor = true;
	/** 有効なとき、画面中央の照準を隠すか */
	public boolean hideCrosshair = true;
	/** カーソルを画面の端まで動かしたとき、その方向に視点を変えるか */
	public boolean turnAtScreenEdge = true;
	/** 画面の端での視点の変え方 */
	public EdgeTurnMode edgeTurnMode = EdgeTurnMode.STEP;
	/** edgeTurnModeがSCROLLのとき、1秒間に回る角度（度） */
	public double edgeScrollSpeed = 90.0;
	/** 乗り物（ボート・トロッコ・馬等）に乗っている間、マイクラ本体による自動的な視点の変更を打ち消すか */
	public boolean keepViewWhileRiding = true;
	/** 弓・雪玉等の飛ばすアイテムを、カーソルの方向へ飛ばすか */
	public boolean aimItemsAtCursor = true;
	/** マイクラ本体のおすすめ設定（RecommendedSettings）を反映済みか。falseに戻すと、次の起動時にもう一度反映する */
	public boolean recommendedSettingsApplied = false;

	private Path path;

	public static SteadyViewConfig load() {
		SteadyViewConfig config = new SteadyViewConfig();
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		if (Files.exists(path)) {
			Properties properties = new Properties();
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				properties.load(reader);
				config.enabledOnStartup = getBoolean(properties, "enabledOnStartup", config.enabledOnStartup);
				config.confineCursor = getBoolean(properties, "confineCursor", config.confineCursor);
				config.hideCrosshair = getBoolean(properties, "hideCrosshair", config.hideCrosshair);
				config.turnAtScreenEdge = getBoolean(properties, "turnAtScreenEdge", config.turnAtScreenEdge);
				config.edgeTurnMode = getMode(properties, "edgeTurnMode", config.edgeTurnMode);
				config.edgeScrollSpeed = getPositiveDouble(properties, "edgeScrollSpeed", config.edgeScrollSpeed);
				config.keepViewWhileRiding = getBoolean(properties, "keepViewWhileRiding", config.keepViewWhileRiding);
				config.aimItemsAtCursor = getBoolean(properties, "aimItemsAtCursor", config.aimItemsAtCursor);
				config.recommendedSettingsApplied = getBoolean(properties, "recommendedSettingsApplied", config.recommendedSettingsApplied);
			} catch (IOException e) {
				SteadyViewClient.LOGGER.warn("Failed to read {}. Using defaults.", path, e);
			}
		}

		// 新しい項目が増えたときにもファイルへ反映されるよう、毎回書き直す
		config.path = path;
		config.save();
		return config;
	}

	public void save() {
		if (this.path == null) {
			return;
		}

		Properties properties = new Properties();
		properties.setProperty("enabledOnStartup", Boolean.toString(this.enabledOnStartup));
		properties.setProperty("confineCursor", Boolean.toString(this.confineCursor));
		properties.setProperty("hideCrosshair", Boolean.toString(this.hideCrosshair));
		properties.setProperty("turnAtScreenEdge", Boolean.toString(this.turnAtScreenEdge));
		properties.setProperty("edgeTurnMode", this.edgeTurnMode.name().toLowerCase(Locale.ROOT));
		properties.setProperty("edgeScrollSpeed", Double.toString(this.edgeScrollSpeed));
		properties.setProperty("keepViewWhileRiding", Boolean.toString(this.keepViewWhileRiding));
		properties.setProperty("aimItemsAtCursor", Boolean.toString(this.aimItemsAtCursor));
		properties.setProperty("recommendedSettingsApplied", Boolean.toString(this.recommendedSettingsApplied));
		try {
			Files.createDirectories(this.path.getParent());
			try (Writer writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
				properties.store(writer, "Steady View settings");
			}
		} catch (IOException e) {
			SteadyViewClient.LOGGER.warn("Failed to write {}.", this.path, e);
		}
	}

	/**
	 * 視点の向きを45度単位に限らないか。画面の端でなめらかに回る方式（PUSH・SCROLL）のときはtrue。
	 * このときは、キーで回るときも今の向きから45度回り、45度単位にはそろえない。
	 */
	public boolean freeAngle() {
		return this.turnAtScreenEdge && this.edgeTurnMode != EdgeTurnMode.STEP;
	}

	private static EdgeTurnMode getMode(final Properties properties, final String key, final EdgeTurnMode defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			return EdgeTurnMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			SteadyViewClient.LOGGER.warn("Unknown {}: {}. Using {}.", key, value, defaultValue);
			return defaultValue;
		}
	}

	private static double getPositiveDouble(final Properties properties, final String key, final double defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			double parsed = Double.parseDouble(value.trim());
			if (parsed > 0.0 && Double.isFinite(parsed)) {
				return parsed;
			}
		} catch (NumberFormatException e) {
			// 下で既定値にする
		}

		SteadyViewClient.LOGGER.warn("Invalid {}: {}. Using {}.", key, value, defaultValue);
		return defaultValue;
	}

	private static boolean getBoolean(final Properties properties, final String key, final boolean defaultValue) {
		String value = properties.getProperty(key);
		return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
	}
}
