package com.minihttp.controller.external;

import com.minihttp.auth.CurrentUser;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Delete;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.model.dto.DeleteResponse;
import com.minihttp.model.dto.IdResponse;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.service.external.ProjectLikeService;

public class ProjectLikeRouter extends BaseRouter {
    private final ProjectLikeService projectLikeService;

    public ProjectLikeRouter(ProjectLikeService projectLikeService) {
        super("/project/like");
        this.projectLikeService = projectLikeService;
    }

    @Post(value = "/{project_id}", status = HttpStatus.CREATED)
    public IdResponse createProjectLike(@Path("project_id") Long projectId, @Depends(CurrentUser.class) Long userId) {
        return this.projectLikeService.createProjectLike(projectId, userId);
    }

    @Delete("/{project_id}")
    public DeleteResponse deleteProjectLike(@Path("project_id") Long projectId, @Depends(CurrentUser.class) Long userId) {
        return this.projectLikeService.deleteProjectLike(projectId, userId);
    }
}
