Weather SDK (Java)

Java SDK для доступа к OpenWeather API с встроенным кэшированием и опциональным режимом опроса (polling) для мгновенных ответов без задержек.
 
Возможности

- Два режима: по запросу и периодический опрос
- Кэш до 10 городов с временем актуальности до 10 минут
- Фоновое обновление данных в режимах опроса (polling)
- Один экземпляр на каждый API ключ, с возможностью явного удаления
- Предсказуемая структура JSON которую удобно читать
- Обработка ошибок с исключениями

## Установка

Это Maven проект. Соберите его локально или используйте собранный jar файла с репозитория:

mvn -q -DskipTests package

Затем подключите сгенерированный или скачанный JAR-файл target/weather-sdk-0.1.0.jar в свой проект.
## Использование

Можно задать API-ключ как переменную окружения OPENWEATHER_API_KEY или передать напрямую в переменную или в метод.

### Режим опроса

String apiKey = System.getenv("OPENWEATHER_API_KEY"); или просто передайте ключ в переменную 
WeatherClientConfig config = WeatherClientConfig.builder(WeatherMode.ON_DEMAND).build();
WeatherClient client = WeatherClient.getInstance(apiKey, config);
String json = client.getWeatherJson("London");
System.out.println(json);
WeatherClient.deleteInstance(apiKey);

### Периодический опрос

String apiKey = System.getenv("OPENWEATHER_API_KEY");
WeatherClientConfig config = WeatherClientConfig.builder(WeatherMode.POLLING)
        .pollingInterval(java.time.Duration.ofMinutes(1))
        .build();
WeatherClient client = WeatherClient.getInstance(apiKey, config);
System.out.println(client.getWeatherJson("New York"));
WeatherClient.deleteInstance(apiKey);


## Структура возвращаемого JSON

```json
{
  "weather": { "main": "Clouds", "description": "scattered clouds" },
  "temperature": { "temp": 269.6, "feels_like": 267.57 },
  "visibility": 10000,
  "wind": { "speed": 1.38 },
  "datetime": 1675744800,
  "sys": { "sunrise": 1675751262, "sunset": 1675787560 },
  "timezone": 10800,
  "name": "Moscow"
}
```

Некоторые поля могут быть null, если данные отсутствуют в исходном ответе OpenWeather.

## Детали реализации

- Кэш: реализован как LRU с упорядочиванием по обращениям, максимум 10 элементов, время жизни 10 минут, но его можно изменить
- Polling: обновляет все города в кэше с заданным интервалом
- Singleton: один экземпляр WeatherClient на API-ключ и метод deleteInstance(key) удаляет и останавливает клиент
- Exceptions: все Исключения регулируются с помощью WeatherSdkException и с подробной информации об ошибке

## Инструменты

- Java 21
- Maven, Jackson for JSON

