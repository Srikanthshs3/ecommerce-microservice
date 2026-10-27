package org.praskanth.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    private static final Set<String> ALLOWED = Set.of("id", "createdAt");

    @Test
    void of_validInput_usesGivenValues() {
        Pageable p = PageRequests.of(2, 20, "createdAt", "asc", ALLOWED, "id");

        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void of_negativePage_clampedToZero() {
        assertThat(PageRequests.of(-5, 10, "id", "asc", ALLOWED, "id").getPageNumber()).isZero();
    }

    @Test
    void of_sizeAboveMax_clampedToMax() {
        assertThat(PageRequests.of(0, 101, "id", "asc", ALLOWED, "id").getPageSize())
                .isEqualTo(PageRequests.MAX_PAGE_SIZE);
        assertThat(PageRequests.of(0, 100, "id", "asc", ALLOWED, "id").getPageSize()).isEqualTo(100);
    }

    @Test
    void of_sizeBelowOne_clampedToOne() {
        assertThat(PageRequests.of(0, 0, "id", "asc", ALLOWED, "id").getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(0, -3, "id", "asc", ALLOWED, "id").getPageSize()).isEqualTo(1);
    }

    @Test
    void of_unknownSortProperty_fallsBackToDefault() {
        Pageable p = PageRequests.of(0, 10, "password", "asc", ALLOWED, "id");

        assertThat(p.getSort().getOrderFor("password")).isNull();
        assertThat(p.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void of_nullSortProperty_fallsBackToDefault() {
        Pageable p = PageRequests.of(0, 10, null, "asc", ALLOWED, "createdAt");

        assertThat(p.getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void of_directionIsCaseInsensitiveAndDefaultsToDesc() {
        assertThat(PageRequests.of(0, 10, "id", "ASC", ALLOWED, "id").getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(PageRequests.of(0, 10, "id", "whatever", ALLOWED, "id").getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(PageRequests.of(0, 10, "id", null, ALLOWED, "id").getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
