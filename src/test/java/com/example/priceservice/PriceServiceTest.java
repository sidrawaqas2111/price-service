package com.example.priceservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering the core assignment requirements:
 * - Batch operations (start, upload, complete, cancel)
 * - Last price by asOf timestamp
 * - Consumer isolation from incomplete batches
 * - Parallel chunk uploads
 * - Resilience to incorrect usage
 */
class PriceServiceTest {
    
    private PriceService service;
    
    @BeforeEach
    void setUp() {
        service = new PriceService();
    }
    
    @Test
    @DisplayName("Basic batch flow: start, upload, complete")
    void testBasicBatchFlow() {
        String batchId = service.startBatch();
        
        List<Price> prices = Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T10:00:00Z"), 150.00),
            new Price("GOOGL", Instant.parse("2026-02-08T10:00:00Z"), 2800.00)
        );
        
        service.uploadPrices(batchId, prices);
        service.completeBatch(batchId);
        
        Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL", "GOOGL"));
        
        assertEquals(2, result.size());
        assertEquals(150.00, result.get("AAPL").getPayload());
        assertEquals(2800.00, result.get("GOOGL").getPayload());
    }
    
    @Test
    @DisplayName("Upload in multiple chunks to same batch")
    void testMultipleChunks() {
        String batchId = service.startBatch();
        
        // Requirement: upload in multiple chunks
        service.uploadPrices(batchId, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T10:00:00Z"), 150.00)
        ));
        
        service.uploadPrices(batchId, Arrays.asList(
            new Price("GOOGL", Instant.parse("2026-02-08T10:00:00Z"), 2800.00)
        ));
        
        service.completeBatch(batchId);
        
        Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL", "GOOGL"));
        assertEquals(2, result.size());
    }
    
    @Test
    @DisplayName("Cancel batch discards all prices")
    void testCancelBatch() {
        String batchId = service.startBatch();
        
        service.uploadPrices(batchId, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T10:00:00Z"), 150.00)
        ));
        
        service.cancelBatch(batchId);
        
        Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL"));
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("Last price determined by asOf timestamp, not upload order")
    void testLastPriceByTimestamp() {
        // Upload older price first
        String batch1 = service.startBatch();
        service.uploadPrices(batch1, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T09:00:00Z"), 145.00)
        ));
        service.completeBatch(batch1);
        
        // Upload newer price - should replace the older one
        String batch2 = service.startBatch();
        service.uploadPrices(batch2, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T10:00:00Z"), 150.00)
        ));
        service.completeBatch(batch2);
        
        Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL"));
        assertEquals(150.00, result.get("AAPL").getPayload());
        
        // Upload even older price - should NOT replace current
        String batch3 = service.startBatch();
        service.uploadPrices(batch3, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T08:00:00Z"), 140.00)
        ));
        service.completeBatch(batch3);
        
        result = service.getLastPrices(Arrays.asList("AAPL"));
        assertEquals(150.00, result.get("AAPL").getPayload());
    }
    
    @Test
    @DisplayName("Consumers don't see incomplete batches")
    void testConsumerIsolation() {
        String batchId = service.startBatch();
        
        service.uploadPrices(batchId, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T10:00:00Z"), 150.00)
        ));
        
        // Before completion - should be invisible
        Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL"));
        assertTrue(result.isEmpty());
        
        service.completeBatch(batchId);
        
        // After completion - now visible
        result = service.getLastPrices(Arrays.asList("AAPL"));
        assertEquals(1, result.size());
    }
    
    @Test
    @DisplayName("Service is resilient to invalid batch IDs")
    void testInvalidBatchId() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.uploadPrices("invalid-batch", Arrays.asList(
                new Price("AAPL", Instant.now(), 150.00)
            ));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.completeBatch("invalid-batch");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.cancelBatch("invalid-batch");
        });
    }
    
    @Test
    @DisplayName("Parallel uploads to same batch work correctly")
    void testParallelUploads() throws Exception {
        String batchId = service.startBatch();
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        
        // 10 threads uploading 100 prices each in parallel
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                List<Price> prices = IntStream.range(0, 100)
                    .mapToObj(j -> new Price(
                        "INSTRUMENT-" + (threadId * 100 + j),
                        Instant.parse("2026-02-08T10:00:00Z"),
                        100.0 + j
                    ))
                    .collect(Collectors.toList());
                
                service.uploadPrices(batchId, prices);
            }));
        }
        
        for (Future<?> future : futures) {
            future.get();
        }
        
        executor.shutdown();
        service.completeBatch(batchId);
        
        assertEquals(1000, service.getCommittedPriceCount());
    }
    
    @Test
    @DisplayName("Consumers can query while batch is being processed")
    void testConcurrentConsumersAndProducers() throws Exception {
        // Pre-populate with initial data
        String initialBatch = service.startBatch();
        service.uploadPrices(initialBatch, Arrays.asList(
            new Price("AAPL", Instant.parse("2026-02-08T09:00:00Z"), 145.00)
        ));
        service.completeBatch(initialBatch);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        
        // Start new batch but don't complete it yet
        String batchId = service.startBatch();
        
        // 5 consumer threads querying
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    // Should see AAPL but not GOOGL (incomplete batch)
                    Map<String, Price> result = service.getLastPrices(Arrays.asList("AAPL", "GOOGL"));
                    assertTrue(result.containsKey("AAPL"));
                    assertFalse(result.containsKey("GOOGL"));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 5 producer threads uploading to the batch
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    service.uploadPrices(batchId, Arrays.asList(
                        new Price("GOOGL", Instant.parse("2026-02-08T10:00:00Z"), 2800.0 + threadId)
                    ));
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        service.completeBatch(batchId);
        executor.shutdown();
        
        // Now GOOGL should be visible
        Map<String, Price> result = service.getLastPrices(Arrays.asList("GOOGL"));
        assertTrue(result.containsKey("GOOGL"));
    }
}

