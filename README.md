# URL Shortener

Сервис сокращения ссылок на Spring Boot. Хранит ссылки в PostgreSQL, кэширует редиректы в Redis, считает клики и поддерживает срок жизни ссылки.

## Возможности

- Создание короткой ссылки через REST API
- Редирект (HTTP 302) на оригинальный URL
- Кэширование в Redis на 24 часа (cache-aside)
- Счётчик кликов, который обновляется асинхронно и не замедляет редирект
- Опциональный срок жизни ссылки (`expireDays`)
- Статистика по ссылке
- Валидация входных данных (только `http://` и `https://`)

## Стек

| Компонент  | Технология                                |
|------------|-------------------------------------------|
| Язык       | Java 21                                   |
| Фреймворк  | Spring Boot 3.5 (Web, Data JPA, Validation) |
| БД         | PostgreSQL 16                             |
| Кэш        | Redis 7                                   |
| Сборка     | Maven (wrapper в комплекте)               |
| Тесты      | JUnit 5, Mockito, MockMvc                 |

## Быстрый старт

### Требования

- JDK 21
- Docker и Docker Compose

### 1. Запустить PostgreSQL и Redis

Из корня проекта:

```bash
docker compose up -d
```

Будут подняты два контейнера:

| Сервис     | Контейнер      | Порт на хосте |
|------------|----------------|---------------|
| PostgreSQL | `url_postgres` | `5433`        |
| Redis      | `url_redis`    | `6379`        |

### 2. Запустить приложение

```bash
cd urlshortener
./mvnw spring-boot:run
```

На Windows: `mvnw.cmd spring-boot:run`.

Приложение будет доступно на `http://localhost:8080`. Таблица `urls` создаётся автоматически (`ddl-auto=update`).

### 3. Запустить тесты

```bash
cd urlshortener
./mvnw test
```

## API

### `POST /shorten` — создать короткую ссылку

Тело запроса:

| Поле         | Тип     | Обязательно | Описание                                        |
|--------------|---------|-------------|-------------------------------------------------|
| `url`        | string  | да          | Исходный URL, должен начинаться с `http://` или `https://` |
| `expireDays` | integer | нет         | Через сколько дней ссылка перестанет работать   |

Пример:

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/very/long/path", "expireDays": 7}'
```

Ответ `200 OK`:

```json
{
  "shortUrl": "http://localhost:8080/1",
  "originalUrl": "https://example.com/very/long/path",
  "clickCount": 0,
  "expiresAt": "2026-11-04T12:00:00.000"
}
```

### `GET /{code}` — редирект

Возвращает `302 Found` с заголовком `Location` на оригинальный URL.

```bash
curl -i http://localhost:8080/1
```

### `GET /stats/{code}` — статистика

```bash
curl http://localhost:8080/stats/1
```

Ответ имеет тот же формат, что и ответ `POST /shorten`, с актуальным `clickCount`.

### Ошибки

Ошибки возвращаются с кодом `400 Bad Request` и телом:

```json
{ "error": "URL должен начинаться с http:// или https://" }
```

Это касается ошибок валидации, несуществующей ссылки и истёкшей ссылки.

## Как это работает

**Генерация кода.** Ссылка сначала сохраняется в БД, чтобы получить автоинкрементный `id`. Затем `id` кодируется в Base62 (`0-9a-zA-Z`), и результат становится коротким кодом. Так коды гарантированно уникальны, а коллизии невозможны.

**Редирект.**

1. Сервис ищет `url:{code}` в Redis.
2. При промахе читает запись из PostgreSQL, проверяет срок жизни и кладёт URL в Redis на 24 часа.
3. Счётчик кликов увеличивается в отдельном виртуальном потоке, поэтому не задерживает ответ.

## Структура проекта

```
.
├── docker-compose.yml              # PostgreSQL + Redis
└── urlshortener/
    ├── pom.xml
    └── src/
        ├── main/java/com/example/urlshortener/
        │   ├── controller/         # UrlController, GlobalExceptionHandler
        │   ├── service/            # UrlService — основная логика
        │   ├── repository/         # UrlRepository (Spring Data JPA)
        │   ├── model/              # Url — JPA-сущность
        │   ├── dto/                # ShortenRequest, ShortenResponse
        │   └── util/               # Base62
        ├── main/resources/
        │   └── application.properties
        └── test/java/...           # Base62Test, UrlControllerTest
```

## Конфигурация

Настройки находятся в `urlshortener/src/main/resources/application.properties`:

| Параметр                     | Значение по умолчанию                            |
|------------------------------|--------------------------------------------------|
| `spring.datasource.url`      | `jdbc:postgresql://localhost:5433/urlshortener`  |
| `spring.datasource.username` | `postgres`                                       |
| `spring.datasource.password` | `password`                                       |
| `spring.data.redis.host`     | `localhost`                                      |
| `spring.data.redis.port`     | `6379`                                           |
| `server.port`                | `8080`                                           |
| `app.base-url`               | `http://localhost:8080`                          |

Параметр `app.base-url` используется для формирования поля `shortUrl`. Для продакшена задайте здесь свой домен. Любой параметр можно переопределить переменной окружения, например `APP_BASE_URL=https://sho.rt`.

> Учётные данные в репозитории подходят только для локальной разработки. В продакшене используйте секреты и переменные окружения.

## Известные ограничения

- Срок жизни проверяется только при чтении из БД. Если ссылка уже лежит в Redis, она будет отдаваться до истечения TTL кэша (24 часа), даже если срок самой ссылки закончился.
- «Не найдена» и «истекла» возвращаются как `400`, а не `404` / `410`.
- Нет кастомных кодов, аутентификации и ограничения частоты запросов.
