package com.modusbox.client.router;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.customexception.CloseWrittenOffAccountException;
import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import com.modusbox.client.validator.BillsPaymentResponseValidator;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class TransfersRouter extends RouteBuilder {

    //private final String PATH_NAME_POST = "Finflux hardcoded postTransfer response";
    //private final String PATH = "/v1/paymentgateway/billerpayments/advance-fetch";

    private static final String TIMER_NAME_POST = "histogram_post_transfers_timer";
    private static final String TIMER_NAME_PUT = "histogram_put_transfers_timer";

    private final BillsPaymentResponseValidator billsPaymentResponseValidator = new BillsPaymentResponseValidator();

    public static final Counter reqCounterPost = Counter.build()
            .name("counter_post_transfers_requests_total")
            .help("Total requests for POST /transfers.")
            .register();

    public static final Counter reqCounterPut = Counter.build()
            .name("counter_put_transfers_requests_total")
            .help("Total requests for POST /transfers.")
            .register();

    private static final Histogram reqLatencyPost = Histogram.build()
            .name("histogram_post_transfers_request_latency")
            .help("Request latency in seconds for POST /transfers.")
            .register();

    private static final Histogram reqLatencyPut = Histogram.build()
            .name("histogram_put_transfers_request_latency")
            .help("Request latency in seconds for PUT /transfers.")
            .register();

    private final String PATH_NAME_PUT = "Finflux Bill Payment Direct Process API";
    private final String PATH = "/v1/paymentgateway/billerpayments/process-direct?paymentType=Mojaloop";
    private final String PATH2 = "/v1/paymentgateway/billerpayments/process-direct?paymentType=";

    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    public void configure() {

        exceptionHandlingConfigurer.configureExceptionHandling(this);
        //new ExceptionHandlingRouter(this);

        from("direct:postTransfers").routeId("com.modusbox.postTransfers").doTry()
                .process(exchange -> {
                    reqCounterPost.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_POST, reqLatencyPost.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received POST /transfers', " +
                        "'Tracking the request', " +
                        "'Call the " + PATH_NAME_PUT + ",  Track the response', " +
                        "'Input Payload: ${body}')") // default logger

                /*
                 * BEGIN processing
                 */
                .to("direct:getAuthHeader")
                .removeHeaders("Camel*")
                .setHeader("Fineract-Platform-TenantId", constant("{{dfsp.tenant-id}}"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))


                .marshal().json()

                //.process(padLoanAccount)

                .transform(datasonnet("resource:classpath:mappings/postTransfersRequest.ds"))

                .setProperty("fspId",simple("${body.content.get('fspId')}"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Calling the " + PATH_NAME_PUT + "', " +
                        "null, " +
                        "null, " +
                        "'Request to POST {{dfsp.host}}" + PATH2 + "${exchangeProperty.fspId}, IN Payload: ${body} IN Headers: ${headers}')")

                .toD("{{dfsp.host}}" + PATH2 + "${exchangeProperty.fspId}")

                .process(billsPaymentResponseValidator)

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Called " + PATH_NAME_PUT + "', " +
                        "null, " +
                        "null, " +
                        "'Response from POST {{dfsp.host}}" + PATH + ", OUT Payload: ${body}')")

                .transform(datasonnet("resource:classpath:mappings/postTransfersResponse.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for POST /transfers', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: empty')") // default logger
                .removeHeaders("*", "X-*")

                .doCatch(CCCustomException.class)
                .to("direct:extractCustomErrors")
                .doFinally().process(exchange -> {
            ((Histogram.Timer) exchange.getProperty(TIMER_NAME_POST)).observeDuration(); // stop Prometheus Histogram metric
        }).end()
        ;

        from("direct:putTransfersByTransferId").routeId("com.modusbox.putTransfers").doTry()
                .process(exchange -> {
                    reqCounterPut.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_PUT, reqLatencyPut.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received PUT /transfers', " +
                        "'Tracking the request', " +
                        "'Call the PUT /Transfer,  Track the response', " +
                        "'Input Payload: ${body}')") // default logger

                /*
                 * BEGIN processing
                 */
//                .to("direct:getAuthHeader")
//                .removeHeaders("Camel*")
//                .setHeader("Fineract-Platform-TenantId", constant("{{dfsp.tenant-id}}"))
//                .setHeader("Content-Type", constant("application/json"))
//                .setHeader(Exchange.HTTP_METHOD, constant("POST"))


//                .marshal().json()

                //.process(padLoanAccount)

//                .transform(datasonnet("resource:classpath:mappings/putTransfersRequest.ds"))

//                .setProperty("fspId",simple("${body.content.get('fspId')}"))
//                .setBody(simple("${body.content}"))
//                .marshal().json()
//                .to("bean:customJsonMessage?method=logJsonMessage(" +
//                        "'info', " +
//                        "${header.X-CorrelationId}, " +
//                        "'Calling the " + PATH_NAME_PUT + "', " +
//                        "null, " +
//                        "null, " +
//                        "'Request to POST {{dfsp.host}}" + PATH2 + "${exchangeProperty.fspId}, IN Payload: ${body} IN Headers: ${headers}')")

//                .toD("{{dfsp.host}}" + PATH2 + "${exchangeProperty.fspId}")

//                .process(billsPaymentResponseValidator)

//                .to("bean:customJsonMessage?method=logJsonMessage(" +
//                        "'info', " +
//                        "${header.X-CorrelationId}, " +
//                        "'Called " + PATH_NAME_PUT + "', " +
//                        "null, " +
//                        "null, " +
//                        "'Response from POST {{dfsp.host}}" + PATH + ", OUT Payload: ${body}')")

//                .transform(datasonnet("resource:classpath:mappings/putTransfersResponse.ds"))
//                .setBody(simple("${body.content}"))
//                .marshal().json()
                /*
                 * END processing
                 */
//                .to("bean:customJsonMessage?method=logJsonMessage(" +
//                        "'info', " +
//                        "${header.X-CorrelationId}, " +
//                        "'Response for PUT /transfers', " +
//                        "'Tracking the response', " +
//                        "null, " +
//                        "'Output Payload: empty')") // default logger
                .removeHeaders("*", "X-*")

                .doCatch(CCCustomException.class)
                    .to("direct:extractCustomErrors")

                .doFinally().process(exchange -> {
            ((Histogram.Timer) exchange.getProperty(TIMER_NAME_PUT)).observeDuration(); // stop Prometheus Histogram metric
        }).end()
        ;

    }
}
