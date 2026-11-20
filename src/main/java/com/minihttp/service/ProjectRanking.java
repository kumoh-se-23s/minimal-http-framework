package com.minihttp.service;

import com.minihttp.model.Project;

import java.util.Comparator;
import java.util.List;

public abstract class ProjectRanking {
    public final List<Project> rank(List<Project> projects, int limit) {
        return projects.stream()
                       .sorted(Comparator.comparingLong(this::score).reversed())
                       .limit(limit)
                       .toList();
    }

    protected abstract long score(Project project);
}
