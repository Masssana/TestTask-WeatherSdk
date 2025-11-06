import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.max.weather.WeatherClient;
import ru.max.weather.WeatherClientConfig;
import ru.max.weather.WeatherMode;
import ru.max.weather.exceptions.WeatherSdkException;
import ru.max.weather.internal.CacheEntry;
import ru.max.weather.internal.LruCache;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class WeatherClientTest {
    private WeatherClientConfig config;

    @BeforeEach
    void setup() {
        config = WeatherClientConfig.builder(WeatherMode.ON_DEMAND)
                .cacheCapacity(5)
                .freshnessTtl(Duration.ofMinutes(10))
                .build();
    }

    @Test
    void testGetInstance_SameKeyReturnsSameInstance() {
        WeatherClient client1 = WeatherClient.getInstance("test-key", config);
        WeatherClient client2 = WeatherClient.getInstance("test-key", config);

        assertSame(client1, client2);
        WeatherClient.deleteInstance("test-key");
    }

    @Test
    void testDeleteInstance_RemovesClient() {
        WeatherClient client = WeatherClient.getInstance("key123", config);
        assertTrue(WeatherClient.deleteInstance("key123"));
        assertFalse(WeatherClient.deleteInstance("key123"));
    }

    @Test
    void testGetInstance_ThrowsIfApiKeyMissing() {
        assertThrows(WeatherSdkException.class, () ->
                WeatherClient.getInstance("", config)
        );
    }

    @Test
    void testClose_MarksAsClosed() throws Exception {
        WeatherClient client = WeatherClient.getInstance("some-key", config);

        client.close();

        Field closedField = WeatherClient.class.getDeclaredField("closed");
        closedField.setAccessible(true);
        boolean closed = (boolean) closedField.get(client);
        assertTrue(closed);

        WeatherClient.deleteInstance("some-key");
    }

    @Test
    void testCacheCapacityEnforced() {
        LruCache<String, CacheEntry> cache = new LruCache<>(2);
        cache.put("a", new CacheEntry(0, "{}", 1, 1));
        cache.put("b", new CacheEntry(0, "{}", 1, 1));
        cache.put("c", new CacheEntry(0, "{}", 1, 1));

        assertFalse(cache.containsKey("a"));
        assertTrue(cache.containsKey("b"));
        assertTrue(cache.containsKey("c"));
    }
}
