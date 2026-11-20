package com.minihttp.service;

import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.MemoRepository;
import com.minihttp.repository.MemoRepository.Memo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public final class MemoService {
    private final MemoRepository memoRepository;
    private final Clock clock;

    public MemoService(MemoRepository memoRepository, Clock clock) {
        this.memoRepository = memoRepository;
        this.clock = clock;
    }

    public Memo create(String content) {
        return this.memoRepository.save(content, LocalDateTime.now(this.clock).toString());
    }

    public Memo get(long memoId) {
        return this.memoRepository.findById(memoId)
                                  .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Memo not found"));
    }

    public List<Memo> list() {
        return this.memoRepository.findAll();
    }
}
