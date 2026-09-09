package org.praskanth.service;

import org.praskanth.dto.CategoryRequest;
import org.praskanth.dto.CategoryResponse;
import org.praskanth.entity.Category;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse create(CategoryRequest request, String role) {
        if (!role.equals("ADMIN")){
            throw new BadRequestException("Only admin able to create Category");
        }
        if (categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("A category named '" + request.getName() + "' already exists");
        }
        validateParent(request.getParentId());

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .parentId(request.getParentId())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getById(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<CategoryResponse> getTopLevel() {
        return categoryRepository.findByParentIdIsNull().stream().map(this::toResponse).toList();
    }

    public List<CategoryResponse> getChildren(String parentId) {
        return categoryRepository.findByParentId(parentId).stream().map(this::toResponse).toList();
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoryResponse update(String id, CategoryRequest request) {
        Category existing = findOrThrow(id);

        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BadRequestException("A category cannot be its own parent");
        }
        validateParent(request.getParentId());

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setParentId(request.getParentId());

        return toResponse(categoryRepository.save(existing));
    }

    public void delete(String id) {
        Category existing = findOrThrow(id);
        if (!categoryRepository.findByParentId(id).isEmpty()) {
            throw new ConflictException("Cannot delete a category that has child categories");
        }
        categoryRepository.delete(existing);
    }

    private void validateParent(String parentId) {
        if (parentId != null && !categoryRepository.existsById(parentId)) {
            throw new NotFoundException("Parent category not found: " + parentId);
        }
    }

    private Category findOrThrow(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
