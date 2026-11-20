package com.minihttp.service;

import com.minihttp.model.Project;
import com.minihttp.repository.ProjectLikeRepository;

public final class LikeCountRanking extends ProjectRanking {
    private final ProjectLikeRepository projectLikeRepository;

    public LikeCountRanking(ProjectLikeRepository projectLikeRepository) {
        this.projectLikeRepository = projectLikeRepository;
    }

    @Override
    protected long score(Project project) {
        return this.projectLikeRepository.countByProject(project.id());
    }
}
