package net.onlinepark.steadyview;

import net.minecraft.util.ARGB;

/**
 * 自分のプレイヤーを半透明に描くときの計算。体（LivingEntityRendererMixin）と、
 * 体に装備した物（防具・エリトラ・マント。EquipmentLayerRendererMixin・CapeLayerMixin）で共通に使う。
 */
public final class PlayerOpacity {
	private PlayerOpacity() {
	}

	/** 描画情報に入っている不透明度（0〜100%）。自分のプレイヤー以外（描画情報がプレイヤーでないものを含む）は100 */
	public static int of(final Object state) {
		return state instanceof PlayerOpacityHolder holder ? holder.steadyview$getOpacity() : 100;
	}

	/** 半透明で描く必要があるか */
	public static boolean isTranslucent(final Object state) {
		return of(state) < 100;
	}

	/** 色（ARGB）のアルファに不透明度を掛ける */
	public static int applyTo(final int color, final int opacity) {
		return ARGB.multiply(color, ARGB.color(Math.round(opacity * 255 / 100.0F), 255, 255, 255));
	}
}
