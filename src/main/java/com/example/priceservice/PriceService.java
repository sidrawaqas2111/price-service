package com.example.priceservice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory price service.
 * Stages batches before committing, always keeps latest price by timestamp.
 */
public class PriceService implements IPriceService {
    
    // Committed prices visible to consumers
    private final ConcurrentHashMap<String, Price> committedPrices = new ConcurrentHashMap<>();
    
    // Staging area for in-progress batches: batchId -> (instrumentId -> price)
    private final Map<String, Map<String, Price>> stagingBatches = new HashMap<>();
    
    private final Set<String> activeBatchIds = new HashSet<>();
    private final AtomicLong batchIdGenerator = new AtomicLong(0);
    
    @Override
    public String startBatch() {
        String batchId = "batch-" + batchIdGenerator.incrementAndGet();
        
        synchronized (this) {
            activeBatchIds.add(batchId);
            stagingBatches.put(batchId, new HashMap<>());
        }
        
        return batchId;
    }
    
    @Override
    public void uploadPrices(String batchId, List<Price> prices) {
        if (prices == null) {
            throw new IllegalArgumentException("Prices list cannot be null");
        }
        
        synchronized (this) {
            if (!activeBatchIds.contains(batchId)) {
                throw new IllegalArgumentException("Invalid or inactive batch ID: " + batchId);
            }
            
            Map<String, Price> batchPrices = stagingBatches.get(batchId);
            
            // Merge prices, keeping latest by timestamp
            for (Price price : prices) {
                if (price == null) continue;
                
                batchPrices.merge(
                    price.getId(),
                    price,
                    (existing, newPrice) -> 
                        newPrice.getAsOf().isAfter(existing.getAsOf()) ? newPrice : existing
                );
            }
        }
    }
    
    @Override
    public void completeBatch(String batchId) {
        Map<String, Price> batchPrices;
        
        synchronized (this) {
            if (!activeBatchIds.contains(batchId)) {
                throw new IllegalArgumentException("Invalid or inactive batch ID: " + batchId);
            }
            
            batchPrices = stagingBatches.remove(batchId);
            activeBatchIds.remove(batchId);
        }
        
        // Commit outside sync block - ConcurrentHashMap.merge() is thread-safe per key
        for (Price price : batchPrices.values()) {
            committedPrices.merge(
                price.getId(),
                price,
                (existing, newPrice) ->
                    newPrice.getAsOf().isAfter(existing.getAsOf()) ? newPrice : existing
            );
        }
    }
    
    @Override
    public void cancelBatch(String batchId) {
        synchronized (this) {
            if (!activeBatchIds.contains(batchId)) {
                throw new IllegalArgumentException("Invalid or inactive batch ID: " + batchId);
            }
            
            stagingBatches.remove(batchId);
            activeBatchIds.remove(batchId);
        }
    }
    
    @Override
    public Map<String, Price> getLastPrices(List<String> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("Instrument IDs list cannot be null");
        }
        
        Map<String, Price> result = new HashMap<>();
        
        for (String id : ids) {
            if (id == null) continue;
            
            Price price = committedPrices.get(id);
            if (price != null) {
                result.put(id, price);
            }
        }
        
        return result;
    }
    
    // Helper methods for testing/monitoring
    
    public int getCommittedPriceCount() {
        return committedPrices.size();
    }
    
    public synchronized int getActiveBatchCount() {
        return activeBatchIds.size();
    }
}
