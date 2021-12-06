package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.customexception.CloseWrittenOffAccountException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.processor.PadLoanAccount;
import com.modusbox.client.processor.TokenStore;
import com.modusbox.client.validator.AccountNumberFormatValidator;
import com.modusbox.client.validator.GetPartyResponseValidator;
import com.modusbox.client.validator.IdSubValueChecker;
import com.modusbox.client.validator.PhoneNumberValidation;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.json.JSONException;

import java.net.SocketTimeoutException;
import java.util.UUID;

public class PartiesRouter extends RouteBuilder {

    private final PadLoanAccount padLoanAccount = new PadLoanAccount();
    private final PhoneNumberValidation phoneNumberValidation = new PhoneNumberValidation();
    private final GetPartyResponseValidator getPartyResponseValidator = new GetPartyResponseValidator();
    private final AccountNumberFormatValidator accountNumberFormatValidator = new AccountNumberFormatValidator();

    private final IdSubValueChecker idSubValueChecker = new IdSubValueChecker();

    private static final String TIMER_NAME = "histogram_get_parties_timer";

    public static final Counter reqCounter = Counter.build()
            .name("counter_get_parties_requests_total")
            .help("Total requests for GET /parties.")
            .register();

    private static final Histogram reqLatency = Histogram.build()
            .name("histogram_get_parties_request_latency")
            .help("Request latency in seconds for GET /parties.")
            .register();

    private final String PATH_NAME = "Finflux Advance Fetch Due API";
    private final String PATH = "/v1/paymentgateway/billerpayments/advance-fetch";

    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    public void configure() {

        exceptionHandlingConfigurer.configureExceptionHandling(this);
        //new ExceptionHandlingRouter(this);

        from("direct:getPartiesByIdTypeIdValue").routeId("com.modusbox.getPartiesByIdTypeIdValue").doTry()
                .process(exchange -> {
                    reqCounter.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME, reqLatency.startTimer()); // initiate Prometheus Histogram metric
                })

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received GET /parties/${header.idType}/${header.idValue}', " +
                        "'Tracking the request', " +
                        "'Call the Finflux API,  Track the response', " +
                        "'Input Payload: ${body}')") // default logger
                /*
                 * BEGIN processing
                 */
                .process(idSubValueChecker)

                .doCatch(CCCustomException.class)
                    .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
            }).end()
        ;

        from("direct:getPartiesByIdTypeIdValueIdSubValue").routeId("com.modusbox.getParties").doTry()
                .process(exchange -> {
                    reqCounter.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME, reqLatency.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received GET /parties/${header.idType}/${header.idValue}/${header.idSubValue}', " +
                        "'Tracking the request', " +
                        "'Call the " + PATH_NAME + ",  Track the response', " +
                        "'Input Payload: ${body}')") // default logger
                /*
                 * BEGIN processing
                 */
                .process(accountNumberFormatValidator)
                .process(padLoanAccount)
                .to("direct:getAuthHeader")
                .process(exchange -> exchange.setProperty("uuid", UUID.randomUUID().toString()))
                .removeHeaders("Camel*")
                .setHeader("Fineract-Platform-TenantId", constant("{{dfsp.tenant-id}}"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setBody(constant(null))

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/getPartiesRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                //.bean("getPartiesRequest")
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Calling the " + PATH_NAME + "', " +
                        "null, " +
                        "null, " +
                        "'Request to POST {{dfsp.host}}" + PATH + ", IN Payload: ${body} IN Headers: ${headers}')")

                .toD("{{dfsp.host}}" + PATH)
                //.marshal().json()
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Called " + PATH_NAME + "', " +
                        "null, " +
                        "null, " +
                        "'Response from POST {{dfsp.host}}" + PATH + ", OUT Payload: ${body}')")
                .process(getPartyResponseValidator)

                .process(phoneNumberValidation)
                .unmarshal().json()

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/getPartiesResponse.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                //.bean("getPartiesResponse")
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for GET /parties/${header.idType}/${header.idValue}/${header.idSubValue}', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: ${body}')") // default logger
                .removeHeaders("*", "X-*")
                .doCatch(HttpOperationFailedException.class)
                    .process(exchange -> {
                        Exception exception = exchange.getException();
                        if (exception == null) {
                            exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                        }
                        exchange.getIn().setBody(((HttpOperationFailedException) exception).getResponseBody().toString());
                })
                    .to("direct:getPartiesWithNewToken")
                .doCatch(CCCustomException.class,CloseWrittenOffAccountException.class, HttpOperationFailedException.class, JSONException.class, ConnectTimeoutException.class, SocketTimeoutException.class, HttpHostConnectException.class,Exception.class)
                    .to("direct:extractCustomErrors")

                .doFinally().process(exchange -> {
            ((Histogram.Timer) exchange.getProperty(TIMER_NAME)).observeDuration(); // stop Prometheus Histogram metric
        }).end()
        ;

        from("direct:getPartiesWithNewToken")
                .choice()
                    .when(body().contains("invalid_token"))
                        .log("getParties token exception : ${body}")
                        .bean(TokenStore.class, "setAccessToken('','0')")
                        .setBody(simple("${exchangeProperty.downstreamRequestBody}"))
                        .to("direct:getPartiesByIdTypeIdValueIdSubValue")
                .otherwise()
                    .log("getParties exception : ${body}")
                    .to("direct:extractCustomErrors")
                .end()
        ;
    }
}