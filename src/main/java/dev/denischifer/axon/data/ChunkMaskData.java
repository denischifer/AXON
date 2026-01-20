package dev.denischifer.axon.data;

import lombok.Getter;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkMaskData {
    private static final ConcurrentLinkedQueue<long[]> POOL = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger POOL_SIZE = new AtomicInteger(0);
    private static final int MAX_POOL_SIZE = 1024;

    @Getter
    private long[] fullMask;
    @Getter
    private long[] complexMask;
    private int solidBlocks = 0;

    public ChunkMaskData() {
        this.fullMask = POOL.poll();
        if (this.fullMask == null) {
            this.fullMask = new long[64];
        } else {
            POOL_SIZE.decrementAndGet();
            Arrays.fill(this.fullMask, 0L);
        }

        this.complexMask = POOL.poll();
        if (this.complexMask == null) {
            this.complexMask = new long[64];
        } else {
            POOL_SIZE.decrementAndGet();
            Arrays.fill(this.complexMask, 0L);
        }
    }

    public ChunkMaskData(long[] full, long[] complex) {
        this();
        System.arraycopy(full, 0, this.fullMask, 0, 64);
        System.arraycopy(complex, 0, this.complexMask, 0, 64);
        for (long l : this.fullMask) if (l != 0) solidBlocks++;
        for (long l : this.complexMask) if (l != 0) solidBlocks++;
    }

    public void setBlock(int x, int y, int z, boolean isFull, boolean isComplex) {
        int bitPos = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        int index = bitPos >> 6;
        long bit = 1L << (bitPos & 63);

        boolean wasSolid = (fullMask[index] & bit) != 0 || (complexMask[index] & bit) != 0;

        if (isFull) fullMask[index] |= bit;
        else fullMask[index] &= ~bit;

        if (isComplex) complexMask[index] |= bit;
        else complexMask[index] &= ~bit;

        boolean isSolid = isFull || isComplex;
        if (!wasSolid && isSolid) solidBlocks++;
        else if (wasSolid && !isSolid) solidBlocks--;
    }

    public boolean isEmpty() {
        return solidBlocks == 0;
    }

    public void release() {
        if (this.fullMask != null && POOL_SIZE.get() < MAX_POOL_SIZE) {
            POOL.offer(this.fullMask);
            POOL_SIZE.incrementAndGet();
        }
        if (this.complexMask != null && POOL_SIZE.get() < MAX_POOL_SIZE) {
            POOL.offer(this.complexMask);
            POOL_SIZE.incrementAndGet();
        }
        this.fullMask = null;
        this.complexMask = null;
    }
}