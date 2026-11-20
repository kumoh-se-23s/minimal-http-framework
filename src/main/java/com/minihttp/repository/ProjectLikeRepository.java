package com.minihttp.repository;

import com.minihttp.model.ProjectLike;

import java.util.List;

public class ProjectLikeRepository extends InMemoryRepository<ProjectLike> {
    public List<ProjectLike> findByProjectAndUser(long projectId, long userId) {
        return this.find(like -> like.projectId() == projectId && like.userId() == userId);
    }

    public long countByProject(long projectId) {
        return this.find(like -> like.projectId() == projectId).size();
    }
}
