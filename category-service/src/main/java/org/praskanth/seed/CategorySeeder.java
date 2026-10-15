package org.praskanth.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.praskanth.entity.Category;
import org.praskanth.entity.CategoryAncestor;
import org.praskanth.repository.CategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * First-run seed of the starter taxonomy (see CategoryTaxonomy). Runs only when category.seed.enabled=true AND the
 * collection is empty, so restarts never duplicate or overwrite anything an admin has since changed.
 */
@Component
@ConditionalOnProperty(name = "category.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CategorySeeder implements ApplicationRunner {

    /** createdBy for seeded rows: 0 = "the system", so only admins can edit them. */
    private static final long SYSTEM_USER = 0L;

    private final CategoryRepository categoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            log.info("Category seed skipped: collection is not empty");
            return;
        }

        List<CategoryTaxonomy.Flat> flat = CategoryTaxonomy.flatten(CategoryTaxonomy.departments());
        List<Category> built = new ArrayList<>(flat.size());
        Instant now = Instant.now();

        for (CategoryTaxonomy.Flat f : flat) {
            Category parent = f.parentIndex() < 0 ? null : built.get(f.parentIndex()); // parents are always built first

            List<CategoryAncestor> ancestors = new ArrayList<>();
            List<String> ancestorIds = new ArrayList<>();
            if (parent != null) {
                ancestors.addAll(parent.getAncestors());
                ancestors.add(CategoryAncestor.builder()
                        .id(parent.getId()).name(parent.getName()).slug(parent.getSlug()).level(parent.getLevel()).build());
                ancestorIds.addAll(parent.getAncestorIds());
                ancestorIds.add(parent.getId());
            }

            built.add(Category.builder()
                    .id(new ObjectId().toHexString())
                    .name(f.name())
                    .nameKey(f.name().toLowerCase(java.util.Locale.ROOT))
                    .slug(f.slug())
                    .slugPath(f.slugPath())
                    .icon(f.icon())
                    .parentId(parent == null ? null : parent.getId())
                    .level(f.level())
                    .ancestors(ancestors)
                    .ancestorIds(ancestorIds)
                    .childCount(f.childCount())
                    .displayOrder(f.displayOrder())
                    .active(true)
                    .createdBy(SYSTEM_USER)
                    .createdAt(now)
                    .build());
        }

        categoryRepository.saveAll(built);
        log.info("Seeded {} categories (3-level taxonomy)", built.size());
    }
}
