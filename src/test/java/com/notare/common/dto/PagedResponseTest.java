package com.notare.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for PagedResponse. */
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
        assertThat(result.totalPages()).isEqualTo(4);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void of_contentIsTheSuppliedListNotTheSourcePagesOwnContent() {
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
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    void of_lastPageHasNoNextButHasPrevious() {
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
