package org.keycloak.authentication.authenticators.browser.telegram;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mockito;

import java.io.IOException;

public class TelegramBotClientTest {

    private CloseableHttpClient httpClient;
    private KeycloakSession session;

    @Before
    public void setup() {
        httpClient = Mockito.mock(CloseableHttpClient.class);
        HttpClientProvider provider = Mockito.mock(HttpClientProvider.class);
        Mockito.when(provider.getHttpClient()).thenReturn(httpClient);
        session = Mockito.mock(KeycloakSession.class);
        Mockito.when(session.getProvider(HttpClientProvider.class)).thenReturn(provider);
    }

    @Test
    public void sendOtpMessage_shouldInvokeHttpClient() throws Exception {
        TelegramBotClient client = new TelegramBotClient(session, "token");
        CloseableHttpResponse resp = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(resp.getStatusLine()).thenReturn(new org.apache.http.StatusLine() {
            @Override public org.apache.http.ProtocolVersion getProtocolVersion() { return null; }
            @Override public int getStatusCode() { return 200; }
            @Override public String getReasonPhrase() { return "OK"; }
        });
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(resp);
        client.sendOtpMessage("42", "123456");
        Mockito.verify(httpClient).execute(Mockito.any(HttpPost.class));
    }

    @Test(expected = TelegramBotClient.TelegramException.class)
    public void sendOtpMessage_httpError_shouldThrow() throws Exception {
        TelegramBotClient client = new TelegramBotClient(session, "token");
        CloseableHttpResponse resp = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(resp.getStatusLine()).thenReturn(new org.apache.http.StatusLine() {
            @Override
            public org.apache.http.ProtocolVersion getProtocolVersion() {return null;}
            @Override
            public int getStatusCode() { return 500; }
            @Override
            public String getReasonPhrase() { return ""; }
        });
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(resp);
        client.sendOtpMessage("42","0000");
    }

    @Test(expected = TelegramBotClient.TelegramException.class)
    public void answerCallbackQuery_ioException_shouldThrow() throws Exception {
        TelegramBotClient client = new TelegramBotClient(session, "t");
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenThrow(new IOException("boom"));
        client.answerCallbackQuery("id", true);
    }
}
