package net.onlinepark.steadyview;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menuの「Mod」一覧から、Steady Viewの設定画面を開けるようにする。
 *
 * <p>Mod Menuは入れなくても動く（入っていないときは、このクラスは読み込まれない）。
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SteadyViewConfigScreen::new;
	}
}
