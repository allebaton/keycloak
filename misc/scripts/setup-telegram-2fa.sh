#!/usr/bin/env bash
# =============================================================================
# setup-telegram-2fa.sh — автоматизированная настройка Telegram 2FA в Keycloak
#
# Зависимости: curl, jq
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Переменные конфигурации — измените под своё окружение
# ---------------------------------------------------------------------------
KEYCLOAK_URL="http://localhost:8080"   # базовый URL без /auth (Keycloak 20+)
REALM="master"                         # realm, в котором включается Telegram 2FA
ADMIN_USER="admin"
ADMIN_PASS="admin"

BOT_TOKEN="<telegram-bot-token>"       # токен от @BotFather
BOT_USERNAME="<bot_username>"          # username бота без @, используется для deep-link
MODE="OTP"                             # OTP — код по SMS-стилю, PUSH — кнопки Approve/Deny
OTP_LENGTH=6
OTP_TIMEOUT=300                        # секунды жизни OTP-кода
PUSH_TIMEOUT=60                        # секунды ожидания подтверждения PUSH
MAX_RETRIES=3
FALLBACK_MODE="RECOVERY_CODES"         # NONE | RECOVERY_CODES | TOTP
WEBHOOK_ENABLED=false                  # true — webhook, false — polling (по умолчанию)

FLOW_ALIAS="browser"                   # алиас flow (строчные буквы для REST API)
REQUIRED_ACTION_ID="telegram-setup"   # совпадает с TelegramSetupRequiredAction.PROVIDER_ID

# ---------------------------------------------------------------------------
# Вспомогательные функции
# ---------------------------------------------------------------------------
die() { echo "ERROR: $*" >&2; exit 1; }

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Требуется утилита: $1"; }
require_cmd curl
require_cmd jq

admin_token() {
  curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password&client_id=admin-cli" \
    | jq -r '.access_token'
}

api() {
  local method=$1; shift
  local path=$1;   shift
  curl -sf -X "${method}" "${KEYCLOAK_URL}/admin/realms/${REALM}${path}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    "$@"
}

# ---------------------------------------------------------------------------
# 1. Получаем admin-токен
# ---------------------------------------------------------------------------
echo ">>> Получаем admin-токен..."
TOKEN=$(admin_token)
[ -n "${TOKEN}" ] && [ "${TOKEN}" != "null" ] || die "Не удалось получить токен администратора"
echo "    OK"

# ---------------------------------------------------------------------------
# 2. Создаём конфигурацию аутентификатора
#    POST /admin/realms/{realm}/authentication/authenticator-providers не работает
#    для пользовательских провайдеров — конфиг создаём через /authentication/config
# ---------------------------------------------------------------------------
echo ">>> Создаём конфигурацию Telegram аутентификатора..."
CONFIG_PAYLOAD=$(jq -n \
  --arg name        "telegram-2fa-${REALM}" \
  --arg token       "${BOT_TOKEN}" \
  --arg username    "${BOT_USERNAME}" \
  --arg mode        "${MODE}" \
  --arg otpLen      "${OTP_LENGTH}" \
  --arg otpTimeout  "${OTP_TIMEOUT}" \
  --arg pushTimeout "${PUSH_TIMEOUT}" \
  --arg maxRetries  "${MAX_RETRIES}" \
  --arg fallback    "${FALLBACK_MODE}" \
  --argjson webhook "${WEBHOOK_ENABLED}" \
  '{
    alias:  $name,
    config: {
      telegramBotToken:    $token,
      telegramBotUsername: $username,
      telegramMode:        $mode,
      otpLength:           $otpLen,
      otpTimeout:          $otpTimeout,
      pushTimeout:         $pushTimeout,
      maxRetries:          $maxRetries,
      fallbackMode:        $fallback,
      webhookEnabled:      ($webhook | tostring)
    }
  }')

CONFIG_LOCATION=$(curl -sf -o /dev/null -w '%{redirect_url}' -X POST \
  "${KEYCLOAK_URL}/admin/realms/${REALM}/authentication/config" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "${CONFIG_PAYLOAD}" || true)

# Некоторые версии Keycloak возвращают Location, другие — тело с id
if [ -n "${CONFIG_LOCATION}" ]; then
  CONFIG_ID=$(basename "${CONFIG_LOCATION}")
else
  CONFIG_ID=$(api POST "/authentication/config" -d "${CONFIG_PAYLOAD}" | jq -r '.id')
fi

[ -n "${CONFIG_ID}" ] && [ "${CONFIG_ID}" != "null" ] \
  || die "Не удалось создать конфигурацию аутентификатора"
echo "    Config ID: ${CONFIG_ID}"

