package com.notare.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1d of the coverage roadmap.
 *
 * <p>Covers {@link PagedResponse#of(List, Page)} - the wrapper every
 * paginated endpoint in the app returns through. Its own doc comment calls
 * out the one thing worth locking in with a test: {@code content} is taken
 * from the caller's already-mapped list, NOT re-derived from {@code
 * source.getContent()} - the Page passed in generally holds entities, the
 * response is supposed to hold DTOs, and this method must never quietly
 * reach back into the Page for its content instead of using what it was
 * given. Everything else is pass-through metadata (page number, size,
 * totals, hasNext/hasPrevious), sourced from Spring Data's own {@link
 * PageImpl}, whose {@code totalPages}/{@code hasNext}/{@code hasPrevious}
 * math is exercised at three points (first page, a middle page, the last
 * page) rather than trusted blind.
 */
class PagedResponseTest {

    @Test
    void of_mapsMetadataFromTheSourcePage() {
        List<String> content = List.of("a", "b", "c");
        Pageable pageable = PageRequest.of(0, 3);
        Page<String> source = new PageImpl<>(content, pageable, 10);

        PagedResponse<String> result = PagedResponse.of(content, source);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(4); // ceil(10 / 3)
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void of_contentIsTheSuppliedListNotTheSourcePagesOwnContent() {
        // The source Page carries "entities"; the content passed to of() is
        // the already-mapped "DTOs". Different types, different values -
        // the response must reflect what was handed to it, not reach back
        // into the Page for its own content.
        List<Integer> sourceEntities = List.of(1, 2, 3);
        Pageable pageable = PageRequest.of(0, 3);
        Page<Integer> source = new PageImpl<>(sourceEntities, pageable, 3);

        List<String> mappedDtos = List.of("one", "two", "three");

        PagedResponse<String> result = PagedResponse.of(mappedDtos, source);

        assertThat(result.content()).isSameAs(mappedDtos);
        assertThat(result.content()).containsExactly("one", "two", "three");
    }

    @Test
    void of_emptyPageProducesEmptyContentAndZeroedPaginationFields() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<String> source = new PageImpl<>(List.of(), pageable, 0);

        PagedResponse<String> result = PagedResponse.of(List.of(), source);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void of_middlePageHasBothHasNextAndHasPrevious() {
        Pageable pageable = PageRequest.of(1, 2);
        Page<String> source = new PageImpl<>(List.of("c", "d"), pageable, 6);

        PagedResponse<String> result = PagedResponse.of(List.of("c", "d"), source);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(3); // ceil(6 / 2)
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    void of_lastPageHasNoNextButHasPrevious() {
        // size=2, total=5 -> 3 pages (0, 1, 2), the last holding a single
        // leftover element. Page index 2 is the last one: nothing after it.
        Pageable pageable = PageRequest.of(2, 2);
        Page<String> source = new PageImpl<>(List.of("e"), pageable, 5);

        PagedResponse<String> result = PagedResponse.of(List.of("e"), source);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    void of_firstPageHasNoPreviousEvenWithMorePagesAfterIt() {
        Pageable pageable = PageRequest.of(0, 2);
        Page<String> source = new PageImpl<>(List.of("a", "b"), pageable, 6);

        PagedResponse<String> result = PagedResponse.of(List.of("a", "b"), source);

        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.hasNext()).isTrue();
    }
}