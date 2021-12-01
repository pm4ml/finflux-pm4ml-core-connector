package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.TokenStore;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class AuthRouter extends RouteBuilder {

    private final String PATH_NAME = "Finflux Fetch Access Token API";
    private final String PATH = "/oauth/token";

    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    public void configure() {

        exceptionHandlingConfigurer.configureExceptionHandling(this);
        //new ExceptionHandlingRouter(this);

        from("direct:getAuthHeader")
                .setProperty("downstreamRequestBody", simple("${body}"))
                .setProperty("AccessToken", method(TokenStore.class, "getAccessToken()"))
                .choice()
                    .when(method(TokenStore.class, "getAccessToken()").isEqualTo(""))
                        .setProperty("username", simple("{{dfsp.username}}"))
                        .setProperty("password", simple("{{dfsp.password}}"))
                        .setProperty("scope", simple("{{dfsp.scope}}"))
                        .setProperty("clientId", simple("{{dfsp.client-id}}"))
                        .setProperty("grantType", simple("{{dfsp.grant-type}}"))
                        .setProperty("isPasswordEncrypted", simple("{{dfsp.is-password-encrypted}}"))
                        .removeHeaders("Camel*")
                        .setHeader("Fineract-Platform-TenantId", simple("{{dfsp.tenant-id}}"))
                        .setHeader("Content-Type", constant("application/json"))
                        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                        .setBody(constant(""))
                        .marshal().json()
                        .transform(datasonnet("resource:classpath:mappings/postAuthTokenRequest.ds"))
                        .setBody(simple("${body.content}"))
                        .marshal().json()
                        //.bean("postAuthTokenRequest")
                        .to("bean:customJsonMessage?method=logJsonMessage(" +
                                "'info', " +
                                "${header.X-CorrelationId}, " +
                                "'Calling the access token " + PATH_NAME + "', " +
                                "null, " +
                                "null, " +
                                "'Request to POST {{dfsp.host}}" + PATH + ", IN Payload: ${body}')")
                        .toD("{{dfsp.host}}" + PATH)
                        .unmarshal().json()

                        .setProperty("RefreshToken", simple("${body['refresh_token']}"))
                        .setProperty("RefreshTokenExpiration", simple("${body['expires_in']}"))
                        .bean(TokenStore.class, "setRefreshToken(${exchangeProperty.RefreshToken}, ${exchangeProperty.RefreshTokenExpiration})")
                        .marshal().json()
                        .transform(datasonnet("resource:classpath:mappings/postAuthRefreshTokenRequest.ds"))
                        .setBody(simple("${body.content}"))
                        .marshal().json()
                        .to("bean:customJsonMessage?method=logJsonMessage(" +
                                "'info', " +
                                "${header.X-CorrelationId}, " +
                                "'Calling the refresh token " + PATH_NAME + "', " +
                                "null, " +
                                "null, " +
                                "'Request to POST {{dfsp.host}}" + PATH + ", IN Payload: ${body}')")
                        .toD("{{dfsp.host}}" + PATH)
                        .unmarshal().json()

                        .setProperty("AccessToken", simple("${body['access_token']}"))
                        .setProperty("AccessTokenExpiration", simple("${body['expires_in']}"))
                        .bean(TokenStore.class, "setAccessToken(${exchangeProperty.AccessToken}, ${exchangeProperty.AccessTokenExpiration})")

                        .to("bean:customJsonMessage?method=logJsonMessage(" +
                                "'info', " +
                                "${header.X-CorrelationId}, " +
                                "'Called refresh token " + PATH_NAME + "', " +
                                "null, " +
                                "null, " +
                                "'Response from POST {{dfsp.host}}" + PATH + ", OUT Payload: ${body}')")
                        //.unmarshal().json(JsonLibrary.Gson)
                .end()

                .setHeader("Authorization", simple("Bearer ${exchangeProperty.AccessToken}"))
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Auth Token caught from " + PATH_NAME + "', " +
                        "null, " +
                        "null, " +
                        "'Authorization: ${header.Authorization}')")
                .removeHeaders("CamelHttp*")
                .removeHeaders("Fineract-Platform-TenantId")
                .setBody(simple("${exchangeProperty.downstreamRequestBody}"))
        ;
    }
}
