package com.minihttp.controller.internal;

import com.minihttp.auth.CurrentUser;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Delete;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.infra.ConnectionPool;
import com.minihttp.model.dto.DeleteResponse;
import com.minihttp.service.internal.ProjectService;

import java.util.List;

public class AdminProjectRouter extends BaseRouter {
    private final ProjectService projectService;
    private final ConnectionPool connectionPool;

    public AdminProjectRouter(ProjectService projectService, ConnectionPool connectionPool) {
        super("/admin/project");
        this.projectService = projectService;
        this.connectionPool = connectionPool;
    }

    @Delete("/{project_id}")
    public DeleteResponse deleteProject(@Path("project_id") Long projectId, @Depends(CurrentUser.class) Long adminId) {
        return this.projectService.deleteProject(projectId, adminId);
    }

    @Get("/audit")
    public List<String> getAuditEntries(@Depends(CurrentUser.class) Long adminId) {
        return this.projectService.getAuditEntries();
    }

    @Get("/pool")
    public ConnectionPool.Status getPoolStatus(@Depends(CurrentUser.class) Long adminId) {
        return this.connectionPool.status();
    }
}
