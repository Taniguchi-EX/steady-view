package net.onlinepark.steadyview;

/**
 * 画面の端での視点の変え方（設定ファイルのedgeTurnMode）。
 */
public enum EdgeTurnMode {
	/** カーソルが端に来たら、その方向に45度切り替える（EdgeTurner） */
	STEP,
	/** カーソルを端からさらに外側へ押し込んだ分だけ、なめらかに回る（EdgeFollower） */
	PUSH,
	/** カーソルが端にある間、一定の速さで回り続ける（EdgeFollower） */
	SCROLL
}
