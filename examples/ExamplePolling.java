import com.example.weather.*;
import java.time.Duration;

public class ExamplePolling {
    public static void main(String[] args) throws InterruptedException {
        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        String apiKey2 = "someKey" // или же передайте его в переменную
        WeatherClientConfig config = WeatherClientConfig.builder(WeatherMode.POLLING)
                .pollingInterval(Duration.ofMinutes(1))
                .build();
        WeatherClient client = WeatherClient.getInstance(apiKey, config);

        System.out.println(client.getWeatherJson("New York"));
        Thread.sleep(2000);
        System.out.println(client.getWeatherJson("New York"));

        WeatherClient.deleteInstance(apiKey);
    }
}


