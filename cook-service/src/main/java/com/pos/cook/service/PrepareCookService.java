package com.pos.cook.service;

import com.pos.cook.config.BatchConfig;
import com.pos.cook.dto.CookOrder;
import com.pos.cook.dto.PrepareCookRequest;
import com.pos.cook.dto.PrepareCookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareCookService {

    private final JobLauncher jobLauncher;
    private final BatchConfig batchConfig;

    public PrepareCookResponse prepareCookOrders(PrepareCookRequest request) {
        log.info("Starting prepare cook batch job for organization: {}, orders count: {}",
                request.getOrganizationId(), request.getOrders().size());

        try {
            List<CookOrder> orders = request.getOrders();
            String outputFileName = request.getOutputFileName();

            Job job = batchConfig.createPrepareCookJob(orders, outputFileName);

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("organizationId", request.getOrganizationId())
                    .addLong("profitCenterId", request.getProfitCenterId())
                    .addString("terminalId", request.getTerminalId())
                    .addDate("startTime", new Date())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(job, jobParameters);

            return buildResponse(jobExecution, orders.size());

        } catch (Exception e) {
            log.error("Failed to execute prepare cook batch job", e);
            return PrepareCookResponse.builder()
                    .status("FAILED")
                    .message("Batch job failed: " + e.getMessage())
                    .startTime(LocalDateTime.now())
                    .build();
        }
    }

    private PrepareCookResponse buildResponse(JobExecution jobExecution, int totalOrders) {
        String outputFilePath = null;
        Integer processedCount = null;

        if (jobExecution.getStepExecutions() != null && !jobExecution.getStepExecutions().isEmpty()) {
            var stepExecution = jobExecution.getStepExecutions().iterator().next();
            outputFilePath = stepExecution.getExecutionContext().getString("outputFilePath", null);
            if (stepExecution.getExecutionContext().containsKey("processedCount")) {
                processedCount = stepExecution.getExecutionContext().getInt("processedCount");
            }
        }

        return PrepareCookResponse.builder()
                .jobExecutionId(jobExecution.getId())
                .jobName(jobExecution.getJobInstance().getJobName())
                .status(jobExecution.getStatus().name())
                .outputFilePath(outputFilePath)
                .totalOrders(totalOrders)
                .processedOrders(processedCount)
                .startTime(jobExecution.getStartTime())
                .endTime(jobExecution.getEndTime())
                .message("Batch job completed successfully")
                .build();
    }
}
