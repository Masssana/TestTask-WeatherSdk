package ru.max.weather;

import java.time.Duration;

public class WeatherClientConfig {
    private final WeatherMode mode;
    private final Duration freshnessTtl;
    private final int cacheCapacity;
    private final Duration pollingInterval;
    private final String units; // standard, metric, imperial

    private WeatherClientConfig(Builder builder) {
        this.mode = builder.mode;
        this.freshnessTtl = builder.freshnessTtl;
        this.cacheCapacity = builder.cacheCapacity;
        this.pollingInterval = builder.pollingInterval;
        this.units = builder.units;
    }

    public WeatherMode getMode() { return mode; }
    public Duration getFreshnessTtl() { return freshnessTtl; }
    public int getCacheCapacity() { return cacheCapacity; }
    public Duration getPollingInterval() { return pollingInterval; }
    public String getUnits() { return units; }

    public static Builder builder(WeatherMode mode) { return new Builder(mode); }

    public static class Builder {
        private final WeatherMode mode;
        private Duration freshnessTtl = Duration.ofMinutes(10);
        private int cacheCapacity = 10;
        private Duration pollingInterval = Duration.ofMinutes(5);
        private String units = "metric";

        public Builder(WeatherMode mode) {
            this.mode = mode;
        }

        public Builder freshnessTtl(Duration freshnessTtl) { this.freshnessTtl = freshnessTtl; return this; }
        public Builder cacheCapacity(int cacheCapacity) {
            if (cacheCapacity > 10){
                throw new IllegalArgumentException("cacheCapacity should be no more than 10");
            }
            if(cacheCapacity <= 0){
                throw new IllegalArgumentException("cacheCapacity should be not less than 0");
            }
            this.cacheCapacity = cacheCapacity;
            return this;
        }
        public Builder pollingInterval(Duration pollingInterval) { this.pollingInterval = pollingInterval; return this; }
        public Builder units(String units) { this.units = units; return this; }

        public WeatherClientConfig build() { return new WeatherClientConfig(this); }
    }
}


