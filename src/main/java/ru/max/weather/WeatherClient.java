package ru.max.weather;

import ru.max.weather.exceptions.WeatherSdkException;
import ru.max.weather.internal.CacheEntry;
import ru.max.weather.internal.LruCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WeatherClient implements AutoCloseable {
    private static final String GEO_URL = "https://api.openweathermap.org/geo/1.0/direct";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather";

    private final Logger logger = Logger.getLogger(WeatherClient.class.getName());

    private static final Map<String, WeatherClient> KEY_TO_INSTANCE = new ConcurrentHashMap<>();

    private final String apiKey;
    private final WeatherClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LruCache<String, CacheEntry> cache;
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;

    private WeatherClient(String apiKey, WeatherClientConfig config) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.cache = new LruCache<>(config.getCacheCapacity());
        if (config.getMode() == WeatherMode.POLLING) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "weather-polling");
                t.setDaemon(true);
                return t;
            });
            this.scheduler.scheduleAtFixedRate(this::refreshAllCached, config.getPollingInterval().toSeconds(), config.getPollingInterval().toSeconds(), TimeUnit.SECONDS);
        } else {
            this.scheduler = null;
        }
    }

    public static WeatherClient getInstance(String apiKey, WeatherClientConfig config) {
        if (apiKey == null || apiKey.isBlank()) throw new WeatherSdkException("API key must be provided");
        return KEY_TO_INSTANCE.compute(apiKey, (k, existing) -> {
            if (existing != null && !existing.closed) return existing;
            return new WeatherClient(k, config);
        });
    }

    public static boolean deleteInstance(String apiKey) {
        WeatherClient client = KEY_TO_INSTANCE.remove(apiKey);
        if (client != null) {
            client.close();
            return true;
        }
        return false;
    }

    public String getWeatherJson(String cityName) {
        JsonNode node = getWeather(cityName);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new WeatherSdkException("Failed to serialize weather JSON", e);
        }
    }

    public JsonNode getWeather(String cityName) {
        ensureOpen();
        String key = normalizeCityKey(cityName);
        CacheEntry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && isFresh(cached.getEpochMillis(), now)) {
            return parseJson(cached.getJson());
        }

        CityCoordinates coords = lookupCityCoordinates(key);
        String json = fetchWeather(coords.lat, coords.lon);
        cache.put(key, new CacheEntry(now, json, coords.lat, coords.lon));
        return parseJson(json);
    }

    private void refreshAllCached() {
        if (closed) return;
        try {
            Set<String> keys = Collections.unmodifiableSet(cache.keySet());
            for (String key : keys) {
                CacheEntry entry = cache.get(key);
                if (entry == null) continue;
                try {
                    String json = fetchWeather(entry.getLat(), entry.getLon());
                    cache.put(key, new CacheEntry(System.currentTimeMillis(), json, entry.getLat(), entry.getLon()));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to refresh cache for the city " + key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during cache refresh", e.getMessage());
        }
    }

    private boolean isFresh(long timestampMillis, long nowMillis) {
        long ttl = config.getFreshnessTtl().toMillis();
        return nowMillis - timestampMillis < ttl;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new WeatherSdkException("Failed to parse weather JSON", e);
        }
    }

    private CityCoordinates lookupCityCoordinates(String cityKey) {
        try {
            String q = URLEncoder.encode(cityKey, StandardCharsets.UTF_8);
            String url = GEO_URL + "?q=" + q + "&limit=1&appid=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WeatherSdkException("Geocoding API error: HTTP " + response.statusCode());
            }
            JsonNode arr = objectMapper.readTree(response.body());
            if (!arr.isArray() || arr.isEmpty()) {
                throw new WeatherSdkException("City not found: " + cityKey);
            }
            JsonNode first = arr.get(0);
            double lat = first.get("lat").asDouble();
            double lon = first.get("lon").asDouble();
            return new CityCoordinates(lat, lon);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherSdkException("Failed to lookup city coordinates", e);
        }
    }

    private String fetchWeather(double lat, double lon) {
        try {
            String url = WEATHER_URL + "?lat=" + lat + "&lon=" + lon + "&appid=" + apiKey + "&units=" + config.getUnits();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new WeatherSdkException("Weather API error: HTTP " + response.statusCode());
            }
            return transformToSdkJson(objectMapper.readTree(response.body())).toString();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherSdkException("Failed to fetch weather", e);
        }
    }

    private JsonNode transformToSdkJson(JsonNode openWeather) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        result.put("weather", extractWeather(openWeather));
        result.put("temperature", extractTemperature(openWeather));
        result.put("visibility", extractVisibility(openWeather));
        result.put("wind", extractWind(openWeather));
        result.put("datetime", extractDatetime(openWeather));
        result.put("sys", extractSys(openWeather));
        result.put("timezone", extractTimezone(openWeather));
        result.put("name", extractCityName(openWeather));

        return objectMapper.valueToTree(result);
    }

    private Map<String, Object> extractWeather(JsonNode openWeather) {
        Map<String, Object> weather = new LinkedHashMap<>();
        JsonNode weatherArr = openWeather.path("weather");
        if (weatherArr.isArray() && !weatherArr.isEmpty()) {
            JsonNode w = weatherArr.get(0);
            weather.put("main", Optional.ofNullable(w.get("main")).map(JsonNode::asText).orElse(null));
            weather.put("description", Optional.ofNullable(w.get("description")).map(JsonNode::asText).orElse(null));
        }
        return weather;
    }

    private Map<String, Object> extractTemperature(JsonNode openWeather) {
        Map<String, Object> temperature = new LinkedHashMap<>();
        JsonNode main = openWeather.path("main");
        temperature.put("temp", main.path("temp").isMissingNode() ? null : main.get("temp").asDouble());
        temperature.put("feels_like", main.path("feels_like").isMissingNode() ? null : main.get("feels_like").asDouble());
        return temperature;
    }

    private Integer extractVisibility(JsonNode openWeather) {
        return openWeather.path("visibility").isMissingNode() ? null : openWeather.get("visibility").asInt();
    }

    private Map<String, Object> extractWind(JsonNode openWeather) {
        Map<String, Object> wind = new LinkedHashMap<>();
        JsonNode windNode = openWeather.path("wind");
        wind.put("speed", windNode.path("speed").isMissingNode() ? null : windNode.get("speed").asDouble());
        return wind;
    }

    private Long extractDatetime(JsonNode openWeather) {
        return openWeather.path("dt").isMissingNode() ? null : openWeather.get("dt").asLong();
    }

    private Map<String, Object> extractSys(JsonNode openWeather) {
        Map<String, Object> sys = new LinkedHashMap<>();
        JsonNode sysNode = openWeather.path("sys");
        sys.put("sunrise", sysNode.path("sunrise").isMissingNode() ? null : sysNode.get("sunrise").asLong());
        sys.put("sunset", sysNode.path("sunset").isMissingNode() ? null : sysNode.get("sunset").asLong());
        return sys;
    }

    private Integer extractTimezone(JsonNode openWeather) {
        return openWeather.path("timezone").isMissingNode() ? null : openWeather.get("timezone").asInt();
    }

    private String extractCityName(JsonNode openWeather) {
        return Optional.ofNullable(openWeather.get("name")).map(JsonNode::asText).orElse(null);
    }


    private String normalizeCityKey(String city) {
        if (city == null || city.isBlank()) throw new WeatherSdkException("City name must be provided");
        return city.trim().toLowerCase();
    }

    private void ensureOpen() {
        if (closed) throw new WeatherSdkException("Client is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private static class CityCoordinates {
        final double lat;
        final double lon;
        CityCoordinates(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
}


