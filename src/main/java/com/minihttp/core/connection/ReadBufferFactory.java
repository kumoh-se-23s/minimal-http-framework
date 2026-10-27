package com.minihttp.core.connection;

import com.minihttp.core.utils.GenericFactory;

import java.util.Arrays;


public final class ReadBufferFactory implements GenericFactory<byte[]> {
    private final int bufferSize;

    public ReadBufferFactory(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    public static ReadBufferFactory createDefaultBufferFactory() {
        return new ReadBufferFactory(32);
    }

    @Override
    public byte[] create() {
        return new byte[this.bufferSize];
    }

    @Override
    public void reset(byte[] object) {
        Arrays.fill(object, (byte) 0);
    }

    @Override
    public void destroy(byte[] target) {
        // byte[]는 GC가 치운다. 할 일 없음.
    }
}
