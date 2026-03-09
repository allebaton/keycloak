package org.keycloak.authentication.authenticators.browser.telegram;

import org.junit.Assert;
import org.junit.Test;

public class TelegramAuthenticatorUnitTest {

    private final TelegramAuthenticator auth = new TelegramAuthenticator();

    @Test
    public void testGenerateOtpLength() {
        String otp4 = auth.generateOtp("4");
        Assert.assertEquals(4, otp4.length());
        Assert.assertTrue(otp4.matches("\\d{4}"));

        String otpDefault = auth.generateOtp("not-a-number");
        Assert.assertEquals(6, otpDefault.length());
        Assert.assertTrue(otpDefault.matches("\\d{6}"));
    }

    @Test
    public void testConfigValueFallback() {
        // null context should return the default value
        String val = auth.getConfigValue(null, "anyKey", "defaultVal");
        Assert.assertEquals("defaultVal", val);
    }
}
