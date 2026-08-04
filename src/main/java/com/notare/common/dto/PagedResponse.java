package com.notare.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A clean, reusable pagination envelope for any paginated endpoint.
 *
 * Why this exists: Spring Data's Page is convenient on the server, but
 * serializing it straight to JSON exposes its internal shape (and logs a
 * warning about an unstable contract). This record is OUR stable API contract.
 * It's generic (<T>), so notes, and later voice notes, search results, etc.,
 * all reuse the same wrapper.
 *
 * `page` is zero-based, matching Spring Data (the first page is page 0).
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    /**
     * Builds the envelope from an already-mapped content list plus the metadata
     * of the source Page. We pass the mapped `content` in separately because the
     * Page holds entities (Note), while the response should hold DTOs
     * (NoteSummaryResponse) — the mapping happens in the service before this call.
     */
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