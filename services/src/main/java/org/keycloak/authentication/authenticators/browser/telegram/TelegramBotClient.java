package org.keycloak.authentication.authenticators.browser.telegram;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TelegramBotClient {

    private final String botToken;
    private final CloseableHttpClient httpClient;

    public TelegramBotClient(KeycloakSession session, String botToken) {
        this.botToken = botToken;
        HttpClientProvider provider = session.getProvider(HttpClientProvider.class);
        this.httpClient = provider.getHttpClient();
    }

    private String apiUrl(String method) {
        return "https://api.telegram.org/bot" + botToken + "/" + method;
    }

    public void sendOtpMessage(String chatId, String code) throws TelegramException {
        String text = "Your authentication code is: " + code;
        sendMessage(chatId, text, null);
    }

    public void sendPushNotification(String chatId, String approvalToken) throws TelegramException {
        String inlineKeyboardJson = "{\"inline_keyboard\":[[{\"text\":\"Approve\",\"callback_data\":\"APPROVE:" + approvalToken + "\"},{\"text\":\"Deny\",\"callback_data\":\"DENY:" + approvalToken + "\"}]]}";
        sendMessage(chatId, "Please approve login request", inlineKeyboardJson);
    }

    private void sendMessage(String chatId, String text, String inlineKeyboardJson) throws TelegramException {
        HttpPost post = new HttpPost(apiUrl("sendMessage"));
        try {
            StringBuilder payload = new StringBuilder();
            payload.append("{\"chat_id\":\"").append(chatId).append("\",\"text\":\"").append(text).append("\"");
            if (inlineKeyboardJson != null) {
                payload.append(",\"reply_markup\":").append(inlineKeyboardJson);
            }
            payload.append("}");
            post.setEntity(new StringEntity(payload.toString(), StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/json");
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 400) {
                    throw new TelegramException("Telegram API error " + status);
                }
            }
        } catch (IOException e) {
            throw new TelegramException("Failed to send message", e);
        }
    }

    public void answerCallbackQuery(String callbackQueryId, boolean approved) throws TelegramException {
        HttpPost post = new HttpPost(apiUrl("answerCallbackQuery"));
        try {
            String json = "{\"callback_query_id\":\"" + callbackQueryId + "\",\"text\":\"" + (approved ? "Approved" : "Denied") + "\"}";
            post.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/json");
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 400) {
                    throw new TelegramException("Telegram API error " + status);
                }
            }
        } catch (IOException e) {
            throw new TelegramException("Failed to answer callback", e);
        }
    }

    public static class TelegramException extends Exception {
        public TelegramException(String message) {
            super(message);
        }

        public TelegramException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
