package com.pos.enterprise.service;

import com.pos.common.dto.PageResponse;
import com.pos.common.exception.BadRequestException;
import com.pos.common.exception.ResourceNotFoundException;
import com.pos.enterprise.dto.CreateOrganizationRequest;
import com.pos.enterprise.dto.OrganizationDto;
import com.pos.enterprise.entity.Organization;
import com.pos.enterprise.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest request) {
        log.info("Creating organization: {}", request.getName());

        if (organizationRepository.existsByName(request.getName())) {
            throw new BadRequestException("Organization with this name already exists");
        }

        Organization organization = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .active(true)
                .build();

        organization = organizationRepository.save(organization);
        log.info("Organization created with id: {}", organization.getId());

        return toDto(organization);
    }

    public OrganizationDto getOrganizationById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));
        return toDto(organization);
    }

    public PageResponse<OrganizationDto> getAllOrganizations(Pageable pageable) {
        Page<Organization> page = organizationRepository.findByActiveTrue(pageable);
        return toPageResponse(page);
    }

    @Transactional
    public OrganizationDto updateOrganization(Long id, CreateOrganizationRequest request) {
        log.info("Updating organization with id: {}", id);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));

        if (request.getName() != null && !request.getName().equals(organization.getName())) {
            if (organizationRepository.existsByName(request.getName())) {
                throw new BadRequestException("Organization with this name already exists");
            }
            organization.setName(request.getName());
        }

        if (request.getDescription() != null) {
            organization.setDescription(request.getDescription());
        }
        if (request.getAddress() != null) {
            organization.setAddress(request.getAddress());
        }
        if (request.getPhone() != null) {
            organization.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            organization.setEmail(request.getEmail());
        }

        organization = organizationRepository.save(organization);
        log.info("Organization updated with id: {}", id);

        return toDto(organization);
    }

    @Transactional
    public void deleteOrganization(Long id) {
        log.info("Deleting organization with id: {}", id);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", id));

        organization.setActive(false);
        organizationRepository.save(organization);
        log.info("Organization soft-deleted with id: {}", id);
    }

    private OrganizationDto toDto(Organization organization) {
        return OrganizationDto.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .address(organization.getAddress())
                .phone(organization.getPhone())
                .email(organization.getEmail())
                .active(organization.isActive())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }

    private PageResponse<OrganizationDto> toPageResponse(Page<Organization> page) {
        return PageResponse.<OrganizationDto>builder()
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
