package com.minihttp.model.dto;

public record ProjectResponse(long id, String title, long makerId, long likeCount, String createdAt) {
}
