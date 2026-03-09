<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout section>
    <#if section=="header">
        ${msg("doLogIn")}
    <#elseif section=="form">
        <p>${msg("telegramOtpInstructions")!"A code was sent to your Telegram. Enter it below."}</p>
        <form id="kc-telegram-otp-form" action="${url.loginAction}" method="post">
            <@field.input name="otp" label=msg("loginOtpOneTime") autocomplete="one-time-code" fieldName="telegramOtp" autofocus=true />
            <@buttons.loginButton />
        </form>
    </#if>
</@layout.registrationLayout>