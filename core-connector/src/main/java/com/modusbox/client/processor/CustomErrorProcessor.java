package com.modusbox.client.processor;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.customexception.CloseWrittenOffAccountException;
import com.modusbox.client.enums.ErrorCode;
import com.modusbox.client.utils.DataFormatUtils;
import com.modusbox.log4j2.message.CustomJsonMessage;
import com.modusbox.log4j2.message.CustomJsonMessageImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.ws.rs.InternalServerErrorException;
import java.net.SocketTimeoutException;

@Component("customErrorProcessor")
public class CustomErrorProcessor implements Processor {

    CustomJsonMessage customJsonMessage = new CustomJsonMessageImpl();

    @Override
    public void process(Exchange exchange) throws Exception {

        String reasonText = "{ \"statusCode\": \"5000\"," +
                "\"message\": \"Unknown\" }";
        String statusCode = "5000";

        String CheckSteps = "";
        int httpResponseCode = 500;

        JSONObject errorResponse = null;

        String errorDescription = "Downstream API failed.";
        // The exception may be in 1 of 2 places
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }

        if (exception != null) {
            CheckSteps = "############### Exception is not null ############### \\r\\n";
            if (exception instanceof HttpOperationFailedException) {
                CheckSteps += "############### HttpOperationFailedException ############### \\r\\n";
                HttpOperationFailedException e = (HttpOperationFailedException) exception;
                try {
                    if (null != e.getResponseBody()) {
                        CheckSteps += "############### e.getResponseBody() is not null ############### \\r\\n";
                        if (DataFormatUtils.isJSONValid(e.getResponseBody())) {
                            CheckSteps += "############### valid Json ############### \\r\\n";
                        /* Below if block needs to be changed as per the error object structure specific to
                            CBS back end API that is being integrated in Core Connector. */
                            JSONObject respObject = new JSONObject(e.getResponseBody());
                            if (respObject.has("returnStatus")) {
                                CheckSteps += "############### Contains returnStatus ############### \\r\\n";
                                statusCode = String.valueOf(respObject.getInt("returnCode"));
                                errorDescription = respObject.getString("returnStatus");

                            }
                            // Disbursement Error Handling
                            else if (respObject.has("message") && respObject.has("transferState")) {
                                CheckSteps += "############### Contains message & transferState ############### \\r\\n";
                                statusCode = String.valueOf(respObject.getInt("statusCode"));
                                try {
                                    errorDescription = respObject.getJSONObject("transferState").getJSONObject("lastError").getJSONObject("mojaloopError").getJSONObject("errorInformation").getString("errorDescription");
                                } catch (JSONException ex) {
                                    errorDescription = "Unknown - no mojaloopError message present";
                                }
                            }
                            else if(e.getStatusCode() == 401 && respObject.has("error") && String.valueOf(respObject.getString("error")).equals("invalid_token"))
                            {
                                CheckSteps += "############### StatusCode 401 & Contains error ############### \\r\\n";
                                statusCode = String.valueOf(ErrorCode.DESTINATION_COMMUNICATION_ERROR.getStatusCode());
                                errorDescription = String.valueOf(respObject.getString("error_description"));
                            }
                            else
                            {
                                CheckSteps += "############### Valid json but can't define error ############### \\r\\n";
                                statusCode = String.valueOf(ErrorCode.DESTINATION_COMMUNICATION_ERROR.getStatusCode());
                                errorDescription = ErrorCode.DESTINATION_COMMUNICATION_ERROR.getDefaultMessage();
                            }

                        } else {
                            CheckSteps += "############### Invalid json format ############### \\r\\n";
                            statusCode = String.valueOf(ErrorCode.MALFORMED_SYNTAX.getStatusCode());
                            errorDescription = String.valueOf(e.getResponseBody());
                        }
                    }
                } finally {
                    CheckSteps += "############### Finally of HttpOperationFailedException ############### \\r\\n";
                    reasonText = "{ \"statusCode\": \"" + statusCode + "\"," +
                            "\"message\": \"" + errorDescription + "\"} ";
                }
            } else if (exception instanceof CloseWrittenOffAccountException) {
                CheckSteps += "############### CloseWrittenOffAccountException ############### \\r\\n";
                httpResponseCode = 200;
                reasonText = "{\"idType\": \"" + (String) exchange.getIn().getHeader("idType") +
                        "\",\"idValue\": \"" + (String) exchange.getIn().getHeader("idValue") +
                        "\",\"idSubValue\": \"" + (String) exchange.getIn().getHeader("idSubValue") +
                        "\",\"extensionList\": [{\"key\": \"errorMessage\",\"value\": \"" + exception.getMessage() +
                        "\"}]}";

            } else {
                try {
                    CheckSteps += "############### Else TRY ############### \\r\\n";
                    if (exception instanceof CCCustomException) {
                        CheckSteps += "############### CCCustomException ############### \\r\\n";
                        errorResponse = new JSONObject(exception.getMessage());
                    } else if (exception instanceof InternalServerErrorException || exception instanceof JSONException) {
                        CheckSteps += "############### InternalServerErrorException & JSONException ############### \\r\\n";
                        errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR));
                    } else if (exception instanceof ConnectTimeoutException || exception instanceof SocketTimeoutException || exception instanceof HttpHostConnectException) {
                        CheckSteps += "############### ConnectTimeoutException & SocketTimeoutException & HttpHostConnectException ############### \\r\\n";
                        errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.SERVER_TIMED_OUT));
                    } else {
                        CheckSteps += "############### Else TRY Else ############### \\r\\n";
                        errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.GENERIC_DOWNSTREAM_ERROR_PAYEE));
                    }
                } finally {
                    CheckSteps += "############### Else TRY Finally ############### \\r\\n";
                    httpResponseCode = errorResponse.getInt("errorCode");
                    errorResponse = errorResponse.getJSONObject("errorInformation");
                    statusCode = String.valueOf(errorResponse.getInt("statusCode"));
                    errorDescription = errorResponse.getString("description");
                    reasonText = "{ \"statusCode\": \"" + statusCode + "\"," +
                            "\"message\": \"" + errorDescription + "\"} ";
                }
            }
            customJsonMessage.logJsonMessage("error", String.valueOf(exchange.getIn().getHeader("X-CorrelationId")),
                    "Processing the exception at CustomErrorProcessor", null, null,
                    exception.getMessage());
            System.out.println("Http Response Code" + httpResponseCode);
            System.out.println(CheckSteps);
        }

        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, httpResponseCode);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody(reasonText);
    }
}