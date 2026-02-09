package com.example.priceservice;

import java.time.Instant;
import java.util.Objects;

/**
 * Simple immutable class to hold price data.
 * 
 * I made payload an Object to keep it flexible - could be a Double, could be a Map
 * with bid/ask/volume, whatever. The service doesn't really care about the structure.
 */
public final class Price {
    private final String id;        // instrument identifier (e.g., "AAPL", "GOOGL")
    private final Instant asOf;     // when this price was recorded
    private final Object payload;   // the actual price data

    public Price(String id, Instant asOf, Object payload) {
        // None of these can be null - fail fast if someone tries
        if (id == null || asOf == null || payload == null) {
            throw new IllegalArgumentException("Price fields cannot be null");
        }
        this.id = id;
        this.asOf = asOf;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Price price = (Price) o;
        return Objects.equals(id, price.id) &&
               Objects.equals(asOf, price.asOf) &&
               Objects.equals(payload, price.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, asOf, payload);
    }

    @Override
    public String toString() {
        return "Price{" +
               "id='" + id + '\'' +
               ", asOf=" + asOf +
               ", payload=" + payload +
               '}';
    }
}
