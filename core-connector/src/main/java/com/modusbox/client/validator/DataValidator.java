package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;

public class DataValidator {

    public void validateZeroAmount(String Amount) throws Exception {
        System.out.println("Amount in validateZeroAmount method:"+ Amount);
        if (Float.parseFloat(Amount) == 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR, "Transfer amount cannot be zero value."));
        }
    }
    public void validateRounding(String Amount, String roundingValue) throws Exception{
        System.out.println("Amount in request body:"+ Amount);
        System.out.println("Constant Rounding Value:"+ roundingValue);

        if ((Float.parseFloat(Amount) % Short.parseShort(roundingValue)) != 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.ROUNDING_VALUE_ERROR, ErrorCode.ROUNDING_VALUE_ERROR.getDefaultMessage().replace("XXXX",roundingValue)));
        }
    }
}
