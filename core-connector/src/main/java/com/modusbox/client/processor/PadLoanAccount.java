package com.modusbox.client.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class PadLoanAccount implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String idType = (String) exchange.getIn().getHeader("idType");
        String loanAccount = "";
        if(idType.equalsIgnoreCase("ACCOUNT_ID")) {
            loanAccount = (String) exchange.getIn().getHeader("idValue");
            loanAccount = loanAccount.substring(3);
            while(loanAccount.length() < 9) {
                loanAccount = "0" + loanAccount;
            }
        }
        exchange.setProperty("loanAccount", loanAccount);
    }
}