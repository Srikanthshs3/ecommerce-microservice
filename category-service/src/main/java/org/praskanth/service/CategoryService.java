package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.client.ProductClient;
import org.praskanth.config.CategoryProperties;
import org.praskanth.dto.CategoryNodeResponse;
import org.praskanth.dto.CategoryRequest;
import org.praskanth.dto.CategoryResponse;
import org.praskanth.entity.Category;
import org.praskanth.entity.CategoryAncestor;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.CategoryRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.util.SlugUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Category tree rules (3 levels, see Category for the storage model).
 *
 * Who may do what:
 *   ADMIN   everything.
 *   SELLER  create at the levels listed in category.seller-allowed-levels (default: all),
 *           and edit / delete only the categories they created.
 *   Anyone  read.
 *
 * Deliberately not supported: moving a category under another parent. That would rewrite the ancestors,
 * level and slugPath of the whole subtree AND the category path stored on every product below it.
 * Create the category in the right place instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private static final String ROOT_KEY = "";

    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;
    private final CategoryProperties properties;
    private final ProductClient productClient;

    // ---- in-memory cache of the public tree -------------------------------------------------------------------
    private record CachedTree(List<CategoryNodeResponse> roots, long loadedAtMillis) {
    }

    private volatile CachedTree cachedTree;

    // =====================================================================================================
    //  writes
    // =====================================================================================================

    public CategoryResponse create(CategoryRequest request, GatewayUser actor) {
        Category parent = request.getParentId() == null ? null : findOrThrow(request.getParentId());

        int level = parent == null ? 1 : parent.getLevel() + 1;
        if (level > properties.getMaxLevel()) {
            throw new BadRequestException("Categories can be at most " + properties.getMaxLevel()
                    + " levels deep. '" + parent.getName() + "' is already at the deepest level.");
        }
        if (!actor.isAdmin() && !properties.getSellerAllowedLevels().contains(level)) {
            throw new ForbiddenOperationException("Only an admin can create level-" + level + " categories");
        }

        String name = request.getName().trim();
        String slug = requireSlug(name);
        String nameKey = name.toLowerCase(Locale.ROOT);
        String parentId = parent == null ? null : parent.getId();

        if (categoryRepository.existsByParentIdAndNameKey(parentId, nameKey)) {
            throw new ConflictException("'" + name + "' already exists at this level");
        }

        List<CategoryAncestor> ancestors = new ArrayList<>();
        List<String> ancestorIds = new ArrayList<>();
        if (parent != null) {
            ancestors.addAll(parent.getAncestors());
            ancestors.add(toAncestor(parent));
            ancestorIds.addAll(parent.getAncestorIds());
            ancestorIds.add(parent.getId());
        }

        Category category = Category.builder()
                .name(name)
                .nameKey(nameKey)
                .slug(slug)
                .slugPath(parent == null ? slug : parent.getSlugPath() + "/" + slug)
                .description(request.getDescription())
                .icon(request.getIcon())
                .parentId(parentId)
                .level(level)
                .ancestors(ancestors)
                .ancestorIds(ancestorIds)
                .childCount(0)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : nextDisplayOrder(parentId))
                .active(request.getActive() == null || request.getActive())
                .createdBy(actor.id())
                .createdAt(Instant.now())
                .build();

        Category saved = saveOrConflict(category, name);
        if (parent != null) {
            adjustChildCount(parent.getId(), +1);
        }
        evictTree();
        log.info("Category created id={} path={} by userId={}", saved.getId(), saved.getSlugPath(), actor.id());
        return toResponse(saved);
    }

    public CategoryResponse update(String id, CategoryRequest request, GatewayUser actor) {
        Category existing = findOrThrow(id);
        assertCanManage(existing, actor);

        // parentId on update means "unchanged"; asking for a different parent is an explicit error
        if (request.getParentId() != null && !request.getParentId().equals(existing.getParentId())) {
            throw new BadRequestException("Moving a category to a different parent is not supported. "
                    + "Create a new category in the right place instead.");
        }

        String newName = request.getName().trim();
        boolean renamed = !newName.equals(existing.getName());
        String oldSlugPath = existing.getSlugPath();

        if (renamed) {
            String newSlug = requireSlug(newName);
            String newNameKey = newName.toLowerCase(Locale.ROOT);
            boolean keyChanged = !newNameKey.equals(existing.getNameKey());
            if (keyChanged && categoryRepository.existsByParentIdAndNameKey(existing.getParentId(), newNameKey)) {
                throw new ConflictException("'" + newName + "' already exists at this level");
            }
            existing.setName(newName);
            existing.setNameKey(newNameKey);
            existing.setSlug(newSlug);
            String parentPath = oldSlugPath.contains("/") ? oldSlugPath.substring(0, oldSlugPath.lastIndexOf('/') + 1) : "";
            existing.setSlugPath(parentPath + newSlug);
        }

        existing.setDescription(request.getDescription());
        existing.setIcon(request.getIcon());
        if (request.getDisplayOrder() != null) {
            existing.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }
        existing.setUpdatedAt(Instant.now());

        Category saved = saveOrConflict(existing, newName);
        if (renamed) {
            cascadeRename(saved, oldSlugPath);
        }
        evictTree();
        return toResponse(saved);
    }

    public void delete(String id, GatewayUser actor) {
        Category existing = findOrThrow(id);
        assertCanManage(existing, actor);

        if (categoryRepository.countByParentId(id) > 0) {
            throw new ConflictException("'" + existing.getName() + "' still has sub-categories. Delete them first.");
        }
        // asks product-service; if it cannot answer we refuse (fail closed) rather than orphan products
        long products = productClient.countProductsInCategory(id);
        if (products > 0) {
            throw new ConflictException("'" + existing.getName() + "' is used by " + products
                    + " product(s). Move or delete those products first.");
        }

        categoryRepository.delete(existing);
        if (existing.getParentId() != null) {
            adjustChildCount(existing.getParentId(), -1);
        }
        evictTree();
        log.info("Category deleted id={} path={} by userId={}", id, existing.getSlugPath(), actor.id());
    }

    // =====================================================================================================
    //  reads
    // =====================================================================================================

    /** The whole active tree, nested, in display order. Cached for category.tree-cache-seconds. */
    public List<CategoryNodeResponse> getTree() {
        CachedTree cached = cachedTree;
        long ttlMillis = properties.getTreeCacheSeconds() * 1000L;
        if (cached != null && System.currentTimeMillis() - cached.loadedAtMillis() < ttlMillis) {
            return cached.roots();
        }
        List<CategoryNodeResponse> roots = buildTree();
        cachedTree = new CachedTree(roots, System.currentTimeMillis());
        return roots;
    }

    public CategoryResponse getById(String id) {
        Category category = findOrThrow(id);
        CategoryResponse response = toResponse(category);
        response.setSelectable(category.isActive() && category.isLeaf() && ancestorsActive(category));
        return response;
    }

    public CategoryResponse getByPath(String slugPath) {
        Category category = categoryRepository.findBySlugPath(slugPath.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("We couldn't find that category."));
        return getById(category.getId());
    }

    public List<CategoryResponse> list(String parentId, Integer level, boolean topLevelOnly) {
        List<Category> result;
        if (parentId != null) {
            result = categoryRepository.findByParentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(parentId);
        } else if (level != null) {
            result = categoryRepository.findByLevelAndActiveTrueOrderByDisplayOrderAscNameAsc(level);
        } else if (topLevelOnly) {
            result = categoryRepository.findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc();
        } else {
            result = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        }
        return result.stream().map(this::toResponse).toList();
    }

    /** Every node including inactive ones: for the admin / seller management screens. */
    public List<CategoryResponse> listForManagement() {
        return categoryRepository.findAllByOrderByLevelAscDisplayOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    public List<CategoryResponse> listCreatedBy(Long userId) {
        return categoryRepository.findByCreatedByOrderByLevelAscNameAsc(userId).stream().map(this::toResponse).toList();
    }

    // =====================================================================================================
    //  helpers
    // =====================================================================================================

    private void assertCanManage(Category category, GatewayUser actor) {
        if (actor.isAdmin()) {
            return;
        }
        if (actor.hasRole("SELLER") && actor.id() != null && actor.id().equals(category.getCreatedBy())) {
            return;
        }
        throw new ForbiddenOperationException("You can only change categories that you created");
    }

    private Category findOrThrow(String id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("We couldn't find that category."));
    }

    private static String requireSlug(String name) {
        String slug = SlugUtil.slugify(name);
        if (slug.isEmpty()) {
            throw new BadRequestException("Category name must contain letters or digits");
        }
        return slug;
    }

    private int nextDisplayOrder(String parentId) {
        long siblings = categoryRepository.countByParentId(parentId);
        return (int) (siblings + 1) * 10;
    }

    private Category saveOrConflict(Category category, String name) {
        try {
            return categoryRepository.save(category);
        } catch (DuplicateKeyException e) {
            // two requests racing past the exists-check: the unique indexes are the real guarantee
            throw new ConflictException("'" + name + "' already exists at this level");
        }
    }

    private void adjustChildCount(String categoryId, int delta) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(categoryId)),
                new Update().inc("childCount", delta),
                Category.class);
    }

    /** After a rename, every descendant carries the old name/slug in ancestors[] and the old prefix in slugPath. */
    private void cascadeRename(Category renamed, String oldSlugPath) {
        List<Category> descendants = categoryRepository.findByAncestorIds(renamed.getId());
        for (Category d : descendants) {
            d.setSlugPath(renamed.getSlugPath() + d.getSlugPath().substring(oldSlugPath.length()));
            for (CategoryAncestor a : d.getAncestors()) {
                if (renamed.getId().equals(a.getId())) {
                    a.setName(renamed.getName());
                    a.setSlug(renamed.getSlug());
                }
            }
        }
        categoryRepository.saveAll(descendants);
        log.info("Renamed category {}: updated {} descendant(s)", renamed.getId(), descendants.size());
    }

    private boolean ancestorsActive(Category category) {
        if (category.getAncestorIds() == null || category.getAncestorIds().isEmpty()) {
            return true;
        }
        List<Category> ancestors = categoryRepository.findAllById(category.getAncestorIds());
        return ancestors.size() == category.getAncestorIds().size() && ancestors.stream().allMatch(Category::isActive);
    }

    private void evictTree() {
        cachedTree = null;
    }

    private List<CategoryNodeResponse> buildTree() {
        List<Category> all = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        Map<String, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParentId() == null ? ROOT_KEY : c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        // walking down from the roots means nodes below an INACTIVE parent are never reached
        return buildNodes(byParent.getOrDefault(ROOT_KEY, List.of()), byParent);
    }

    private List<CategoryNodeResponse> buildNodes(List<Category> categories, Map<String, List<Category>> byParent) {
        List<CategoryNodeResponse> nodes = new ArrayList<>();
        for (Category c : categories) {
            List<CategoryNodeResponse> children = buildNodes(byParent.getOrDefault(c.getId(), List.of()), byParent);
            nodes.add(CategoryNodeResponse.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .slug(c.getSlug())
                    .slugPath(c.getSlugPath())
                    .icon(c.getIcon())
                    .level(c.getLevel())
                    .displayOrder(c.getDisplayOrder())
                    .leaf(children.isEmpty())
                    .children(children)
                    .build());
        }
        return nodes;
    }

    private static CategoryAncestor toAncestor(Category c) {
        return CategoryAncestor.builder().id(c.getId()).name(c.getName()).slug(c.getSlug()).level(c.getLevel()).build();
    }

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .slugPath(c.getSlugPath())
                .description(c.getDescription())
                .icon(c.getIcon())
                .parentId(c.getParentId())
                .level(c.getLevel())
                .displayOrder(c.getDisplayOrder())
                .active(c.isActive())
                .leaf(c.isLeaf())
                .ancestors(c.getAncestors())
                .ancestorIds(c.getAncestorIds())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }
}