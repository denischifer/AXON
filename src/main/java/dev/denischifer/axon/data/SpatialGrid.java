package dev.denischifer.axon.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class SpatialGrid {
    private static final Map<Long, ChunkMaskData> MASTER_MAP = new Long2ObjectOpenHashMap<>();
    private static final AtomicReference<Map<Long, ChunkMaskData>> SNAPSHOT = new AtomicReference<>(new Long2ObjectOpenHashMap<>());
    private static final ReentrantLock[] STRIPES = new ReentrantLock[64];
    private static volatile boolean dirty = true;

    private static final AtomicLong TOTAL_QUERIES = new AtomicLong(0);
    private static final AtomicLong SKIPPED_QUERIES = new AtomicLong(0);

    static {
        for (int i = 0; i < 64; i++) STRIPES[i] = new ReentrantLock();
    }

    private static ReentrantLock getLock(long key) {
        return STRIPES[(int) (Math.abs(key) % 64)];
    }

    public static void setBlock(int x, int y, int z, boolean isFull, boolean isComplex) {
        long key = (long) (x >> 4) << 42 | (long) (y >> 4) << 21 | (long) (z >> 4);
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            MASTER_MAP.computeIfAbsent(key, k -> new ChunkMaskData()).setBlock(x & 15, y & 15, z & 15, isFull, isComplex);
            dirty = true;
        } finally {
            lock.unlock();
        }
    }

    public static void unloadChunkSection(int cx, int cy, int cz) {
        long key = (long) cx << 42 | (long) cy << 21 | (long) cz;
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            ChunkMaskData data = MASTER_MAP.remove(key);
            if (data != null) {
                data.release();
                dirty = true;
            }
        } finally {
            lock.unlock();
        }
    }

    public static boolean isAreaEmpty(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        TOTAL_QUERIES.incrementAndGet();
        Map<Long, ChunkMaskData> map = SNAPSHOT.get();
        if (map == null || map.isEmpty()) {
            SKIPPED_QUERIES.incrementAndGet();
            return true;
        }

        int xStart = (int) Math.floor(minX - 0.001);
        int yStart = (int) Math.floor(minY - 0.001);
        int zStart = (int) Math.floor(minZ - 0.001);
        int xEnd = (int) Math.floor(maxX + 0.001);
        int yEnd = (int) Math.floor(maxY + 0.001);
        int zEnd = (int) Math.floor(maxZ + 0.001);

        for (int y = yStart; y <= yEnd; y++) {
            int chunkY = y >> 4;
            int relY = y & 15;
            for (int z = zStart; z <= zEnd; z++) {
                int chunkZ = z >> 4;
                int relZ = z & 15;
                for (int x = xStart; x <= xEnd; x++) {
                    long key = (long) (x >> 4) << 42 | (long) chunkY << 21 | (long) chunkZ;
                    ChunkMaskData data = map.get(key);
                    if (data == null) continue;

                    int bitPos = (relY << 8) | (relZ << 4) | (x & 15);
                    int idx = bitPos >> 6;
                    long bit = 1L << (bitPos & 63);

                    if ((data.getFullMask()[idx] & bit) != 0 || (data.getComplexMask()[idx] & bit) != 0) return false;
                }
            }
        }
        SKIPPED_QUERIES.incrementAndGet();
        return true;
    }

    public static boolean isComplexCollision(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Map<Long, ChunkMaskData> map = SNAPSHOT.get();
        if (map == null) return false;

        int xStart = (int) Math.floor(minX - 0.001);
        int yStart = (int) Math.floor(minY - 0.001);
        int zStart = (int) Math.floor(minZ - 0.001);
        int xEnd = (int) Math.floor(maxX + 0.001);
        int yEnd = (int) Math.floor(maxY + 0.001);
        int zEnd = (int) Math.floor(maxZ + 0.001);

        for (int y = yStart; y <= yEnd; y++) {
            for (int z = zStart; z <= zEnd; z++) {
                for (int x = xStart; x <= xEnd; x++) {
                    long key = (long) (x >> 4) << 42 | (long) (y >> 4) << 21 | (long) (z >> 4);
                    ChunkMaskData data = map.get(key);
                    if (data == null) continue;
                    int bitPos = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                    if ((data.getComplexMask()[bitPos >> 6] & (1L << (bitPos & 63))) != 0) return true;
                }
            }
        }
        return false;
    }

    public static void createSnapshot() {
        if (!dirty) return;
        synchronized (MASTER_MAP) {
            Map<Long, ChunkMaskData> copy = new Long2ObjectOpenHashMap<>();
            MASTER_MAP.forEach((k, v) -> {
                if (!v.isEmpty()) copy.put(k, new ChunkMaskData(v.getFullMask(), v.getComplexMask()));
            });
            Map<Long, ChunkMaskData> old = SNAPSHOT.get();
            if (old != null) old.values().forEach(ChunkMaskData::release);
            SNAPSHOT.set(copy);
            dirty = false;
        }
    }
}