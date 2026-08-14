package com.pos.cook.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

@Slf4j
@RequiredArgsConstructor
public class CookOrderWriteListener implements StepExecutionListener {

    private final CookOrderJsonFileItemWriter writer;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.info("Starting cook order processing step");
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        try {
            writer.writeToFile();
            stepExecution.getExecutionContext().putString("outputFilePath", writer.getOutputFilePath());
            stepExecution.getExecutionContext().putInt("processedCount", writer.getProcessedCount());
            log.info("Cook order processing step completed. Processed {} orders", writer.getProcessedCount());
            return ExitStatus.COMPLETED;
        } catch (Exception e) {
            log.error("Failed to write orders to file", e);
            return ExitStatus.FAILED;
        }
    }
}
