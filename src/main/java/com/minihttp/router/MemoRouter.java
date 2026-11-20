package com.minihttp.router;

import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.MemoRepository.Memo;
import com.minihttp.service.MemoService;

import java.util.List;

public class MemoRouter extends BaseRouter {
    public record CreateMemoRequest(String content) {
    }

    private final MemoService memoService;

    public MemoRouter(MemoService memoService) {
        super("/memos");
        this.memoService = memoService;
    }

    @Get
    public List<Memo> list() {
        return this.memoService.list();
    }

    @Get("/{memoId}")
    public Memo get(long memoId) {
        return this.memoService.get(memoId);
    }

    @Post(status = HttpStatus.CREATED)
    public Memo create(CreateMemoRequest request) {
        return this.memoService.create(request.content());
    }
}
