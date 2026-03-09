# Двухфакторная аутентификация через Telegram

Документация описывает архитектуру, установку и настройку модуля Telegram 2FA (`TelegramAuthenticator`) для Keycloak.

---

## Обзор

Модуль реализует второй фактор аутентификации через Telegram Bot API и поддерживает два режима:

| Режим | Описание |
|-------|----------|
| **OTP** | Бот отправляет числовой код; пользователь вводит его в браузере |
| **PUSH** | Бот отправляет сообщение с кнопками **Approve** / **Deny**; пользователь нажимает в Telegram, затем подтверждает в браузере |

Каждый Realm использует **собственный** Telegram-бот: токен и имя задаются в конфигурации authenticator.

---

## Архитектура

### Ключевые компоненты

| Файл | Роль |
|------|------|
| `TelegramAuthenticator` | Основной authenticator: генерирует OTP / инициирует PUSH |
| `TelegramAuthenticatorFactory` | Фабрика + описание конфиг-полей для Admin Console |
| `TelegramBotClient` | HTTP-клиент к Telegram Bot API |
| `TelegramPushStatusStore` | In-memory хранилище токенов подтверждения с TTL |
| `TelegramSetupRequiredAction` | Required Action привязки Telegram Chat ID пользователя |
| `TelegramWebhookResource` | JAX-RS эндпоинт для приёма callback_query от бота |
| `TelegramWebhookResourceProviderFactory` | Регистрирует эндпоинт через `RealmResourceProvider` SPI |

### Эндпоинт webhook

```
POST /realms/{realm}/telegram-webhook
```

Путь формируется из `TelegramWebhookResourceProviderFactory.getId()` = `"telegram-webhook"`.
Ресурс реализует `RealmResourceProvider` и регистрируется через:
```
META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory
```

### Атрибут пользователя

`telegram_user_id` — Telegram Chat ID, хранится в атрибутах пользователя Keycloak.
Привязывается вручную администратором или автоматически через Required Action при первом входе.

### FreeMarker шаблоны (тема `keycloak.v2`)

| Шаблон | Страница |
|--------|----------|
| `login-telegram-otp.ftl` | Ввод OTP-кода |
| `login-telegram-push.ftl` | Ожидание подтверждения в Telegram |
| `login-telegram-setup.ftl` | Привязка Telegram Chat ID |

### Конфигурационные поля authenticator

| Ключ | Описание | Значение по умолчанию |
|------|----------|-----------------------|
| `telegramBotToken` | Токен бота от @BotFather | — |
| `telegramBotUsername` | Username бота (без @), используется для deep-link на setup-странице | — |
| `telegramMode` | `OTP` или `PUSH` | `OTP` |
| `otpLength` | Количество цифр в коде | `6` |
| `otpTimeout` | Время жизни OTP, секунды | `300` |
| `pushTimeout` | Время ожидания подтверждения, секунды | `60` |
| `maxRetries` | Максимум попыток ввода OTP | `3` |
| `fallbackMode` | Fallback при недоступности Telegram: `NONE`, `RECOVERY_CODES`, `TOTP` | `RECOVERY_CODES` |
| `webhookEnabled` | Использовать webhook вместо polling | `false` |

---

## Установка

### Шаг 1. Создание Telegram-бота

1. Откройте `@BotFather` в Telegram.
2. Выполните `/newbot` — получите токен вида `123456:ABC-DEF...`.
3. Запомните **username** бота (например, `my_auth_bot`).

### Шаг 2. Сборка

```bash
./mvnw clean install -DskipTests -pl server-spi-private,services,themes -am
```

### Шаг 3. Настройка в Admin Console

1. Войдите в Admin Console → нужный **Realm**.
2. **Authentication → Flows** — откройте или скопируйте поток `browser`.
3. Добавьте step: **Add step → Telegram Authenticator** → установите требование `Required`.
4. Нажмите шестерёнку рядом с execution → заполните конфигурацию:
   - **Telegram Bot Token** — токен из шага 1.
   - **Telegram Bot Username** — username бота без `@`.
   - **Mode** — `OTP` или `PUSH`.
   - Остальные поля при необходимости.
5. Сохраните.

### Шаг 4. Required Action для привязки Chat ID

1. **Authentication → Required Actions** → найдите **Configure Telegram for 2FA**.
2. Включите (**Enabled**).
3. При необходимости установите **Set as default action** — тогда пользователи без `telegram_user_id` будут автоматически перенаправлены на страницу привязки при первом входе.

На странице привязки пользователю показывается ссылка для открытия бота (deep-link `https://t.me/<botUsername>`). Ссылка формируется из поля `telegramBotUsername` конфигурации.

---

## Потоки аутентификации

### OTP-режим

```
Пользователь входит
    │
    ▼
TelegramAuthenticator.authenticate()
    ├── нет telegram_user_id → Required Action (привязка)
    └── есть telegram_user_id
            │
            ▼
        TelegramBotClient.sendOtpMessage(chatId, code)
            │
            ▼
        Страница login-telegram-otp.ftl
            │
            ▼
        TelegramAuthenticator.action()
            ├── код верный, не истёк ──────────────── success()
            ├── код неверный, попыток < maxRetries → снова форма
            ├── попытки исчерпаны ──────────────────── failureChallenge(INVALID_CREDENTIALS)
            └── код истёк ─────────────────────────── failureChallenge(EXPIRED_CODE)
```

### PUSH-режим

