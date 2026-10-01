package net.onlinepark.steadyview;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
			} catch (IOException e) {
				SteadyViewClient.LOGGER.warn("Failed to read {}. Using defaults.", path, e);
			}
		}

		// 新しい項目が増えたときにもファイルへ反映されるよう、毎回書き直す
		config.save(path);
		return config;
	}

	private void save(final Path path) {
		Properties properties = new Properties();
		properties.setProperty("enabledOnStartup", Boolean.toString(this.enabledOnStartup));
		properties.setProperty("confineCursor", Boolean.toString(this.confineCursor));
		properties.setProperty("hideCrosshair", Boolean.toString(this.hideCrosshair));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				properties.store(writer, "Steady View settings");
			}
		} catch (IOException e) {
			SteadyViewClient.LOGGER.warn("Failed to write {}.", path, e);
		}
	}

	private static boolean getBoolean(final Properties properties, final String key, final boolean defaultValue) {
		String value = properties.getProperty(key);
		return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
	}
}
