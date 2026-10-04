package net.onlinepark.steadyview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AngleMathTest {
	private static final float DELTA = 1.0E-4F;

	@Test
	void snapYawRoundsToNearest45() {
		assertEquals(0.0F, AngleMath.snapYaw(0.0F), DELTA);
		assertEquals(0.0F, AngleMath.snapYaw(22.0F), DELTA);
		assertEquals(45.0F, AngleMath.snapYaw(23.0F), DELTA);
		assertEquals(90.0F, AngleMath.snapYaw(100.0F), DELTA);
		assertEquals(-45.0F, AngleMath.snapYaw(-30.0F), DELTA);
		assertEquals(-180.0F, AngleMath.snapYaw(-170.0F), DELTA);
	}

	@Test
	void snapYawKeepsUnwrappedValues() {
		// 何周も回った後の値も、360度で折り返さずにそろえる
		assertEquals(720.0F, AngleMath.snapYaw(719.0F), DELTA);
		assertEquals(-765.0F, AngleMath.snapYaw(-760.0F), DELTA);
	}

	@Test
	void snapPitchRoundsAndClamps() {
		assertEquals(0.0F, AngleMath.snapPitch(10.0F), DELTA);
		assertEquals(45.0F, AngleMath.snapPitch(30.0F), DELTA);
		assertEquals(-45.0F, AngleMath.snapPitch(-60.0F), DELTA);
		assertEquals(90.0F, AngleMath.snapPitch(90.0F), DELTA);
		assertEquals(-90.0F, AngleMath.snapPitch(-90.0F), DELTA);
		assertEquals(90.0F, AngleMath.snapPitch(120.0F), DELTA);
	}

	@Test
	void turnYawSnapsThenTurns() {
		assertEquals(45.0F, AngleMath.turnYaw(0.0F, 1), DELTA);
		assertEquals(-45.0F, AngleMath.turnYaw(0.0F, -1), DELTA);
		// ずれていた場合は、そろえてから回す
		assertEquals(135.0F, AngleMath.turnYaw(80.0F, 1), DELTA);
		assertEquals(180.0F, AngleMath.turnYaw(0.0F, 4), DELTA);
		// 一周すると360度になる（折り返さない）
		assertEquals(360.0F, AngleMath.turnYaw(0.0F, 8), DELTA);
	}

	@Test
	void tiltPitchStopsAtStraightUpAndDown() {
		assertEquals(45.0F, AngleMath.tiltPitch(0.0F, 1), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(45.0F, 1), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitch(90.0F, 1), DELTA);
		assertEquals(-45.0F, AngleMath.tiltPitch(0.0F, -1), DELTA);
		assertEquals(-90.0F, AngleMath.tiltPitch(-90.0F, -1), DELTA);
		assertEquals(0.0F, AngleMath.tiltPitch(-10.0F, 0), DELTA);
	}

	@Test
	void isSnappedDetectsDeviation() {
		assertTrue(AngleMath.isSnapped(0.0F, 0.0F));
		assertTrue(AngleMath.isSnapped(-135.0F, 45.0F));
		assertTrue(AngleMath.isSnapped(360.0F, -90.0F));
		assertFalse(AngleMath.isSnapped(10.0F, 0.0F));
		assertFalse(AngleMath.isSnapped(0.0F, 12.5F));
		// マウスで回した後に残るわずかな誤差もずれとして扱う（ゲーム内テストで実際に出た値）
		assertFalse(AngleMath.isSnapped(45.000008F, 0.0F));
	}

	@Test
	void turnYawFreeKeepsOffset() {
		assertEquals(75.0F, AngleMath.turnYawFree(30.0F, 1), DELTA);
		assertEquals(-15.0F, AngleMath.turnYawFree(30.0F, -1), DELTA);
		assertEquals(390.0F, AngleMath.turnYawFree(30.0F, 8), DELTA);
	}

	@Test
	void tiltPitchFreeKeepsOffsetAndStops() {
		assertEquals(55.0F, AngleMath.tiltPitchFree(10.0F, 1), DELTA);
		assertEquals(-35.0F, AngleMath.tiltPitchFree(10.0F, -1), DELTA);
		assertEquals(90.0F, AngleMath.tiltPitchFree(60.0F, 1), DELTA);
		assertEquals(-90.0F, AngleMath.tiltPitchFree(-60.0F, -1), DELTA);
	}
}
