package com.bookService.ledger.domain;

/**
 * 결제 확정 거래(원본의 FinanceType.PAYMENT_ORDER)에 쓰이는 계정 이름.
 * 매출은 REVENUE로 들어오고(CREDIT), 그 자금은 구매자 쪽 계정에서 나간다(DEBIT).
 * accounts 테이블에 이 이름으로 사전 시드되어 있어야 한다 (docs/ddl/ledger.sql 참고).
 */
public final class AccountNames {

    public static final String REVENUE = "REVENUE";
    public static final String ITEM_BUYER = "ITEM_BUYER";

    private AccountNames() {
    }
}
