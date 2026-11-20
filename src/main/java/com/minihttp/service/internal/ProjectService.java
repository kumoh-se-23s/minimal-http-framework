package com.minihttp.service.internal;

import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.infra.AuditLog;
import com.minihttp.model.ProjectLike;
import com.minihttp.model.dto.DeleteResponse;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.ProjectLikeRepository;
import com.minihttp.repository.ProjectRepository;

import java.util.List;

public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final AuditLog auditLog;

    public ProjectService(ProjectRepository projectRepository, ProjectLikeRepository projectLikeRepository, AuditLog auditLog) {
        this.projectRepository = projectRepository;
        this.projectLikeRepository = projectLikeRepository;
        this.auditLog = auditLog;
    }

    public DeleteResponse deleteProject(long projectId, long adminId) {
        if (!this.projectRepository.delete(projectId)) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }

        List<ProjectLike> likes = this.projectLikeRepository.find(like -> like.projectId() == projectId);
        likes.forEach(like -> this.projectLikeRepository.delete(like.id()));
        this.auditLog.record("user " + adminId + " deleted project " + projectId);
        return new DeleteResponse(true);
    }

    public List<String> getAuditEntries() {
        return this.auditLog.entries();
    }
}
