package org.praskanth.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Builds safe Pageables: bounded page size and whitelisted sort properties (no arbitrary property names from the client). */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {}

    public static Pageable of(int page, int size, String sortBy, String direction,
                              Set<String> allowedSortProperties, String defaultSortProperty) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String property = sortBy != null && allowedSortProperties.contains(sortBy) ? sortBy : defaultSortProperty;
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(dir, property));
    }
}
