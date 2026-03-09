package org.keycloak.authentication.authenticators.browser.telegram;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsPages;
import org.keycloak.authentication.authenticators.browser.telegram.TelegramSetupRequiredAction;
import org.keycloak.models.UserModel;
import jakarta.ws.rs.core.Response;

public class TelegramAuthenticator implements Authenticator {

    // authentication session note keys
    public static final String NOTE_OTP = "telegram_otp_code";
    public static final String NOTE_OTP_EXPIRES = "telegram_otp_expires";
    public static final String NOTE_ATTEMPTS = "telegram_attempt_count";
    public static final String NOTE_APPROVAL_TOKEN = "telegram_approval_token";
    public static final String NOTE_APPROVED = "telegram_approved";
    public static final String NOTE_PUSH_EXPIRES = "telegram_push_expires";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            context.challenge(context.form().setError("no_user").createErrorPage(Response.Status.BAD_REQUEST));
            return;
        }

        String telegramId = user.getFirstAttribute(TelegramAuthenticatorFactory.USER_TELEGRAM_ID);
        if (telegramId == null || telegramId.isEmpty()) {
            // ask user to configure Telegram
            context.failureChallenge(AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED,
                    context.form().setError("telegram_not_configured").createForm(LoginFormsPages.LOGIN_TELEGRAM_SETUP.name()));
            return;
        }

        String mode = getConfigValue(context, TelegramAuthenticatorFactory.MODE, "OTP");
        if ("PUSH".equalsIgnoreCase(mode)) {
            doPush(context, telegramId);
        } else {
            doOtp(context, telegramId);
        }
    }

    private void doOtp(AuthenticationFlowContext context, String chatId) {
        // generate code and send
        String code = generateOtp(getConfigValue(context, TelegramAuthenticatorFactory.OTP_LENGTH, "6"));
        long timeout = Long.parseLong(getConfigValue(context, TelegramAuthenticatorFactory.OTP_TIMEOUT, "300"));
        context.getAuthenticationSession().setAuthNote(NOTE_OTP, code);
        context.getAuthenticationSession().setAuthNote(NOTE_OTP_EXPIRES, String.valueOf(System.currentTimeMillis() + timeout * 1000));
        context.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, "0");

        String botToken = getConfigValue(context, TelegramAuthenticatorFactory.BOT_TOKEN, "");
        try {
            new TelegramBotClient(context.getSession(), botToken).sendOtpMessage(chatId, code);
        } catch (TelegramBotClient.TelegramException e) {
            context.challenge(context.form().setError("telegram_send_failed").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }

        context.challenge(context.form().createForm(LoginFormsPages.LOGIN_TELEGRAM_OTP.name()));
    }

    private void doPush(AuthenticationFlowContext context, String chatId) {
        String token = java.util.UUID.randomUUID().toString();
        long timeout = Long.parseLong(getConfigValue(context, TelegramAuthenticatorFactory.PUSH_TIMEOUT, "60"));
        context.getAuthenticationSession().setAuthNote(NOTE_APPROVAL_TOKEN, token);
        context.getAuthenticationSession().setAuthNote(NOTE_APPROVED, "false");
        context.getAuthenticationSession().setAuthNote(NOTE_PUSH_EXPIRES, String.valueOf(System.currentTimeMillis() + timeout * 1000));

        String botToken = getConfigValue(context, TelegramAuthenticatorFactory.BOT_TOKEN, "");
        try {
            new TelegramBotClient(context.getSession(), botToken).sendPushNotification(chatId, token);
        } catch (TelegramBotClient.TelegramException e) {
            context.challenge(context.form().setError("telegram_send_failed").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }
        // track mapping: approvalToken -> auth session so the webhook can update the session
        org.keycloak.sessions.AuthenticationSessionModel authSess = context.getAuthenticationSession();
        String rootId = authSess.getParentSession() != null ? authSess.getParentSession().getId() : null;
        String tabId = authSess.getTabId();
        TelegramPushStatusStore.add(token, rootId, tabId, botToken, timeout * 1000);

        context.challenge(context.form().createForm(LoginFormsPages.LOGIN_TELEGRAM_PUSH.name()));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String mode = getConfigValue(context, TelegramAuthenticatorFactory.MODE, "OTP");
        if ("PUSH".equalsIgnoreCase(mode)) {
            String pushExpires = context.getAuthenticationSession().getAuthNote(NOTE_PUSH_EXPIRES);
            if (pushExpires != null && System.currentTimeMillis() > Long.parseLong(pushExpires)) {
                context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                        context.form().setError("push_expired").createErrorPage(Response.Status.BAD_REQUEST));
                return;
            }
            if (Boolean.parseBoolean(context.getAuthenticationSession().getAuthNote(NOTE_APPROVED))) {
                context.success();
            } else {
                context.failureChallenge(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR,
                        context.form().setError("not_approved").createForm(LoginFormsPages.LOGIN_TELEGRAM_PUSH.name()));
            }
        } else {
            String entered = context.getHttpRequest().getDecodedFormParameters().getFirst("otp");
            String stored = context.getAuthenticationSession().getAuthNote(NOTE_OTP);
            String expires = context.getAuthenticationSession().getAuthNote(NOTE_OTP_EXPIRES);
            String attemptsStr = context.getAuthenticationSession().getAuthNote(NOTE_ATTEMPTS);
            int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

            if (stored == null || expires == null || System.currentTimeMillis() > Long.parseLong(expires)) {
                context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                        context.form().setError("otp_expired").createForm(LoginFormsPages.LOGIN_TELEGRAM_OTP.name()));
                return;
            }
            if (entered != null && entered.equals(stored)) {
                context.success();
                return;
            }
            attempts++;
            context.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, String.valueOf(attempts));
            int max = Integer.parseInt(getConfigValue(context, TelegramAuthenticatorFactory.MAX_RETRIES, "3"));
            if (attempts >= max) {
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        context.form().setError("otp_fail").createErrorPage(Response.Status.BAD_REQUEST));
            } else {
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        context.form().setError("otp_invalid").createForm(LoginFormsPages.LOGIN_TELEGRAM_OTP.name()));
            }
        }
    }

    String getConfigValue(AuthenticationFlowContext context, String key, String defaultValue) {
        if (context == null || context.getAuthenticatorConfig() == null) return defaultValue;
        String val = context.getAuthenticatorConfig().getConfig().get(key);
        return val != null ? val : defaultValue;
    }

    String generateOtp(String lengthStr) {
        int length;
        try {
            length = Integer.parseInt(lengthStr);
        } catch (NumberFormatException nfe) {
            length = 6;
        }
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public boolean requiresUser() {
        return true; // user must be identified before second factor
    }

    @Override
    public boolean configuredFor(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) {
        // we'll check whether the user has telegram_user_id attribute configured
        String id = user.getFirstAttribute(TelegramAuthenticatorFactory.USER_TELEGRAM_ID);
        return id != null && !id.isEmpty();
    }

    @Override
    public void setRequiredActions(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) {
        // Maybe ask user to configure Telegram if not set
        if (!configuredFor(session, realm, user)) {
            user.addRequiredAction(TelegramSetupRequiredAction.PROVIDER_ID);
        }
    }

    @Override
    public void close() {
        // no resources to clean up
    }

}
