package com.pos.cook.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos.cook.dto.CookOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CookOrderJsonFileItemWriter implements ItemWriter<CookOrder> {

    private final String outputFilePath;
    private final ObjectMapper objectMapper;
    private final List<CookOrder> allOrders;

    public CookOrderJsonFileItemWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.allOrders = new ArrayList<>();
    }

    @Override
    public void write(@NonNull Chunk<? extends CookOrder> chunk) throws Exception {
        log.debug("Writing {} orders to buffer", chunk.size());
        allOrders.addAll(chunk.getItems());
    }

    public void writeToFile() throws IOException {
        Path path = Paths.get(outputFilePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.info("Created output directory: {}", parentDir);
        }

        File outputFile = path.toFile();
        objectMapper.writeValue(outputFile, allOrders);
        log.info("Successfully wrote {} orders to file: {}", allOrders.size(), outputFilePath);
    }

    public int getProcessedCount() {
        return allOrders.size();
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }
}
