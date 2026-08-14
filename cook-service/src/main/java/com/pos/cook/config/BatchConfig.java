package com.pos.cook.config;

import com.pos.cook.batch.CookOrderItemProcessor;
import com.pos.cook.batch.CookOrderItemReader;
import com.pos.cook.batch.CookOrderJsonFileItemWriter;
import com.pos.cook.batch.CookOrderWriteListener;
import com.pos.cook.dto.CookOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${cook.batch.chunk-size:10}")
    private int chunkSize;

    @Value("${cook.batch.output-path:./output/cook-orders}")
    private String defaultOutputPath;

    @Bean
    public CookOrderItemProcessor cookOrderItemProcessor() {
        return new CookOrderItemProcessor();
    }

    public Job createPrepareCookJob(List<CookOrder> orders, String outputFileName) {
        String filePath = buildOutputFilePath(outputFileName);
        CookOrderItemReader reader = new CookOrderItemReader(orders);
        CookOrderJsonFileItemWriter writer = new CookOrderJsonFileItemWriter(filePath);

        Step step = new StepBuilder("prepareCookStep", jobRepository)
                .<CookOrder, CookOrder>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(cookOrderItemProcessor())
                .writer(writer)
                .listener(new CookOrderWriteListener(writer))
                .build();

        return new JobBuilder("prepareCookJob", jobRepository)
                .start(step)
                .build();
    }

    private String buildOutputFilePath(String outputFileName) {
        if (outputFileName == null || outputFileName.isBlank()) {
            outputFileName = "cook-orders-" + System.currentTimeMillis() + ".json";
        } else if (!outputFileName.endsWith(".json")) {
            outputFileName = outputFileName + ".json";
        }
        return defaultOutputPath + "/" + outputFileName;
    }
}
