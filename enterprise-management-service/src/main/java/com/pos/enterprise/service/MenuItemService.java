package com.pos.enterprise.service;

import com.pos.common.dto.PageResponse;
import com.pos.common.exception.ResourceNotFoundException;
import com.pos.enterprise.dto.MenuItemDto;
import com.pos.enterprise.dto.MenuItemModifierDto;
import com.pos.enterprise.entity.MenuCategory;
import com.pos.enterprise.entity.MenuItem;
import com.pos.enterprise.entity.MenuItemModifier;
import com.pos.enterprise.repository.MenuCategoryRepository;
import com.pos.enterprise.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    @Transactional
    public MenuItemDto createMenuItem(MenuItemDto request) {
        log.info("Creating menu item: {}", request.getName());

        MenuCategory category = null;
        if (request.getCategoryId() != null) {
            category = menuCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        }

        MenuItem menuItem = MenuItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .category(category)
                .organizationId(request.getOrganizationId())
                .active(true)
                .build();

        menuItem = menuItemRepository.save(menuItem);
        log.info("Menu item created with id: {}", menuItem.getId());

        return toDto(menuItem);
    }

    public MenuItemDto getMenuItemById(Long id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return toDto(menuItem);
    }

    public PageResponse<MenuItemDto> getMenuItemsByOrganization(Long organizationId, Pageable pageable) {
        Page<MenuItem> page = menuItemRepository.findByOrganizationIdAndActiveTrue(organizationId, pageable);
        return toPageResponse(page);
    }

    public List<MenuItemDto> getActiveMenuItems(Long organizationId) {
        return menuItemRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MenuItemDto updateMenuItem(Long id, MenuItemDto request) {
        log.info("Updating menu item with id: {}", id);

        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));

        if (request.getName() != null) {
            menuItem.setName(request.getName());
        }
        if (request.getDescription() != null) {
            menuItem.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            menuItem.setPrice(request.getPrice());
        }
        if (request.getSku() != null) {
            menuItem.setSku(request.getSku());
        }
        if (request.getImageUrl() != null) {
            menuItem.setImageUrl(request.getImageUrl());
        }
        if (request.getCategoryId() != null) {
            MenuCategory category = menuCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            menuItem.setCategory(category);
        }

        menuItem = menuItemRepository.save(menuItem);
        log.info("Menu item updated with id: {}", id);

        return toDto(menuItem);
    }

    @Transactional
    public void deleteMenuItem(Long id) {
        log.info("Deleting menu item with id: {}", id);

        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));

        menuItem.setActive(false);
        menuItemRepository.save(menuItem);
        log.info("Menu item soft-deleted with id: {}", id);
    }

    private MenuItemDto toDto(MenuItem menuItem) {
        return MenuItemDto.builder()
                .id(menuItem.getId())
                .name(menuItem.getName())
                .description(menuItem.getDescription())
                .price(menuItem.getPrice())
                .sku(menuItem.getSku())
                .active(menuItem.isActive())
                .imageUrl(menuItem.getImageUrl())
                .categoryId(menuItem.getCategory() != null ? menuItem.getCategory().getId() : null)
                .categoryName(menuItem.getCategory() != null ? menuItem.getCategory().getName() : null)
                .organizationId(menuItem.getOrganizationId())
                .modifiers(menuItem.getModifiers().stream()
                        .map(this::toModifierDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private MenuItemModifierDto toModifierDto(MenuItemModifier modifier) {
        return MenuItemModifierDto.builder()
                .id(modifier.getId())
                .name(modifier.getName())
                .description(modifier.getDescription())
                .priceAdjustment(modifier.getPriceAdjustment())
                .type(modifier.getType())
                .active(modifier.isActive())
                .build();
    }

    private PageResponse<MenuItemDto> toPageResponse(Page<MenuItem> page) {
        return PageResponse.<MenuItemDto>builder()
                .content(page.getContent().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
