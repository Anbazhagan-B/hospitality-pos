package com.pos.admin.service;

import com.pos.admin.dto.FormDefinitionDto;
import com.pos.admin.dto.FormFieldDto;
import com.pos.admin.entity.FormDefinition;
import com.pos.admin.entity.FormField;
import com.pos.admin.repository.FormDefinitionRepository;
import com.pos.common.dto.PageResponse;
import com.pos.common.exception.BadRequestException;
import com.pos.common.exception.ResourceNotFoundException;
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
public class FormDefinitionService {

    private final FormDefinitionRepository formDefinitionRepository;

    @Transactional
    public FormDefinitionDto createFormDefinition(FormDefinitionDto dto) {
        log.info("Creating form definition: {}", dto.getName());

        if (formDefinitionRepository.existsByName(dto.getName())) {
            throw new BadRequestException("Form definition with name '" + dto.getName() + "' already exists");
        }

        FormDefinition formDefinition = FormDefinition.builder()
                .name(dto.getName())
                .displayName(dto.getDisplayName())
                .description(dto.getDescription())
                .entityType(dto.getEntityType())
                .displayOrder(dto.getDisplayOrder())
                .organizationId(dto.getOrganizationId())
                .active(true)
                .build();

        if (dto.getFields() != null) {
            for (FormFieldDto fieldDto : dto.getFields()) {
                FormField field = toFieldEntity(fieldDto);
                formDefinition.addField(field);
            }
        }

        formDefinition = formDefinitionRepository.save(formDefinition);
        log.info("Form definition created with id: {}", formDefinition.getId());

        return toDto(formDefinition);
    }

    public FormDefinitionDto getFormDefinitionById(Long id) {
        FormDefinition formDefinition = formDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", "id", id));
        return toDto(formDefinition);
    }

    public FormDefinitionDto getFormDefinitionByName(String name) {
        FormDefinition formDefinition = formDefinitionRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", "name", name));
        return toDto(formDefinition);
    }

    public FormDefinitionDto getFormDefinitionByEntityType(String entityType) {
        FormDefinition formDefinition = formDefinitionRepository.findByEntityTypeAndActiveTrue(entityType)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", "entityType", entityType));
        return toDto(formDefinition);
    }

    public PageResponse<FormDefinitionDto> getAllFormDefinitions(Pageable pageable) {
        Page<FormDefinition> page = formDefinitionRepository.findByActiveTrue(pageable);
        return toPageResponse(page);
    }

    public List<FormDefinitionDto> getFormDefinitionsByOrganization(Long organizationId) {
        return formDefinitionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FormDefinitionDto updateFormDefinition(Long id, FormDefinitionDto dto) {
        log.info("Updating form definition with id: {}", id);

        FormDefinition formDefinition = formDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", "id", id));

        if (dto.getDisplayName() != null) {
            formDefinition.setDisplayName(dto.getDisplayName());
        }
        if (dto.getDescription() != null) {
            formDefinition.setDescription(dto.getDescription());
        }
        if (dto.getDisplayOrder() != null) {
            formDefinition.setDisplayOrder(dto.getDisplayOrder());
        }

        if (dto.getFields() != null) {
            formDefinition.getFields().clear();
            for (FormFieldDto fieldDto : dto.getFields()) {
                FormField field = toFieldEntity(fieldDto);
                formDefinition.addField(field);
            }
        }

        formDefinition = formDefinitionRepository.save(formDefinition);
        log.info("Form definition updated with id: {}", id);

        return toDto(formDefinition);
    }

    @Transactional
    public void deleteFormDefinition(Long id) {
        log.info("Deleting form definition with id: {}", id);

        FormDefinition formDefinition = formDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FormDefinition", "id", id));

        formDefinition.setActive(false);
        formDefinitionRepository.save(formDefinition);
        log.info("Form definition soft-deleted with id: {}", id);
    }

    private FormDefinitionDto toDto(FormDefinition formDefinition) {
        return FormDefinitionDto.builder()
                .id(formDefinition.getId())
                .name(formDefinition.getName())
                .displayName(formDefinition.getDisplayName())
                .description(formDefinition.getDescription())
                .entityType(formDefinition.getEntityType())
                .active(formDefinition.isActive())
                .displayOrder(formDefinition.getDisplayOrder())
                .organizationId(formDefinition.getOrganizationId())
                .fields(formDefinition.getFields().stream()
                        .map(this::toFieldDto)
                        .collect(Collectors.toList()))
                .createdAt(formDefinition.getCreatedAt())
                .updatedAt(formDefinition.getUpdatedAt())
                .build();
    }

    private FormFieldDto toFieldDto(FormField field) {
        return FormFieldDto.builder()
                .id(field.getId())
                .name(field.getName())
                .label(field.getLabel())
                .fieldType(field.getFieldType())
                .placeholder(field.getPlaceholder())
                .defaultValue(field.getDefaultValue())
                .helpText(field.getHelpText())
                .required(field.isRequired())
                .readonly(field.isReadonly())
                .hidden(field.isHidden())
                .displayOrder(field.getDisplayOrder())
                .minLength(field.getMinLength())
                .maxLength(field.getMaxLength())
                .pattern(field.getPattern())
                .patternMessage(field.getPatternMessage())
                .options(field.getOptions())
                .dependsOn(field.getDependsOn())
                .dependsOnValue(field.getDependsOnValue())
                .customValidation(field.getCustomValidation())
                .build();
    }

    private FormField toFieldEntity(FormFieldDto dto) {
        return FormField.builder()
                .name(dto.getName())
                .label(dto.getLabel())
                .fieldType(dto.getFieldType())
                .placeholder(dto.getPlaceholder())
                .defaultValue(dto.getDefaultValue())
                .helpText(dto.getHelpText())
                .required(dto.isRequired())
                .readonly(dto.isReadonly())
                .hidden(dto.isHidden())
                .displayOrder(dto.getDisplayOrder())
                .minLength(dto.getMinLength())
                .maxLength(dto.getMaxLength())
                .pattern(dto.getPattern())
                .patternMessage(dto.getPatternMessage())
                .options(dto.getOptions())
                .dependsOn(dto.getDependsOn())
                .dependsOnValue(dto.getDependsOnValue())
                .customValidation(dto.getCustomValidation())
                .build();
    }

    private PageResponse<FormDefinitionDto> toPageResponse(Page<FormDefinition> page) {
        return PageResponse.<FormDefinitionDto>builder()
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
