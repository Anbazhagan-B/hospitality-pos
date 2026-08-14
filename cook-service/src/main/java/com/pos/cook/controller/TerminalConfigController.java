package com.pos.cook.controller;

import com.pos.common.dto.ApiResponse;
import com.pos.cook.dto.TerminalConfig;
import com.pos.cook.service.TerminalConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/terminal-config")
@RequiredArgsConstructor
@Tag(name = "Terminal Configuration", description = "Terminal configuration and menu data JSON generation")
@SecurityRequirement(name = "bearerAuth")
public class TerminalConfigController {

    private final TerminalConfigService terminalConfigService;

    @GetMapping("/{terminalId}")
    @Operation(summary = "Get Terminal Config", description = "Generate terminal configuration JSON")
    public ResponseEntity<ApiResponse<TerminalConfig>> getTerminalConfig(
            @PathVariable String terminalId,
            @RequestParam Long organizationId) {
        TerminalConfig config = terminalConfigService.generateTerminalConfig(terminalId, organizationId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @GetMapping("/{terminalId}/download")
    @Operation(summary = "Download Terminal Config", description = "Download terminal configuration as JSON file")
    public ResponseEntity<TerminalConfig> downloadTerminalConfig(
            @PathVariable String terminalId,
            @RequestParam Long organizationId) {
        TerminalConfig config = terminalConfigService.generateTerminalConfig(terminalId, organizationId);

        String filename = String.format("terminal-config-%s-%d.json", terminalId, System.currentTimeMillis());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(config);
    }

    @GetMapping("/menu/{organizationId}")
    @Operation(summary = "Get Menu Config", description = "Get menu configuration for an organization")
    public ResponseEntity<ApiResponse<TerminalConfig.MenuConfig>> getMenuConfig(
            @PathVariable Long organizationId) {
        TerminalConfig config = terminalConfigService.generateTerminalConfig("menu-only", organizationId);
        return ResponseEntity.ok(ApiResponse.success(config.getMenu()));
    }
}
