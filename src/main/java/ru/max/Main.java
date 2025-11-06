package ru.max;

import ru.max.weather.WeatherClient;
import ru.max.weather.WeatherClientConfig;
import ru.max.weather.WeatherMode;

public class Main {
    public static void main(String[] args) {
        String apiKey = "someKey";
        WeatherClientConfig config = WeatherClientConfig.builder(WeatherMode.POLLING)
                .build();
        WeatherClient client = WeatherClient.getInstance(apiKey, config);
        String json = client.getWeatherJson("Moscow");
        String json2 = client.getWeatherJson("New-York");
        System.out.println(json);
        System.out.println(json2);
        WeatherClient.deleteInstance(apiKey);
    }
}