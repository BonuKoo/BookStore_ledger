package com.bookService.ledger.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 복식부기 거래 한 건 = 결제 주문의 판매자-상품 한 줄(payment_orders 한 행에 대응).
 * 원본(paymentModel ledger)은 nullable + 비-UNIQUE idempotency_key였으나(사실상 멱등
 * 보장이 안 되는 설계), 여기서는 UNIQUE(order_id, seller_id, product_id)로 "이 주문의
 * 이 판매자-상품 줄은 한 번만 기록된다"를 DB가 보장한다.
 *
 * 원본은 reference_id(항목의 원본 DB 행 id)도 저장했으나, 컨슈머가 받는 JSON 메시지에는
 * 그 id가 없어(sellerId/productId/amount/quantity만 있음) 제외했다 — (order_id, seller_id,
 * product_id) 조합이 이미 각 줄을 유일하게 식별한다.
 */
@Entity
@Table(
        name = "ledger_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ledger_tx_order_seller_product",
                columnNames = {"order_id", "seller_id", "product_id"})
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 255)
    private String orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false, length = 255)
    private String productId;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
