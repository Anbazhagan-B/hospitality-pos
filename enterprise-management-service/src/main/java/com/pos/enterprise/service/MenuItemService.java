package com.pos.enterprise.service;

import com.pos.common.cache.CacheInvalidationEvent;
import com.pos.common.cache.CacheInvalidationPublisher;
import com.pos.common.cache.CacheNames;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * ObjectProvider rather than direct injection so this service still starts
     * when caching is switched off with {@code pos.cache.enabled=false} - for
     * local development, or to rule the cache out while debugging a stale read.
     */
    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final ObjectProvider<CacheInvalidationPublisher> invalidationPublisher;

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

        invalidate(menuItem.getOrganizationId(), menuItem.getId());

        return toDto(menuItem);
    }

    /**
     * Keyed on the surrogate primary key alone, which is globally unique, so
     * there is no cross-tenant collision. Note this method has never enforced
     * that the caller is entitled to the item's organisation - caching does not
     * change that, but it is worth fixing separately.
     */
    @Cacheable(cacheNames = CacheNames.MENU_ITEM, key = "#id")
    public MenuItemDto getMenuItemById(Long id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return toDto(menuItem);
    }

    /**
     * Deliberately not cached. Every distinct page number, page size and sort
     * order is a separate key, so the region fills with near-duplicate entries
     * that are individually rarely reused, and each one has to be invalidated on
     * any write. Paginated admin browsing is also low-frequency - it is the
     * terminal read path below that needs the cache.
     */
    public PageResponse<MenuItemDto> getMenuItemsByOrganization(Long organizationId, Pageable pageable) {
        Page<MenuItem> page = menuItemRepository.findByOrganizationIdAndActiveTrue(organizationId, pageable);
        return toPageResponse(page);
    }

    /**
     * The hot path: every terminal loads this to render its menu, and it changes
     * perhaps once a day. The key is the organisation id, which is what makes it
     * tenant-safe - a key of, say, the category name would collide across
     * restaurants and serve one tenant another's pricing.
     */
    @Cacheable(cacheNames = CacheNames.ACTIVE_MENU_ITEMS, key = "#organizationId")
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

        invalidate(menuItem.getOrganizationId(), id);

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

        invalidate(menuItem.getOrganizationId(), id);
    }

    /**
     * Drops this service's own cached copies and announces the change so other
     * services can drop theirs.
     *
     * <p>Done programmatically rather than with {@code @CacheEvict} because two
     * regions with different key shapes have to be cleared - one by item id, one
     * by organisation id - and the organisation is only known after the entity
     * is loaded. Expressing that in SpEL against {@code #result} works but reads
     * far worse than this, and silently evicts nothing if the expression is
     * wrong.
     *
     * <p>Deliberately called <em>inside</em> the transaction. If the commit then
     * fails, the cache has been cleared unnecessarily - a harmless extra
     * database read. The reverse ordering would be worse: evicting after commit
     * leaves a window where a concurrent reader repopulates the cache from
     * pre-commit state and the stale value survives for the whole TTL.
     */
    private void invalidate(Long organizationId, Long menuItemId) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager != null) {
            evict(cacheManager, CacheNames.MENU_ITEM, menuItemId);
            evict(cacheManager, CacheNames.ACTIVE_MENU_ITEMS, organizationId);
        }

        invalidationPublisher.ifAvailable(publisher -> publisher.publish(
                CacheInvalidationEvent.EntityType.MENU_ITEM, organizationId, menuItemId));
    }

    private void evict(CacheManager cacheManager, String region, Object key) {
        Cache cache = cacheManager.getCache(region);
        if (cache != null && key != null) {
            cache.evict(key);
        }
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
