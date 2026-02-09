package com.example.priceservice;

import java.util.List;
import java.util.Map;

/**
 * Price service for batch uploads and queries.
 * Thread-safe, atomic batches, keeps latest price by timestamp.
 */
public interface IPriceService {
    
    /** Start a new batch and get its ID. */
    String startBatch();
    
    /**
     * Upload prices to a batch. Can be called multiple times, even in parallel.
     * Latest timestamp wins for duplicate instruments.
     */
    void uploadPrices(String batchId, List<Price> prices);
    
    /** Complete a batch - all prices become visible atomically. */
    void completeBatch(String batchId);
    
    /** Cancel a batch and discard its prices. */
    void cancelBatch(String batchId);
    
    /**
     * Get latest committed prices. Only returns completed batches,
     * missing instruments are omitted from results.
     */
    Map<String, Price> getLastPrices(List<String> ids);
}
