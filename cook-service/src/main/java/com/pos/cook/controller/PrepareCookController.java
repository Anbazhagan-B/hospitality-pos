package com.pos.cook.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.cook.dto.PrepareCookRequest;
import com.pos.cook.dto.PrepareCookResponse;
import com.pos.cook.service.PrepareCookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/cook")
@RequiredArgsConstructor
@Tag(name = "Prepare Cook", description = "Cook order preparation and batch processing")
@SecurityRequirement(name = "bearerAuth")
public class PrepareCookController {

    private final PrepareCookService prepareCookService;

    @PostMapping("/prepare")
    @Operation(
            summary = "Prepare cook orders",
            description = "Process cook orders through Spring Batch and store the prepared JSON to a file"
    )
    public ResponseEntity<ApiResponse<PrepareCookResponse>> prepareCook(
            @Valid @RequestBody PrepareCookRequest request) {

        log.info("Received prepare cook request for organization: {}, orders: {}",
                request.getOrganizationId(), request.getOrders().size());

        PrepareCookResponse response = prepareCookService.prepareCookOrders(request);

        if ("COMPLETED".equals(response.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(response.getMessage()));
        }
    }
}
