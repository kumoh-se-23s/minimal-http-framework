package com.minihttp.controller.external;

import com.minihttp.auth.CurrentUser;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.model.dto.CreateProjectRequest;
import com.minihttp.model.dto.IdResponse;
import com.minihttp.model.dto.ProjectResponse;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.service.external.ProjectService;

import java.util.List;

public class ProjectRouter extends BaseRouter {
    private final ProjectService projectService;

    public ProjectRouter(ProjectService projectService) {
        super("/project");
        this.projectService = projectService;
    }

    @Post(status = HttpStatus.CREATED)
    public IdResponse createProject(CreateProjectRequest dto, @Depends(CurrentUser.class) Long userId) {
        return this.projectService.createProject(userId, dto.title());
    }

    @Get
    public List<ProjectResponse> getProjects(@Query(defaultValue = "1") int page) {
        return this.projectService.getProjects(page);
    }

    @Get("/popular")
    public List<ProjectResponse> getPopularProjects() {
        return this.projectService.getPopularProjects();
    }

    @Get("/{project_id}")
    public ProjectResponse getProject(@Path("project_id") Long projectId) {
        return this.projectService.getProject(projectId);
    }
}
