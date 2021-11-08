package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;

public class DataValidator {

    public void validateZeroAmount(String Amount) throws Exception {
        System.out.println("Amount in validateZeroAmount method:"+ Amount);
        if (Float.parseFloat(Amount) == 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR, "Invalid amount."));
        }
    }
    public void validateRounding(String Amount, String roundingValue) throws Exception{
        System.out.println("Amount in request body:"+ Amount);
        System.out.println("Constant Rounding Value:"+ roundingValue);

        if ((Float.parseFloat(Amount) % Short.parseShort(roundingValue)) != 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR, "Amount is invalid. Please enter the amount in multiple of 50.(e.g. 50, 100, 150, 200, etc)"));
        }
    }
}
