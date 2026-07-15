package com.gatekeeper.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagination envelope shared by every paginated list endpoint (Milestone 5
 * Architecture, Section 3). Kept separate from ApiResponse&lt;T&gt; itself -
 * ApiResponse is the frozen success/message/data envelope every endpoint
 * already uses, and pagination metadata is a property of the payload, not of
 * response success. A paginated endpoint's body is ApiResponse&lt;PageResponse&lt;X&gt;&gt;.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
