package org.praskanth.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.entity.Category;
import org.praskanth.repository.CategoryRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorySeederTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Captor
    private ArgumentCaptor<List<Category>> savedCaptor;

    @Test
    @DisplayName("run skips seeding when the collection already has data")
    void run_collectionNotEmpty_doesNothing() {
        // Given
        when(categoryRepository.count()).thenReturn(5L);

        // When
        new CategorySeeder(categoryRepository).run(null);

        // Then
        verify(categoryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("run seeds the whole taxonomy with consistent parent/ancestor links when empty")
    void run_emptyCollection_seedsConsistentTree() {
        // Given
        when(categoryRepository.count()).thenReturn(0L);

        // When
        new CategorySeeder(categoryRepository).run(null);

        // Then
        verify(categoryRepository).saveAll(savedCaptor.capture());
        List<Category> saved = savedCaptor.getValue();
        assertThat(saved).hasSameSizeAs(CategoryTaxonomy.flatten(CategoryTaxonomy.departments()));

        Map<String, Category> byId = new HashMap<>();
        for (Category c : saved) {
            assertThat(c.getId()).isNotBlank();
            byId.put(c.getId(), c);
        }
        assertThat(byId).hasSameSizeAs(saved);

        for (Category c : saved) {
            assertThat(c.getCreatedBy()).isEqualTo(0L);
            assertThat(c.isActive()).isTrue();
            assertThat(c.getNameKey()).isEqualTo(c.getName().toLowerCase(java.util.Locale.ROOT));
            if (c.getLevel() == 1) {
                assertThat(c.getParentId()).isNull();
                assertThat(c.getAncestorIds()).isEmpty();
                assertThat(c.getAncestors()).isEmpty();
                assertThat(c.getSlugPath()).isEqualTo(c.getSlug());
            } else {
                Category parent = byId.get(c.getParentId());
                assertThat(parent).isNotNull();
                assertThat(parent.getLevel()).isEqualTo(c.getLevel() - 1);
                assertThat(c.getAncestorIds()).hasSize(c.getLevel() - 1);
                assertThat(c.getAncestorIds().get(c.getAncestorIds().size() - 1)).isEqualTo(parent.getId());
                assertThat(c.getAncestors()).hasSize(c.getLevel() - 1);
                assertThat(c.getAncestors().get(c.getAncestors().size() - 1).getName()).isEqualTo(parent.getName());
                assertThat(c.getSlugPath()).isEqualTo(parent.getSlugPath() + "/" + c.getSlug());
            }
        }
    }
}
