package org.praskanth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Tunables, bound from the "category.*" keys in application.yml / the config-server. */
@Data
@Component
@ConfigurationProperties(prefix = "category")
public class CategoryProperties {

    /** Depth of the tree. 3 = department > category > brand. */
    private int maxLevel = 3;

    /**
     * Levels a SELLER may create. Admins may always create any level.
     * Default 1,2,3 (sellers can build the whole tree). A stricter, marketplace-style policy is "3":
     * admins own departments and categories, sellers only add brands.
     */
    private Set<Integer> sellerAllowedLevels = Set.of(1, 2, 3);

    /** How long the public tree is cached in memory (it changes rarely and is read on every page view). */
    private int treeCacheSeconds = 60;

    private Seed seed = new Seed();

    @Data
    public static class Seed {
        /** Insert the starter taxonomy when the collection is empty. Turn off once real data exists. */
        private boolean enabled = false;
    }
}
