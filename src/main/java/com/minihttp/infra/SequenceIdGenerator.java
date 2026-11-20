package com.minihttp.infra;

import java.util.concurrent.atomic.AtomicLong;

public final class SequenceIdGenerator implements IdGenerator {
    private final AtomicLong sequence;

    public SequenceIdGenerator(long firstId) {
        this.sequence = new AtomicLong(firstId - 1);
    }

    @Override
    public long nextId() {
        return this.sequence.incrementAndGet();
    }
}
