package com.minihttp.repository;

import java.util.List;
import java.util.Optional;

public interface MemoRepository {
    record Memo(long id, String content, String createdAt) {
    }

    Memo save(String content, String createdAt);

    Optional<Memo> findById(long id);

    List<Memo> findAll();
}
