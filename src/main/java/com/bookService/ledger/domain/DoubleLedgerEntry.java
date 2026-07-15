package com.bookService.ledger.domain;

import java.math.BigDecimal;

/**
 * 복식부기의 핵심 불변식을 표현하는 도메인 객체: 차변(debit)과 대변(credit) 금액은
 * 항상 같아야 한다. 원본(paymentModel ledger)의 설계를 그대로 이식했다 — 생성자에서
 * 검증해 "차변≠대변인 분개"가 애초에 존재할 수 없게 한다.
 *
 * 지금은 신용/차변 금액이 항상 같은 소스(하나의 결제 항목 금액)에서 나오므로 이 검증이
 * 항상 통과하지만, 나중에 신용·차변 금액이 서로 다른 경로로 계산되도록 코드가 바뀌어도
 * 이 불변식이 여전히 지켜지도록 방어선 역할을 한다.
 */
public final class DoubleLedgerEntry {

    private final BigDecimal creditAmount;
    private final BigDecimal debitAmount;

    public DoubleLedgerEntry(BigDecimal creditAmount, BigDecimal debitAmount) {
        if (creditAmount.compareTo(debitAmount) != 0) {
            throw new IllegalArgumentException(
                    "복식부기 불변식 위반: 대변(" + creditAmount + ")과 차변(" + debitAmount + ")의 금액이 다릅니다.");
        }
        this.creditAmount = creditAmount;
        this.debitAmount = debitAmount;
    }

    public BigDecimal amount() {
        return creditAmount;
    }
}
