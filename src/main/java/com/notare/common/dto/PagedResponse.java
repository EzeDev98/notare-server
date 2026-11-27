package com.notare.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** A page of results with its paging details. */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static <T> PagedResponse<T> of(List<T> content, Page<?> source) {
        return new PagedResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext(),
                source.hasPrevious()
        );
    }
}
