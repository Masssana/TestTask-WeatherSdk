import com.example.weather.*;

public class ExampleOnDemand {
    public static void main(String[] args) {
        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        String apiKey2 = "someKey" // или же передайте его в переменную
        WeatherClientConfig config = WeatherClientConfig.builder(WeatherMode.ON_DEMAND)
                .build();
        WeatherClient client = WeatherClient.getInstance(apiKey, config);
        String json = client.getWeatherJson("London");
        System.out.println(json);
        WeatherClient.deleteInstance(apiKey);
    }
}


