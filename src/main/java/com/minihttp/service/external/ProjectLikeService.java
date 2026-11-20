package com.minihttp.service.external;

import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.model.ProjectLike;
import com.minihttp.model.dto.DeleteResponse;
import com.minihttp.model.dto.IdResponse;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.ProjectLikeRepository;
import com.minihttp.repository.ProjectRepository;

import java.util.List;

public class ProjectLikeService {
    private final ProjectLikeRepository projectLikeRepository;
    private final ProjectRepository projectRepository;

    public ProjectLikeService(ProjectLikeRepository projectLikeRepository, ProjectRepository projectRepository) {
        this.projectLikeRepository = projectLikeRepository;
        this.projectRepository = projectRepository;
    }

    public IdResponse createProjectLike(long projectId, long userId) {
        this.requireProject(projectId);

        if (!this.projectLikeRepository.findByProjectAndUser(projectId, userId).isEmpty()) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "Already liked");
        }

        ProjectLike like = this.projectLikeRepository.create(id -> new ProjectLike(id, projectId, userId));
        return new IdResponse(like.id());
    }

    public DeleteResponse deleteProjectLike(long projectId, long userId) {
        this.requireProject(projectId);
        List<ProjectLike> likes = this.projectLikeRepository.findByProjectAndUser(projectId, userId);

        if (likes.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Like not found");
        }

        likes.forEach(like -> this.projectLikeRepository.delete(like.id()));
        return new DeleteResponse(true);
    }

    private void requireProject(long projectId) {
        if (this.projectRepository.findById(projectId).isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
