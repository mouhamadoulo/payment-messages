package com.bank.paymentmessages.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debtor {

    private String accountNumber;

    private String name;

    private String bankCode;
}