# ---------------------------------------------------------------------------
# 3. Добавляем execution в Browser flow
# ---------------------------------------------------------------------------
echo ">>> Добавляем Telegram authenticator в flow '${FLOW_ALIAS}'..."
api POST "/authentication/flows/${FLOW_ALIAS}/executions/execution" \
  -d '{"provider":"auth-telegram"}' > /dev/null

# Получаем ID только что добавленного execution
EXECUTION_ID=$(api GET "/authentication/flows/${FLOW_ALIAS}/executions" \
  | jq -r '.[] | select(.providerId == "auth-telegram") | .id' | tail -1)

[ -n "${EXECUTION_ID}" ] && [ "${EXECUTION_ID}" != "null" ] \
  || die "Execution 'auth-telegram' не найден в flow после добавления"
echo "    Execution ID: ${EXECUTION_ID}"

# Устанавливаем requirement = REQUIRED
api PUT "/authentication/flows/${FLOW_ALIAS}/executions" \
  -d "{\"id\":\"${EXECUTION_ID}\",\"requirement\":\"REQUIRED\"}" > /dev/null

# Привязываем конфигурацию к execution
api PUT "/authentication/executions/${EXECUTION_ID}/config" \
  -d "{\"id\":\"${CONFIG_ID}\"}" > /dev/null || \
api POST "/authentication/executions/${EXECUTION_ID}/config" \
  -d "{\"id\":\"${CONFIG_ID}\"}" > /dev/null

echo "    OK — Telegram 2FA добавлен в flow как REQUIRED"

# ---------------------------------------------------------------------------
# 4. Регистрируем Required Action (если ещё не зарегистрирован)
# ---------------------------------------------------------------------------
echo ">>> Регистрируем Required Action '${REQUIRED_ACTION_ID}'..."
EXISTING=$(api GET "/authentication/required-actions" \
  | jq -r ".[] | select(.providerId == \"${REQUIRED_ACTION_ID}\") | .providerId")

if [ -z "${EXISTING}" ]; then
  api POST "/authentication/register-required-action" \
    -d "{\"providerId\":\"${REQUIRED_ACTION_ID}\",\"name\":\"Configure Telegram for 2FA\"}" > /dev/null
  echo "    Зарегистрирован"
else
  echo "    Уже зарегистрирован"
fi

# Включаем Required Action
api PUT "/authentication/required-actions/${REQUIRED_ACTION_ID}" \
  -d "{\"alias\":\"${REQUIRED_ACTION_ID}\",\"name\":\"Configure Telegram for 2FA\",\"enabled\":true,\"defaultAction\":true}" \
  > /dev/null
echo "    OK — defaultAction включён"

# ---------------------------------------------------------------------------
# 5. (Опционально) Устанавливаем webhook у Telegram
# ---------------------------------------------------------------------------
if [ "${WEBHOOK_ENABLED}" = "true" ]; then
  echo ">>> Регистрируем webhook у Telegram..."
  WEBHOOK_URL="${KEYCLOAK_URL}/realms/${REALM}/telegram-webhook"
  RESULT=$(curl -sf \
    "https://api.telegram.org/bot${BOT_TOKEN}/setWebhook?url=${WEBHOOK_URL}")
  echo "${RESULT}" | jq -r 'if .ok then "    OK — webhook: \(.result)" else "    ОШИБКА: \(.description)" end'
else
  echo ">>> Webhook отключён (WEBHOOK_ENABLED=false). Используется polling."
  echo "    Для ручной установки webhook выполните:"
  echo "    curl https://api.telegram.org/bot\${BOT_TOKEN}/setWebhook?url=${KEYCLOAK_URL}/realms/${REALM}/telegram-webhook"
fi

# ---------------------------------------------------------------------------
# 6. Пример: назначить атрибут telegram_user_id конкретному пользователю
# ---------------------------------------------------------------------------
# USER_ID="<keycloak-user-uuid>"
# TELEGRAM_CHAT_ID="<telegram-chat-id>"
# api PUT "/users/${USER_ID}" \
#   -d "{\"attributes\":{\"telegram_user_id\":[\"${TELEGRAM_CHAT_ID}\"]}}" > /dev/null

echo ""
echo "=== Настройка завершена ==="
echo "Realm  : ${REALM}"
echo "Mode   : ${MODE}"
echo "Webhook: ${WEBHOOK_ENABLED}"
echo ""
echo "Следующие шаги:"
echo "  1. Убедитесь, что пользователи имеют Telegram Chat ID в атрибуте 'telegram_user_id'"
echo "     либо назначьте defaultAction '${REQUIRED_ACTION_ID}' — тогда пользователь"
echo "     привяжет Telegram при следующем входе."
echo "  2. При MODE=PUSH убедитесь, что endpoint доступен по адресу:"
echo "     ${KEYCLOAK_URL}/realms/${REALM}/telegram-webhook"
