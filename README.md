# Latest-Value Price Service

Thread-safe in-memory service for tracking latest prices of financial instruments with atomic batch upload support.

## Features

- Thread-safe concurrent operations
- Atomic batch completion (all-or-nothing visibility)
- Consumer isolation from incomplete batches
- Latest price resolution by timestamp
- In-memory storage with ConcurrentHashMap

## API

### Price
```java
new Price(String id, Instant asOf, Object payload)
```
- `id` - Instrument identifier
- `asOf` - Timestamp of the price
- `payload` - Price data

### PriceService

**Producer Methods:**
```java
String startBatch()                              // Start new batch
void uploadPrices(String batchId, List<Price>)   // Upload prices to batch
void completeBatch(String batchId)               // Make batch visible
void cancelBatch(String batchId)                 // Cancel batch
```

**Consumer Method:**
```java
Map<String, Price> getLastPrices(List<String> ids)  // Query latest prices
```

## Usage

```java
PriceService service = new PriceService();

// Producer: upload batch
String batchId = service.startBatch();
service.uploadPrices(batchId, Arrays.asList(
    new Price("AAPL", Instant.now(), 150.00),
    new Price("GOOGL", Instant.now(), 2800.00)
));
service.completeBatch(batchId);

// Consumer: query prices
Map<String, Price> prices = service.getLastPrices(Arrays.asList("AAPL", "GOOGL"));
```

## Build & Test

**Requirements:** Java 11+, Gradle 7.6+

```bash
./gradlew build      # Build project
./gradlew test       # Run tests
./gradlew clean      # Clean build
```
