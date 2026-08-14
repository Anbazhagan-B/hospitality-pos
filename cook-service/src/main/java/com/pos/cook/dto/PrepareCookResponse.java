package com.pos.cook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepareCookResponse {

    private Long jobExecutionId;
    private String jobName;
    private String status;
    private String outputFilePath;
    private Integer totalOrders;
    private Integer processedOrders;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String message;
}
