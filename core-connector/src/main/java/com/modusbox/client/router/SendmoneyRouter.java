package com.modusbox.client.router;

import com.modusbox.client.exception.RouteExceptionHandlingConfigurer;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.model.dataformat.JsonLibrary;

public class SendmoneyRouter extends RouteBuilder {

    private static final String TIMER_NAME_POST = "histogram_post_sendmoney_timer";
    private static final String TIMER_NAME_PUT = "histogram_put_sendmoney_by_id_timer";

    private final RouteExceptionHandlingConfigurer exceptionHandlingConfigurer = new RouteExceptionHandlingConfigurer();

    public static final Counter reqCounterPost = Counter.build()
            .name("counter_post_sendmoney_requests_total")
            .help("Total requests for POST /sendmoney.")
            .register();

    public static final Counter reqCounterPut = Counter.build()
            .name("counter_put_sendmoney_by_id_requests_total")
            .help("Total requests for PUT /sendmoney.")
            .register();

    private static final Histogram reqLatencyPost = Histogram.build()
            .name("histogram_post_sendmoney_request_latency")
            .help("Request latency in seconds for POST /sendmoney.")
            .register();

    private static final Histogram reqLatencyPut = Histogram.build()
            .name("histogram_put_sendmoney_by_id_request_latency")
            .help("Request latency in seconds for PUT /sendmoney.")
            .register();

    public void configure() {

        exceptionHandlingConfigurer.configureExceptionHandling(this);
        //new ExceptionHandlingRouter(this);

        from("direct:postSendMoney").routeId("com.modusbox.postSendMoney").doTry()
                .process(exchange -> {
                    reqCounterPost.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_POST, reqLatencyPost.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received POST /sendmoney', " +
                        "'Tracking the request', " +
                        "'Call the Mojaloop Connector Outbound API, Track the response', " +
                        "'Input Payload: ${body}')")
                /*
                 * BEGIN processing
                 */
                .setProperty("origPayload", simple("${body}"))
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("MFIName", constant("{{dfsp.name}}"))

                // Prune empty items from the request
                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/postSendMoneyRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Calling the Mojaloop Connector Outbound API postSendMoney POST {{ml-conn.outbound.host}}'," +
                        "'Tracking the request', " +
                        "'Track the response', " +
                        "'Request to POST {{ml-conn.outbound.host}}/transfers, IN Payload: ${body} IN Headers: ${headers}')")

                .toD("{{ml-conn.outbound.host}}/transfers?bridgeEndpoint=true")
                .unmarshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Called the Mojaloop Connector Outbound API postSendMoney', " +
                        "'Tracking the response', " +
                        "'Verify the response', " +
                        "'Response from POST {{ml-conn.outbound.host}}/transfers, OUT Payload: ${body}')")

                .setProperty("postSendMoneyInitial", body())
                // Send request to accept the party instead of hard coding AUTO_ACCEPT_PARTY: true
                .to("direct:putTransfersAcceptParty")
                .to("direct:putTransfersAcceptQuote")
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for POST /sendmoney', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: ${body}')")
                // .removeHeaders("*", "X-*")

                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_POST)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;

        from("direct:putTransfersAcceptParty").routeId("com.modusbox.putTransfersAcceptParty")
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setHeader("Content-Type", constant("application/json"))

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/putTransfersAcceptPartyRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling outbound API, putTransfersAcceptParty " +
                        "PUT {{ml-conn.outbound.host}}/transfers/${exchangeProperty.postSendMoneyInitial?.get('transferId')}', " +
                        "'Tracking the request',"+
                        "'Track the response'," +
                        "'Input Payload: ${body}')")

                // Instead of having to do a DataSonnet transformation
                .toD("{{ml-conn.outbound.host}}/transfers/${exchangeProperty.postSendMoneyInitial?.get('transferId')}?bridgeEndpoint=true")
                .unmarshal().json()
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Response from outbound API, putTransfersAcceptParty: ${body}', " +
                        "'Tracking the response', 'Verify the response',"+
                        "'Output Payload: ${body}')")
        ;

        from("direct:putTransfersAcceptQuote").routeId("com.modusbox.putTransfersAcceptQuote")
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setHeader("Content-Type", constant("application/json"))

                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/putTransfersAcceptQuoteRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Calling outbound API, putTransfersAcceptQuote " +
                        "PUT {{ml-conn.outbound.host}}/transfers/${exchangeProperty.postSendMoneyInitial?.get('transferId')}', " +
                        "'Tracking the request'," +
                        "'Track the response'," +
                        "'Input Payload: ${body}')")

                // Instead of having to do a DataSonnet transformation
                .toD("{{ml-conn.outbound.host}}/transfers/${exchangeProperty.postSendMoneyInitial?.get('transferId')}?bridgeEndpoint=true")
                .unmarshal().json()
                .to("bean:customJsonMessage?method=logJsonMessage('info', ${header.X-CorrelationId}, " +
                        "'Response from outbound API, putTransfersAcceptQuote: ${body}', " +
                        "'Tracking the response', 'Verify the response', " +
                        "'Output Payload: ${body}')")
        ;

        from("direct:putSendMoneyByTransferId") .routeId("com.modusbox.putSendMoneyByTransferId").doTry()
                .process(exchange -> {
                    reqCounterPut.inc(1); // increment Prometheus Counter metric
                    exchange.setProperty(TIMER_NAME_PUT, reqLatencyPut.startTimer()); // initiate Prometheus Histogram metric
                })
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Request received PUT /sendmoney/${header.transferId}', " +
                        "'Tracking the request'," +
                        "'Call the Mojaloop Connector Outbound API, Track the response', " +
                        "'Input Payload: ${body}')") // default logger
                /*
                 * BEGIN processing
                 */
                .setProperty("origPayload", simple("${body}"))
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setHeader("Content-Type", constant("application/json"))

                // Will convert to JSON and only take the accept quote section
                .marshal().json()
                .transform(datasonnet("resource:classpath:mappings/putTransfersDisburseRequest.ds"))
                .setBody(simple("${body.content}"))
                .marshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Calling the Mojaloop Connector Outbound API putTransfersById " +
                        "POST {{ml-conn.outbound.host}}', " +
                        "'Tracking the request',"+
                        "'Track the response'," +
                        "'Request to PUT {{ml-conn.outbound.host}}/transfers, IN Payload: ${body} IN Headers: ${headers}')")

                .toD("{{ml-conn.outbound.host}}/transfers/${header.transferId}?bridgeEndpoint=true")
                .unmarshal().json()

                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Called the Mojaloop Connector Outbound API putTransfersById', " +
                        "null, " +
                        "null, " +
                        "'Response from POST {{ml-conn.outbound.host}}/transfers, OUT Payload: ${body}')")
                /*
                 * END processing
                 */
                .to("bean:customJsonMessage?method=logJsonMessage(" +
                        "'info', " +
                        "${header.X-CorrelationId}, " +
                        "'Response for PUT /sendmoney', " +
                        "'Tracking the response', " +
                        "null, " +
                        "'Output Payload: ${body}')")
//                .removeHeaders("*", "X-*")
//                .setBody(simple(""))
                .doFinally().process(exchange -> {
                    ((Histogram.Timer) exchange.getProperty(TIMER_NAME_PUT)).observeDuration(); // stop Prometheus Histogram metric
                }).end()
        ;
    }
}
