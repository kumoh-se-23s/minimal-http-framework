package com.minihttp.repository;

import com.minihttp.model.Project;

import java.util.List;

public class ProjectRepository extends InMemoryRepository<Project> {
    public List<Project> findPage(int page, int pageSize) {
        return this.findAll().stream()
                   .skip((long) (page - 1) * pageSize)
                   .limit(pageSize)
                   .toList();
    }
}
