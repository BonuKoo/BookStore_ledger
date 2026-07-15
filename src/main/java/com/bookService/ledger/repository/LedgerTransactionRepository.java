package com.bookService.ledger.repository;

import com.bookService.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    /** 흔한 중복 전달을 예외 없이 빠르게 걸러내는 사전 확인 (재고 차감/정산 컨슈머와 같은 패턴). */
    boolean existsByOrderIdAndSellerIdAndProductId(String orderId, Long sellerId, String productId);
}
