package com.bookService.ledger.service;

import com.bookService.ledger.domain.Account;
import com.bookService.ledger.domain.AccountNames;
import com.bookService.ledger.domain.DoubleLedgerEntry;
import com.bookService.ledger.domain.LedgerEntry;
import com.bookService.ledger.domain.LedgerEntryType;
import com.bookService.ledger.domain.LedgerTransaction;
import com.bookService.ledger.repository.AccountRepository;
import com.bookService.ledger.repository.LedgerEntryRepository;
import com.bookService.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 결제 주문 한 줄(판매자-상품 조합)의 복식부기 분개를 독립 트랜잭션으로 기록한다.
 * 별도 빈으로 분리한 이유는 settlement-worker의 SellerWalletCreditor와 같다: 줄 하나의
 * 처리가 실패해도 다른 줄의 이미 커밋된 분개를 되돌릴 필요가 없다(각 줄은 독립적인
 * 회계 사실이다) — 실패한 줄만 다음 재전달 때 재시도된다.
 *
 * 멱등성: ledger_transactions(order_id, seller_id, product_id) UNIQUE에 saveAndFlush를
 * 먼저 실행한다. 원본은 여기서 저장 로직 자체가 없어 컴파일이 안 됐던 지점 — 여기서 완성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerLineRecorder {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void record(String orderId, Long sellerId, String productId, BigDecimal amount) {
        if (ledgerTransactionRepository.existsByOrderIdAndSellerIdAndProductId(orderId, sellerId, productId)) {
            log.info("이미 기록된 장부 줄 — 중복 전달 skip: orderId={}, sellerId={}, productId={}",
                    orderId, sellerId, productId);
            return;
        }

        LedgerTransaction transaction;
        try {
            transaction = ledgerTransactionRepository.saveAndFlush(LedgerTransaction.builder()
                    .orderId(orderId)
                    .sellerId(sellerId)
                    .productId(productId)
                    .description("결제 확정 정산: orderId=" + orderId + ", sellerId=" + sellerId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("이미 기록된 장부 줄 — 동시 중복 전달 skip: orderId={}, sellerId={}, productId={}",
                    orderId, sellerId, productId);
            return;
        }

        // 대변(매출)과 차변(구매자) 금액은 같은 소스에서 나오므로 항상 같지만,
        // 복식부기 불변식을 도메인 객체로 명시해 방어선을 남긴다.
        DoubleLedgerEntry doubleLedgerEntry = new DoubleLedgerEntry(amount, amount);

        Account revenue = loadAccount(AccountNames.REVENUE);
        Account itemBuyer = loadAccount(AccountNames.ITEM_BUYER);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .account(revenue)
                .amount(doubleLedgerEntry.amount())
                .type(LedgerEntryType.CREDIT)
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .account(itemBuyer)
                .amount(doubleLedgerEntry.amount())
                .type(LedgerEntryType.DEBIT)
                .build());

        log.info("장부 기록 완료: orderId={}, sellerId={}, productId={}, amount={}",
                orderId, sellerId, productId, amount);
    }

    private Account loadAccount(String name) {
        return accountRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "계정 과목이 시드되어 있지 않습니다: " + name + " (docs/ddl/ledger.sql 적용 여부 확인)"));
    }
}
