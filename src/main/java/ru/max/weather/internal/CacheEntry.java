package ru.max.weather.internal;

public class CacheEntry {
    private final long epochMillis;
    private final String json;
    private final double lat;
    private final double lon;

    public CacheEntry(long epochMillis, String json, double lat, double lon) {
        this.epochMillis = epochMillis;
        this.json = json;
        this.lat = lat;
        this.lon = lon;
    }

    public long getEpochMillis() { return epochMillis; }
    public String getJson() { return json; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
}


