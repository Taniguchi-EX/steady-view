package net.onlinepark.steadyview;

/**
 * プレイヤーの描画情報（AvatarRenderState）に、Steady Viewが付け足す「不透明度」。AvatarRenderStateMixinで実装する。
 *
 * <p>描画情報を作るとき（AvatarRendererMixin）に、自分のプレイヤーなら設定の不透明度を入れ、描くとき（LivingEntityRendererMixin）に使う。
 */
public interface PlayerOpacityHolder {
	/** 不透明度（0〜100%）。100なら通常どおり描く */
	int steadyview$getOpacity();

	void steadyview$setOpacity(int opacity);
}
