package net.onlinepark.steadyview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 元のカメラの位置（マイクラ本体が障害物の手前に寄せた位置）と、プレイヤーの目の位置から見えるマスを求める（SeeThrough参照）。
 *
 * <p>元のカメラの位置を中心にした{@value #SIZE}ブロック四方の範囲について、マスごとに「どちらかの位置から、光を通さないブロック
 * （{@link BlockState#isSolidRender()}。石・土等）に遮られずに線を引けるか」を調べる。ブロックの状態は表示用のスレッドで写し取り、
 * 計算は別のスレッドで行う（高さごとに分けて、複数のCPUで同時に計算する）。
 * 元のカメラの位置か目のマスが変わったとき（歩いた・向きを変えた等）は、その場で（毎フレームの、カメラの位置を決める処理の中で）
 * 求め直しを始める。ブロックの変化を反映するため、動かなくても{@value #REFRESH_MILLIS}ミリ秒ごとに求め直す。
 * 求め直している間は、前の結果を使う。
 *
 * <p>結果（{@link Result}）は、地形のシェーダー（assets/steadyview/shaders/include/visibility.glsl）と、モブ・チェスト等を描くか、
 * カーソルで狙えるかの判断で使う。範囲の外の点は、元のカメラの位置（または目）から点への線が範囲を出るマスで判断する。
 */
public final class VisibilityGrid {
	/** 範囲の一辺（ブロック）。シェーダーのSTEADYVIEW_GRID_SIZEと同じ値にする */
	public static final int SIZE = 48;
	public static final int CELLS = SIZE * SIZE * SIZE;
	/** 結果のビット列（int）の長さ */
	public static final int WORDS = CELLS / 32;
	/** ブロックの変化を反映するため、動かなくても求め直す間隔（ミリ秒） */
	private static final long REFRESH_MILLIS = 250;
	/** 求めた後、元のカメラの位置がこれ以上動いたら、結果を使わない（テレポート等） */
	private static final double MAX_DRIFT = 8.0;

	private static final byte AIR = 0;
	private static final byte NON_OPAQUE = 1;
	private static final byte OPAQUE = 2;

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Steady View visibility");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile @Nullable Result latest;
	private static @Nullable Future<?> running;
	private static long submittedAt;
	private static @Nullable BlockPos submittedCell;
	private static @Nullable BlockPos submittedEyeCell;

	private VisibilityGrid() {
	}

	/** 毎ティック呼ぶ（表示用のスレッド）。透かさなくなったら、結果を捨てる */
	public static void tick(final Minecraft minecraft) {
		if (minecraft.level == null || SeeThrough.viewpoint() == null || !SeeThrough.isActive(minecraft)) {
			latest = null;
			submittedCell = null;
		}
	}

	/**
	 * 毎フレーム、カメラの位置を決めた後に呼ぶ（表示用のスレッド。CameraMixin）。
	 * 元のカメラの位置か目のマスが変わったか、前回から{@value #REFRESH_MILLIS}ミリ秒たったら、ブロックの状態を写し取って求め直しを始める。
	 */
	public static void update(final Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		SeeThrough.Viewpoint viewpoint = SeeThrough.viewpoint();
		if (level == null || viewpoint == null || !SeeThrough.isActive(minecraft) || running != null && !running.isDone()) {
			return;
		}

		BlockPos cell = BlockPos.containing(viewpoint.original());
		BlockPos eyeCell = BlockPos.containing(viewpoint.eye());
		Result current = latest;
		long now = System.nanoTime();
		boolean moved = current == null || current.level != level || !cell.equals(submittedCell) || !eyeCell.equals(submittedEyeCell);
		if (!moved && now - submittedAt < REFRESH_MILLIS * 1_000_000L) {
			return;
		}

		submittedAt = now;
		submittedCell = cell;
		submittedEyeCell = eyeCell;
		BlockPos min = cell.offset(-SIZE / 2, -SIZE / 2, -SIZE / 2);
		Snapshot snapshot = Snapshot.take(level, min);
		running = EXECUTOR.submit(() -> {
			try {
				latest = compute(level, snapshot, viewpoint);
			} catch (RuntimeException e) {
				SteadyViewClient.LOGGER.warn("Failed to compute the visible area.", e);
			}
		});
	}

	/** 最後に求めた結果。まだないときはnull */
	public static @Nullable Result latest() {
		return latest;
	}

	private static Result compute(final Level level, final Snapshot snapshot, final SeeThrough.Viewpoint viewpoint) {
		byte[] cells = snapshot.cells();
		Vec3 eyeInGrid = viewpoint.eye().subtract(snapshot.minX, snapshot.minY, snapshot.minZ);
		Vec3 viewpointInGrid = leaveOpaqueCell(cells, viewpoint.original().subtract(snapshot.minX, snapshot.minY, snapshot.minZ), eyeInGrid);
		// 目がブロックの中にあるとき（窒息しているとき等）は、目からは調べない（そこから線を引くと、ブロックの向こうが見えてしまう）
		boolean useEye = inside(eyeInGrid.x, eyeInGrid.y, eyeInGrid.z) && !isOpaque(cells, eyeInGrid);
		int[] bits = new int[WORDS];
		// 高さごとに分けて同時に計算する（1つの高さのマス（SIZE×SIZE個）は、ビット列の別々のintに入るため、書き込みがぶつからない）
		IntStream.range(0, SIZE).parallel().forEach(y -> {
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int index = index(x, y, z);
					if (cells[index] == OPAQUE) {
						continue;
					}

					// 何もないマスで、周りにも何もなければ、中心だけで調べる（面が描かれないため、細かく調べなくてよい）
					boolean detailed = cells[index] != AIR || hasNonAirNeighbor(cells, x, y, z);
					if (cellVisible(cells, viewpointInGrid, x, y, z, detailed) || useEye && cellVisible(cells, eyeInGrid, x, y, z, detailed)) {
						bits[index >> 5] |= 1 << (index & 31);
					}
				}
			}
		});

		return new Result(level, snapshot.minX, snapshot.minY, snapshot.minZ, viewpointInGrid, useEye ? eyeInGrid : null, bits);
	}

	/**
	 * 元のカメラの位置が光を通さないブロックのマスにあるとき、目の方へ少しずつ動かして、マスの外に出す。
	 * マイクラ本体は、目の周り（±0.1ブロック）から後ろへ引いた8本の線が障害物に当たる距離のうち、一番近い距離までカメラを下げるため、
	 * カメラが障害物の面ちょうどか、わずかに中へ入った位置になることがある。
	 * 線を引くとき始まりのマスは調べないため、ブロックの中から線を引くと、そのブロックの向こう側まで見えてしまう。
	 */
	private static Vec3 leaveOpaqueCell(final byte[] cells, final Vec3 origin, final Vec3 eye) {
		Vec3 delta = eye.subtract(origin);
		double length = delta.length();
		if (length < 1.0E-6) {
			return origin;
		}

		for (double distance = 0.0; distance <= length; distance += 0.02) {
			Vec3 point = origin.add(delta.scale(distance / length));
			if (!isOpaque(cells, point)) {
				return point;
			}
		}

		return origin;
	}

	private static boolean isOpaque(final byte[] cells, final Vec3 point) {
		return inside(point.x, point.y, point.z) && cells[index((int)Math.floor(point.x), (int)Math.floor(point.y), (int)Math.floor(point.z))] == OPAQUE;
	}

	/** マスの番号。シェーダーと同じ並び（x、z、yの順に大きくなる） */
	private static int index(final int x, final int y, final int z) {
		return (y * SIZE + z) * SIZE + x;
	}

	private static boolean inside(final double x, final double y, final double z) {
		return x >= 0.0 && y >= 0.0 && z >= 0.0 && x < SIZE && y < SIZE && z < SIZE;
	}

	private static boolean hasNonAirNeighbor(final byte[] cells, final int x, final int y, final int z) {
		for (Direction direction : Direction.values()) {
			int nx = x + direction.getStepX();
			int ny = y + direction.getStepY();
			int nz = z + direction.getStepZ();
			if (nx >= 0 && ny >= 0 && nz >= 0 && nx < SIZE && ny < SIZE && nz < SIZE && cells[index(nx, ny, nz)] != AIR) {
				return true;
			}
		}

		return false;
	}

	/** 中心の点、detailedなら面の中心と角の近くの点まで、どれか1つに遮られずに線を引けるか */
	private static boolean cellVisible(final byte[] cells, final Vec3 from, final int x, final int y, final int z, final boolean detailed) {
		if (reachable(cells, from.x, from.y, from.z, x + 0.5, y + 0.5, z + 0.5)) {
			return true;
		}

		if (!detailed) {
			return false;
		}

		for (Direction direction : Direction.values()) {
			double px = x + 0.5 + direction.getStepX() * 0.45;
			double py = y + 0.5 + direction.getStepY() * 0.45;
			double pz = z + 0.5 + direction.getStepZ() * 0.45;
			if (reachable(cells, from.x, from.y, from.z, px, py, pz)) {
				return true;
			}
		}

		for (int corner = 0; corner < 8; corner++) {
			double px = x + ((corner & 1) == 0 ? 0.1 : 0.9);
			double py = y + ((corner & 2) == 0 ? 0.1 : 0.9);
			double pz = z + ((corner & 4) == 0 ? 0.1 : 0.9);
			if (reachable(cells, from.x, from.y, from.z, px, py, pz)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * fromからtoへの線が、光を通さないブロックのマスを通らないか（Amanatides-Wooの方法でマスをたどる）。
	 * 始まりのマス（fromのマス）は調べない。toのマスは光を通さないブロックでないこと。
	 */
	private static boolean reachable(
		final byte[] cells, final double fromX, final double fromY, final double fromZ, final double toX, final double toY, final double toZ
	) {
		int x = (int)Math.floor(fromX);
		int y = (int)Math.floor(fromY);
		int z = (int)Math.floor(fromZ);
		int targetX = (int)Math.floor(toX);
		int targetY = (int)Math.floor(toY);
		int targetZ = (int)Math.floor(toZ);
		double dx = toX - fromX;
		double dy = toY - fromY;
		double dz = toZ - fromZ;
		int stepX = dx > 0.0 ? 1 : dx < 0.0 ? -1 : 0;
		int stepY = dy > 0.0 ? 1 : dy < 0.0 ? -1 : 0;
		int stepZ = dz > 0.0 ? 1 : dz < 0.0 ? -1 : 0;
		double deltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
		double deltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
		double deltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
		double maxX = stepX > 0 ? (x + 1 - fromX) * deltaX : stepX < 0 ? (fromX - x) * deltaX : Double.MAX_VALUE;
		double maxY = stepY > 0 ? (y + 1 - fromY) * deltaY : stepY < 0 ? (fromY - y) * deltaY : Double.MAX_VALUE;
		double maxZ = stepZ > 0 ? (z + 1 - fromZ) * deltaZ : stepZ < 0 ? (fromZ - z) * deltaZ : Double.MAX_VALUE;
		for (int steps = 0; steps < SIZE * 3; steps++) {
			if (x == targetX && y == targetY && z == targetZ) {
				return true;
			}

			if (maxX < maxY && maxX < maxZ) {
				x += stepX;
				maxX += deltaX;
			} else if (maxY < maxZ) {
				y += stepY;
				maxY += deltaY;
			} else {
				z += stepZ;
				maxZ += deltaZ;
			}

			if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE || z >= SIZE) {
				return false;
			}

			if (cells[index(x, y, z)] == OPAQUE && !(x == targetX && y == targetY && z == targetZ)) {
				return false;
			}
		}

		return false;
	}

	/**
	 * 見えるマスの結果。座標はすべて範囲の角（minX, minY, minZ）からの相対位置。
	 * eyeは、目の位置が範囲の外にあって使わなかったときはnull。
	 */
	public record Result(Level level, int minX, int minY, int minZ, Vec3 viewpoint, @Nullable Vec3 eye, int[] bits) {
		/** 求めたときから元のカメラの位置が大きく動いていないか（動いていたら、この結果は使わない） */
		public boolean isCloseTo(final Vec3 original) {
			return original.subtract(this.minX, this.minY, this.minZ).distanceToSqr(this.viewpoint) < MAX_DRIFT * MAX_DRIFT;
		}

		/** ワールド内の点が見えるか（点のあるマスが、元のカメラの位置または目から見えるか）。シェーダーのsteadyViewPointVisibleと同じ計算 */
		public boolean isPointVisible(final double worldX, final double worldY, final double worldZ) {
			double x = worldX - this.minX;
			double y = worldY - this.minY;
			double z = worldZ - this.minZ;
			if (inside(x, y, z)) {
				return this.bit((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
			}

			// 範囲の外の点は、元のカメラの位置（または目）からの線が範囲を出るマスで判断する
			return this.exitCellVisible(this.viewpoint, x, y, z) || this.eye != null && this.exitCellVisible(this.eye, x, y, z);
		}

		/** ブロックの面の点が見えるか（面の手前のマスが見えるか）。地形のシェーダーと同じ判断 */
		public boolean isFaceVisible(final Vec3 location, final Direction face) {
			return this.isPointVisible(location.x + face.getStepX() * 0.02, location.y + face.getStepY() * 0.02, location.z + face.getStepZ() * 0.02);
		}

		/** 箱（エンティティの大きさ）の重なるマスのどれかが見えるか */
		public boolean isBoxVisible(final AABB box) {
			int fromX = (int)Math.floor(box.minX);
			int fromY = (int)Math.floor(box.minY);
			int fromZ = (int)Math.floor(box.minZ);
			int toX = (int)Math.floor(box.maxX);
			int toY = (int)Math.floor(box.maxY);
			int toZ = (int)Math.floor(box.maxZ);
			// とても大きいもの（エンダードラゴン等）は、中心だけで判断する
			if ((long)(toX - fromX + 1) * (toY - fromY + 1) * (toZ - fromZ + 1) > 64) {
				Vec3 center = box.getCenter();
				return this.isPointVisible(center.x, center.y, center.z);
			}

			for (int y = fromY; y <= toY; y++) {
				for (int z = fromZ; z <= toZ; z++) {
					for (int x = fromX; x <= toX; x++) {
						if (this.isPointVisible(x + 0.5, y + 0.5, z + 0.5)) {
							return true;
						}
					}
				}
			}

			return false;
		}

		private boolean bit(final int x, final int y, final int z) {
			int index = index(x, y, z);
			return (this.bits[index >> 5] >>> (index & 31) & 1) != 0;
		}

		private boolean exitCellVisible(final Vec3 from, final double x, final double y, final double z) {
			double dx = x - from.x;
			double dy = y - from.y;
			double dz = z - from.z;
			double t = Math.min(Math.min(exitT(from.x, dx), exitT(from.y, dy)), exitT(from.z, dz));
			int cellX = clamp((int)Math.floor(from.x + dx * t));
			int cellY = clamp((int)Math.floor(from.y + dy * t));
			int cellZ = clamp((int)Math.floor(from.z + dz * t));
			return this.bit(cellX, cellY, cellZ);
		}

		private static double exitT(final double from, final double delta) {
			if (delta > 1.0E-6) {
				return (SIZE - 0.001 - from) / delta;
			}

			if (delta < -1.0E-6) {
				return (0.001 - from) / delta;
			}

			return Double.MAX_VALUE;
		}

		private static int clamp(final int value) {
			return Math.max(0, Math.min(SIZE - 1, value));
		}
	}

	/**
	 * 範囲のブロックの状態の写し。表示用のスレッドで、範囲に重なるチャンクの区画（16ブロック四方）ごとに写し取る（配列の複製なので速い）。
	 * ワールドのデータは表示用のスレッドで書き換わるため、別のスレッドで直接読まない。
	 */
	private record Snapshot(int minX, int minY, int minZ, @Nullable PalettedContainer<BlockState>[] sections, int sectionsX, int sectionsY) {
		@SuppressWarnings("unchecked")
		static Snapshot take(final ClientLevel level, final BlockPos min) {
			int fromSectionX = SectionPos.blockToSectionCoord(min.getX());
			int fromSectionY = SectionPos.blockToSectionCoord(min.getY());
			int fromSectionZ = SectionPos.blockToSectionCoord(min.getZ());
			int toSectionX = SectionPos.blockToSectionCoord(min.getX() + SIZE - 1);
			int toSectionY = SectionPos.blockToSectionCoord(min.getY() + SIZE - 1);
			int toSectionZ = SectionPos.blockToSectionCoord(min.getZ() + SIZE - 1);
			int sectionsX = toSectionX - fromSectionX + 1;
			int sectionsY = toSectionY - fromSectionY + 1;
			int sectionsZ = toSectionZ - fromSectionZ + 1;
			PalettedContainer<BlockState>[] sections = new PalettedContainer[sectionsX * sectionsY * sectionsZ];
			for (int sz = 0; sz < sectionsZ; sz++) {
				for (int sx = 0; sx < sectionsX; sx++) {
					LevelChunk chunk = level.getChunkSource().getChunk(fromSectionX + sx, fromSectionZ + sz, ChunkStatus.FULL, false);
					if (chunk == null) {
						continue;
					}

					for (int sy = 0; sy < sectionsY; sy++) {
						int sectionY = fromSectionY + sy;
						if (sectionY < level.getMinSectionY() || sectionY > level.getMaxSectionY()) {
							continue;
						}

						LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
						if (!section.hasOnlyAir()) {
							sections[(sz * sectionsY + sy) * sectionsX + sx] = section.getStates().copy();
						}
					}
				}
			}

			return new Snapshot(min.getX(), min.getY(), min.getZ(), sections, sectionsX, sectionsY);
		}

		/** マスごとの種類（AIR・NON_OPAQUE・OPAQUE）。読み込まれていない所や、高さの範囲の外は空気として扱う */
		byte[] cells() {
			byte[] cells = new byte[CELLS];
			int fromSectionX = SectionPos.blockToSectionCoord(this.minX);
			int fromSectionY = SectionPos.blockToSectionCoord(this.minY);
			int fromSectionZ = SectionPos.blockToSectionCoord(this.minZ);
			// 高さごとに分けて同時に読む（写しは読むだけなので、同時に読んでも問題ない）
			IntStream.range(0, SIZE).parallel().forEach(y -> {
				int worldY = this.minY + y;
				int sy = SectionPos.blockToSectionCoord(worldY) - fromSectionY;
				for (int z = 0; z < SIZE; z++) {
					int worldZ = this.minZ + z;
					int sz = SectionPos.blockToSectionCoord(worldZ) - fromSectionZ;
					for (int x = 0; x < SIZE; x++) {
						int worldX = this.minX + x;
						int sx = SectionPos.blockToSectionCoord(worldX) - fromSectionX;
						PalettedContainer<BlockState> section = this.sections[(sz * this.sectionsY + sy) * this.sectionsX + sx];
						if (section == null) {
							continue;
						}

						BlockState state = section.get(worldX & 15, worldY & 15, worldZ & 15);
						cells[index(x, y, z)] = state.isSolidRender() ? OPAQUE : state.isAir() ? AIR : NON_OPAQUE;
					}
				}
			});

			return cells;
		}
	}
}
