package net.onlinepark.steadyview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AngleMathTest {
	private static final float DELTA = 1.0E-4F;
	private static final float S45 = 45.0F;

	@Test
	void snapYawRoundsToNearest45() {
		assertEquals(0.0F, AngleMath.snapYaw(0.0F, S45), DELTA);
		assertEquals(0.0F, AngleMath.snapYaw(22.0F, S45), DELTA);
		assertEquals(45.0F, AngleMath.snapYaw(23.0F, S45), DELTA);
		assertEquals(90.0F, AngleMath.snapYaw(100.0F, S45), DELTA);
		assertEquals(-45.0F, AngleMath.snapYaw(-30.0F, S45), DELTA);
		assertEquals(-180.0F, AngleMath.snapYaw(-170.0F, S45), DELTA);
	}

	@Test
	void snapYawKeepsUnwrappedValues() {
		// 何周も回った後の値も、360度で折り返さずにそろえる
		assertEquals(720.0F, AngleMath.snapYaw(719.0F, S45), DELTA);
		assertEquals(-765.0F, AngleMath.snapYaw(-760.0F, S45), DELTA);
	}

	@Test
	void snapPitchRoundsAndClamps() {
		assertEquals(0.0F, AngleMath.snapPitch(10.0F, S45), DELTA);
		assertEquals(45.0F, AngleMath.snapPitch(30.0F, S45), DELTA);
		assertEquals(-45.0F, AngleMath.snapPitch(-60.0F, S45), DELTA);
		assertEquals(90.0F, AngleMath.snapPitch(90.0F, S45), DELTA);
		assertEquals(-90.0F, AngleMath.snapPitch(-90.0F, S45), DELTA);
		assertEquals(90.0F, AngleMath.snapPitch(120.0F, S45), DELTA);
	}

	@Test
	void turnYawSnapsThenTurns() {
		assertEquals(45.0F, AngleMath.turnYaw(0.0F, 1, S45), DELTA);
		assertEquals(-45.0F, AngleMath.turnYaw(0.0F, -1, S45), DELTA);
		// ずれていた場合は、そろえてから回す
		assertEquals(135.0F, AngleMath.turnYaw(80.0F, 1, S45), DELTA);
		assertEquals(180.0F, AngleMath.turnYaw(0.0F, 4, S45), DELTA);
		// 一周すると360度になる（折り返さない）
		assertEquals(360.0F, AngleMath.turnYaw(0.0F, 8, S45), DELTA);
	}

	@Test
	void tiltPitchStopsAtStraightUpAndDown() {
		assertEquals(45.0F, AngleMath.tiltPitch(0.0F, 1, S45), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(45.0F, 1, S45), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(90.0F, 1, S45), DELTA);
		assertEquals(-45.0F, AngleMath.tiltPitch(0.0F, -1, S45), DELTA);
		assertEquals(-90.0F, AngleMath.tiltPitch(-90.0F, -1, S45), DELTA);
		assertEquals(0.0F, AngleMath.tiltPitch(-10.0F, 0, S45), DELTA);
	}

	@Test
	void isSnappedDetectsDeviation() {
		assertTrue(AngleMath.isSnapped(0.0F, 0.0F, S45));
		assertTrue(AngleMath.isSnapped(-135.0F, 45.0F, S45));
		assertTrue(AngleMath.isSnapped(360.0F, -90.0F, S45));
		assertFalse(AngleMath.isSnapped(10.0F, 0.0F, S45));
		assertFalse(AngleMath.isSnapped(0.0F, 12.5F, S45));
		// マウスで回した後に残るわずかな誤差もずれとして扱う（ゲーム内テストで実際に出た値）
		assertFalse(AngleMath.isSnapped(45.000008F, 0.0F, S45));
	}

	@Test
	void turnYawFreeKeepsOffset() {
		assertEquals(75.0F, AngleMath.turnYawFree(30.0F, 1, S45), DELTA);
		assertEquals(-15.0F, AngleMath.turnYawFree(30.0F, -1, S45), DELTA);
		assertEquals(390.0F, AngleMath.turnYawFree(30.0F, 8, S45), DELTA);
	}

	@Test
	void tiltPitchFreeKeepsOffsetAndStops() {
		assertEquals(55.0F, AngleMath.tiltPitchFree(10.0F, 1, S45), DELTA);
		assertEquals(-35.0F, AngleMath.tiltPitchFree(10.0F, -1, S45), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitchFree(60.0F, 1, S45), DELTA);
		assertEquals(-90.0F, AngleMath.tiltPitchFree(-60.0F, -1, S45), DELTA);
	}

	@Test
	void otherStepsWork() {
		// 30度: 東西南北と水平・真上・真下に加え、30度ずつ
		assertEquals(30.0F, AngleMath.snapYaw(20.0F, 30.0F), DELTA);
		assertEquals(60.0F, AngleMath.turnYaw(25.0F, 1, 30.0F), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(60.0F, 1, 30.0F), DELTA);
		assertEquals(-90.0F, AngleMath.tiltPitch(-75.0F, -1, 30.0F), DELTA);
		assertTrue(AngleMath.isSnapped(-150.0F, 60.0F, 30.0F));
		assertFalse(AngleMath.isSnapped(45.0F, 0.0F, 30.0F));
		// 90度: 東西南北だけ。上下は水平・真上・真下
		assertEquals(90.0F, AngleMath.turnYaw(10.0F, 1, 90.0F), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(0.0F, 1, 90.0F), DELTA);
		// 5度
		assertEquals(15.0F, AngleMath.turnYaw(12.0F, 1, 5.0F), DELTA);
		assertEquals(17.0F, AngleMath.turnYawFree(12.0F, 1, 5.0F), DELTA);
	}

	@Test
	void allowedStepsDivide90() {
		for (int step : AngleMath.ALLOWED_STEPS) {
			assertEquals(0, 90 % step, step + "は90の約数ではない");
		}
	}

	@Test
	void nearestAllowedStepNormalizesInvalidValues() {
		assertEquals(45, AngleMath.nearestAllowedStep(45));
		assertEquals(45, AngleMath.nearestAllowedStep(40));
		assertEquals(30, AngleMath.nearestAllowedStep(25));
		assertEquals(5, AngleMath.nearestAllowedStep(0));
		assertEquals(5, AngleMath.nearestAllowedStep(-10));
		assertEquals(90, AngleMath.nearestAllowedStep(120));
		// 12は10と15の間で、10の方が近い
		assertEquals(10, AngleMath.nearestAllowedStep(12));
		assertEquals(0, AngleMath.stepIndex(5));
		assertEquals(7, AngleMath.stepIndex(45));
		assertEquals(8, AngleMath.stepIndex(90));
	}
}
