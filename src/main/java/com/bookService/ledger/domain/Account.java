package com.bookService.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단순 계정 과목표(chart of accounts). M3 범위에서는 결제 확정 거래에 필요한
 * REVENUE(매출)/ITEM_BUYER(구매자 미수금성 계정) 두 계정만 사용한다
 * (원본 FinanceType.PAYMENT_ORDER 계정쌍과 동일 — {@link AccountNames} 참고).
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
