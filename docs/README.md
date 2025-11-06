# Документация WeatherSDK API

Эта директория содержит OpenAPI 3.0 описание в файле openapi.yaml, описывающий простой HTTP происходящий в библиотеке.

- Эндпоинт: `GET /weather?city=...`
- Ответ: нормализованный ответ Json библиотеки

Use cases:
- Вы можете загрузить `openapi.yaml` в Swagger, Postman или куда вам наиболее удобно.

Быстрый старт:

```bash

npx -y swagger-ui-watcher ./openapi.yaml
```

