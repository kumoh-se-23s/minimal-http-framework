package com.minihttp.service.external;

import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.infra.AppSettings;
import com.minihttp.infra.NotificationSender;
import com.minihttp.model.Project;
import com.minihttp.model.dto.IdResponse;
import com.minihttp.model.dto.ProjectResponse;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.ProjectLikeRepository;
import com.minihttp.repository.ProjectRepository;
import com.minihttp.service.ProjectRanking;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class ProjectService {
    private static final int POPULAR_LIMIT = 3;

    private final ProjectRepository projectRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final ProjectRanking projectRanking;
    private final NotificationSender notificationSender;
    private final AppSettings appSettings;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectLikeRepository projectLikeRepository,
                          ProjectRanking projectRanking,
                          NotificationSender notificationSender,
                          AppSettings appSettings,
                          Clock clock) {
        this.projectRepository = projectRepository;
        this.projectLikeRepository = projectLikeRepository;
        this.projectRanking = projectRanking;
        this.notificationSender = notificationSender;
        this.appSettings = appSettings;
        this.clock = clock;
    }

    public IdResponse createProject(long makerId, String title) {
        if (title == null || title.isBlank()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }

        String createdAt = LocalDateTime.now(this.clock).toString();
        Project project = this.projectRepository.create(id -> new Project(id, title, makerId, createdAt));
        this.notificationSender.send("project " + project.id() + " created by user " + makerId);
        return new IdResponse(project.id());
    }

    public ProjectResponse getProject(long projectId) {
        return this.projectRepository.findById(projectId)
                                     .map(this::toResponse)
                                     .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public List<ProjectResponse> getProjects(int page) {
        if (page <= 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "page must be positive");
        }

        return this.projectRepository.findPage(page, this.appSettings.pageSize()).stream()
                                     .map(this::toResponse)
                                     .toList();
    }

    public List<ProjectResponse> getPopularProjects() {
        return this.projectRanking.rank(this.projectRepository.findAll(), POPULAR_LIMIT).stream()
                                  .map(this::toResponse)
                                  .toList();
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.id(),
                                   project.title(),
                                   project.makerId(),
                                   this.projectLikeRepository.countByProject(project.id()),
                                   project.createdAt());
    }
}