```
Пользователь входит
    │
    ▼
TelegramAuthenticator.authenticate()
    └── TelegramBotClient.sendPushNotification(chatId, token)
            ├── TelegramPushStatusStore.add(token, rootId, tabId, botToken, ttlMs)
            └── Страница login-telegram-push.ftl (кнопка «Продолжить»)

                            ↑ параллельно ↓

Пользователь нажимает Approve/Deny в Telegram
    │
    └── Telegram → POST /realms/{realm}/telegram-webhook
                        │
                        └── TelegramWebhookResource
                                ├── TelegramPushStatusStore.remove(token)
                                ├── authSession.setAuthNote("telegram_approved", "true/false")
                                └── TelegramBotClient.answerCallbackQuery(queryId, approved)

Пользователь нажимает «Продолжить» в браузере
    │
    ▼
TelegramAuthenticator.action()
    ├── pushTimeout истёк ─────────────── failureChallenge(EXPIRED_CODE)
    ├── telegram_approved = true ─────── success()
    └── telegram_approved = false ────── снова форма (сообщение «not_approved»)
```

---

## Webhook vs Polling

### Webhook (рекомендуется для production)

Telegram отправляет callback_query на ваш эндпоинт в момент нажатия кнопки.

**Требования:**
- Keycloak должен быть доступен по публичному HTTPS-адресу.
- В конфигурации authenticator установить `webhookEnabled = true`.

**Регистрация webhook у Telegram:**
```bash
curl "https://api.telegram.org/bot<BOT_TOKEN>/setWebhook?url=https://keycloak.example.com/realms/<realm>/telegram-webhook"
```

**Проверка:**
```bash
curl "https://api.telegram.org/bot<BOT_TOKEN>/getWebhookInfo"
```

### Без webhook (по умолчанию)

При `webhookEnabled = false` бот не регистрирует webhook автоматически. Webhook-эндпоинт
`/realms/{realm}/telegram-webhook` тем не менее **всегда активен** — его можно зарегистрировать
вручную командой `setWebhook` в любой момент.

> Для тестовых окружений без публичного HTTPS можно использовать [ngrok](https://ngrok.com)
> или аналогичный туннель.

---

## Безопасность

- Токен бота хранится как `PASSWORD`-поле (маскируется в UI, шифруется Keycloak Vault-интеграцией).
- Токены подтверждения PUSH — UUID, привязаны к сессии и имеют TTL равный `pushTimeout`.
- `TelegramPushStatusStore` — in-memory, **не реплицируется в кластере**. Для кластерных
  инсталляций рекомендуется заменить хранилище на Infinispan-кэш.
- Webhook-эндпоинт не требует аутентификации (Telegram не поддерживает OAuth на уровне webhook).
  При необходимости ограничьте доступ к `/realms/*/telegram-webhook` на уровне reverse-proxy
  по [IP-диапазонам Telegram](https://core.telegram.org/bots/webhooks#the-short-version).

---

## Автоматизированное развёртывание

Скрипт `misc/scripts/setup-telegram-2fa.sh` выполняет полную настройку через Admin REST API.

### Предварительные требования

- `curl` ≥ 7.x
- `jq` ≥ 1.6
- Keycloak ≥ 20 (без `/auth`-префикса в URL)

### Использование

```bash
# Отредактируйте переменные в начале скрипта:
#   KEYCLOAK_URL, REALM, ADMIN_USER, ADMIN_PASS, BOT_TOKEN, BOT_USERNAME, MODE, ...

chmod +x misc/scripts/setup-telegram-2fa.sh
./misc/scripts/setup-telegram-2fa.sh
```

### Что делает скрипт

| Шаг | Действие |
|-----|----------|
| 1 | Получает admin-токен через OpenID Connect password grant |
| 2 | Создаёт конфигурацию authenticator со всеми параметрами (включая `telegramBotUsername`) |
| 3 | Добавляет execution `auth-telegram` в Browser flow с `REQUIRED` |
| 4 | Регистрирует Required Action `telegram-setup` и включает как `defaultAction` |
| 5 | При `WEBHOOK_ENABLED=true` — регистрирует webhook у Telegram через `setWebhook` |

---

## Устранение неполадок

| Симптом | Вероятная причина | Решение |
|---------|-------------------|---------|
| Страница OTP/PUSH не отображается | Шаблон не найден | Убедитесь, что Realm использует тему `keycloak.v2` |
| `telegram_send_failed` | Неверный токен или недоступна сеть | Проверьте `telegramBotToken` и сетевой доступ к `api.telegram.org` |
| `not_approved` при PUSH | Пользователь не нажал кнопку до истечения `pushTimeout` | Увеличьте `pushTimeout` или нажмите «Продолжить» снова |
| `push_expired` при PUSH | `pushTimeout` истёк до нажатия «Продолжить» | Увеличьте `pushTimeout` |
| Webhook не получает вызовы | Эндпоинт недоступен из интернета | Проверьте HTTPS, DNS, firewall; убедитесь в регистрации webhook (`getWebhookInfo`) |
| Deep-link ведёт на `#` | `telegramBotUsername` не заполнен | Укажите username в конфигурации authenticator |
| Required Action не появляется | Action не включён как `defaultAction` | Admin Console → Authentication → Required Actions → включить |
| `telegram_user_id` не сохраняется | Поле `telegramId` отправляется пустым | Убедитесь, что пользователь заполнил поле на странице setup |
