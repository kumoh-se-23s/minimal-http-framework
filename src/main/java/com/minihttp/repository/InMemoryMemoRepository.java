package com.minihttp.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMemoRepository implements MemoRepository {
    private final Map<Long, Memo> memos = new ConcurrentSkipListMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Memo save(String content, String createdAt) {
        Memo memo = new Memo(this.sequence.incrementAndGet(), content, createdAt);
        this.memos.put(memo.id(), memo);
        return memo;
    }

    @Override
    public Optional<Memo> findById(long id) {
        return Optional.ofNullable(this.memos.get(id));
    }

    @Override
    public List<Memo> findAll() {
        return List.copyOf(this.memos.values());
    }
}
