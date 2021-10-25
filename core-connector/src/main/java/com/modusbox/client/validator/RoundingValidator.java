package com.modusbox.client.validator;

import com.modusbox.client.model.QuoteRequest;
import com.modusbox.client.common.Constants;
import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class RoundingValidator implements Processor {

    public void process(Exchange exchange) throws Exception {

        QuoteRequest quoteRequest = exchange.getIn().getBody(QuoteRequest.class);
        String amount = quoteRequest.getAmount();
        exchange.setProperty("amount", amount);

        float famount =  Float.parseFloat((String) exchange.getProperty("amount"));
        System.out.println("Amount in request body:"+famount);
        System.out.println("Constant Rounding Value:"+Constants.ROUNDING_VALUE);

        if ((famount % Constants.ROUNDING_VALUE) != 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_ID_NOT_FOUND, "Rounding Error"));
        }
    }
}
