package org.praskanth.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTaxonomyTest {

    @Test
    @DisplayName("flatten on a small tree is pre-order with correct levels, paths, order and counts")
    void flatten_smallTree_preOrderWithComputedFields() {
        // Given
        CategoryTaxonomy.Node leafA = new CategoryTaxonomy.Node("Van Heusen", null, List.of());
        CategoryTaxonomy.Node leafB = new CategoryTaxonomy.Node("Arrow", null, List.of());
        CategoryTaxonomy.Node shirts = new CategoryTaxonomy.Node("Shirts", null, List.of(leafA, leafB));
        CategoryTaxonomy.Node mens = new CategoryTaxonomy.Node("Men's", "icon", List.of(shirts));
        CategoryTaxonomy.Node books = new CategoryTaxonomy.Node("Books", null, List.of());

        // When
        List<CategoryTaxonomy.Flat> flat = CategoryTaxonomy.flatten(List.of(mens, books));

        // Then
        assertThat(flat).hasSize(5);

        CategoryTaxonomy.Flat f0 = flat.get(0);
        assertThat(f0.name()).isEqualTo("Men's");
        assertThat(f0.icon()).isEqualTo("icon");
        assertThat(f0.slug()).isEqualTo("mens");
        assertThat(f0.slugPath()).isEqualTo("mens");
        assertThat(f0.level()).isEqualTo(1);
        assertThat(f0.displayOrder()).isEqualTo(10);
        assertThat(f0.parentIndex()).isEqualTo(-1);
        assertThat(f0.childCount()).isEqualTo(1);

        CategoryTaxonomy.Flat f1 = flat.get(1);
        assertThat(f1.slugPath()).isEqualTo("mens/shirts");
        assertThat(f1.level()).isEqualTo(2);
        assertThat(f1.parentIndex()).isEqualTo(0);
        assertThat(f1.childCount()).isEqualTo(2);

        CategoryTaxonomy.Flat f2 = flat.get(2);
        assertThat(f2.slugPath()).isEqualTo("mens/shirts/van-heusen");
        assertThat(f2.level()).isEqualTo(3);
        assertThat(f2.parentIndex()).isEqualTo(1);
        assertThat(f2.displayOrder()).isEqualTo(10);
        assertThat(f2.childCount()).isZero();

        CategoryTaxonomy.Flat f3 = flat.get(3);
        assertThat(f3.slugPath()).isEqualTo("mens/shirts/arrow");
        assertThat(f3.displayOrder()).isEqualTo(20);

        CategoryTaxonomy.Flat f4 = flat.get(4);
        assertThat(f4.slugPath()).isEqualTo("books");
        assertThat(f4.displayOrder()).isEqualTo(20);
        assertThat(f4.parentIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("flatten of an empty list is empty")
    void flatten_empty_returnsEmpty() {
        assertThat(CategoryTaxonomy.flatten(List.of())).isEmpty();
    }

    @Test
    @DisplayName("starter taxonomy is a consistent 3-level tree with parents before children")
    void departments_flattened_isConsistent() {
        // When
        List<CategoryTaxonomy.Node> departments = CategoryTaxonomy.departments();
        List<CategoryTaxonomy.Flat> flat = CategoryTaxonomy.flatten(departments);

        // Then
        assertThat(departments).isNotEmpty();
        assertThat(flat).isNotEmpty();

        Set<String> paths = new HashSet<>();
        for (int i = 0; i < flat.size(); i++) {
            CategoryTaxonomy.Flat f = flat.get(i);
            assertThat(f).isNotNull();
            assertThat(f.level()).isBetween(1, 3);
            assertThat(f.slug()).isNotBlank();
            assertThat(f.displayOrder() % 10).isZero();
            assertThat(paths.add(f.slugPath())).as("duplicate slugPath %s", f.slugPath()).isTrue();
            if (f.level() == 1) {
                assertThat(f.parentIndex()).isEqualTo(-1);
            } else {
                assertThat(f.parentIndex()).isBetween(0, i - 1);
                CategoryTaxonomy.Flat parent = flat.get(f.parentIndex());
                assertThat(parent.level()).isEqualTo(f.level() - 1);
                assertThat(f.slugPath()).startsWith(parent.slugPath() + "/");
            }
            if (f.level() == 3) {
                assertThat(f.childCount()).isZero();
            } else {
                assertThat(f.childCount()).isPositive();
            }
        }
    }
}
