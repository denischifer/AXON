package dev.denischifer.axon.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class SpatialGrid {
    private static final Map<Long, ChunkMaskData> MASTER_MAP = new Long2ObjectOpenHashMap<>();
    private static final AtomicReference<Map<Long, ChunkMaskData>> SNAPSHOT = new AtomicReference<>(new Long2ObjectOpenHashMap<>());
    private static final ReentrantLock[] STRIPES = new ReentrantLock[64];
    private static final AtomicBoolean dirty = new AtomicBoolean(true);

    static {
        for (int i = 0; i < 64; i++) STRIPES[i] = new ReentrantLock();
    }

    private static ReentrantLock getLock(long key) {
        return STRIPES[(int) (key & 63)];
    }

    private static long getChunkKey(int x, int y, int z) {
        return ((long) (x >> 4) & 0x3FFFFFL) << 42 |
                ((long) (y >> 4) & 0xFFFFFL) << 21 |
                ((long) (z >> 4) & 0x3FFFFFL);
    }

    public static void setBlock(int x, int y, int z, boolean isFull, boolean isComplex) {
        long key = getChunkKey(x, y, z);
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            ChunkMaskData data = MASTER_MAP.get(key);
            if (data == null) {
                data = new ChunkMaskData();
                MASTER_MAP.put(key, data);
            }

            int rx = x & 15, ry = y & 15, rz = z & 15;
            int bitPos = (ry << 8) | (rz << 4) | rx;
            int idx = bitPos >> 6;
            long bit = 1L << (bitPos & 63);

            boolean currentFull = (data.getFullMask()[idx] & bit) != 0;
            boolean currentComplex = (data.getComplexMask()[idx] & bit) != 0;

            if (currentFull != isFull || currentComplex != isComplex) {
                data.setBlock(rx, ry, rz, isFull, isComplex);
                dirty.set(true);
            }
        } finally {
            lock.unlock();
        }
    }

    public static void unloadChunkSection(int cx, int cy, int cz) {
        long key = ((long) cx & 0x3FFFFFL) << 42 |
                ((long) cy & 0xFFFFFL) << 21 |
                ((long) cz & 0x3FFFFFL);
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            ChunkMaskData data = MASTER_MAP.remove(key);
            if (data != null) {
                data.release();
                dirty.set(true);
            }
        } finally {
            lock.unlock();
        }
    }

    public static boolean isAreaEmpty(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Map<Long, ChunkMaskData> map = SNAPSHOT.get();
        if (map.isEmpty()) return true;

        int xStart = (int) Math.floor(minX - 1.0E-7);
        int yStart = (int) Math.floor(minY - 1.0E-7);
        int zStart = (int) Math.floor(minZ - 1.0E-7);
        int xEnd = (int) Math.floor(maxX + 1.0E-7);
        int yEnd = (int) Math.floor(maxY + 1.0E-7);
        int zEnd = (int) Math.floor(maxZ + 1.0E-7);

        for (int y = yStart; y <= yEnd; y++) {
            int chunkY = y >> 4;
            int relY = y & 15;
            for (int z = zStart; z <= zEnd; z++) {
                int chunkZ = z >> 4;
                int relZ = z & 15;
                for (int x = xStart; x <= xEnd; x++) {
                    long key = getChunkKey(x, y, z);
                    ChunkMaskData data = map.get(key);
                    if (data == null) continue;
                    int bitPos = (relY << 8) | (relZ << 4) | (x & 15);
                    if ((data.getFullMask()[bitPos >> 6] & (1L << (bitPos & 63))) != 0 ||
                            (data.getComplexMask()[bitPos >> 6] & (1L << (bitPos & 63))) != 0) return false;
                }
            }
        }
        return true;
    }

    public static boolean isComplexCollision(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Map<Long, ChunkMaskData> map = SNAPSHOT.get();
        if (map.isEmpty()) return false;

        int xStart = (int) Math.floor(minX - 1.0E-7);
        int yStart = (int) Math.floor(minY - 1.0E-7);
        int zStart = (int) Math.floor(minZ - 1.0E-7);
        int xEnd = (int) Math.floor(maxX + 1.0E-7);
        int yEnd = (int) Math.floor(maxY + 1.0E-7);
        int zEnd = (int) Math.floor(maxZ + 1.0E-7);

        for (int y = yStart; y <= yEnd; y++) {
            for (int z = zStart; z <= zEnd; z++) {
                for (int x = xStart; x <= xEnd; x++) {
                    long key = getChunkKey(x, y, z);
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
        if (!dirty.compareAndSet(true, false)) return;

        Map<Long, ChunkMaskData> copy = new Long2ObjectOpenHashMap<>();
        synchronized (MASTER_MAP) {
            MASTER_MAP.forEach((k, v) -> {
                if (!v.isEmpty()) {
                    copy.put(k, new ChunkMaskData(v.getFullMask(), v.getComplexMask()));
                }
            });
        }
        Map<Long, ChunkMaskData> old = SNAPSHOT.getAndSet(copy);
        if (old != null) {
            old.values().forEach(ChunkMaskData::release);
        }
    }
}