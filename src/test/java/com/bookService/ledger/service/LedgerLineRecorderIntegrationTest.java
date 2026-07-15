package com.bookService.ledger.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장부 기록의 멱등성/동시성/복식부기 불변식 계약 검증. 실제 MySQL에서 스레드별
 * 독립 트랜잭션으로 실행해 "UNIQUE(order_id, seller_id, product_id) 멱등 가드"가
 * 실제 경쟁 상황에서 지켜지는지 본다 (settlement-worker 테스트와 같은 방법론).
 */
@SpringBootTest
class LedgerLineRecorderIntegrationTest {

    private static final String ORDER_PREFIX = "ledger-test-order-";

    @Autowired private LedgerLineRecorder ledgerLineRecorder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE le FROM ledger_entries le "
                        + "JOIN ledger_transactions lt ON le.transaction_id = lt.id "
                        + "WHERE lt.order_id LIKE ?", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM ledger_transactions WHERE order_id LIKE ?", ORDER_PREFIX + "%");
    }

    private List<Map<String, Object>> entriesOf(String orderId, Long sellerId, String productId) {
        return jdbcTemplate.queryForList(
                "SELECT le.amount, le.type, a.name AS account_name "
                        + "FROM ledger_entries le "
                        + "JOIN ledger_transactions lt ON le.transaction_id = lt.id "
                        + "JOIN accounts a ON le.account_id = a.id "
                        + "WHERE lt.order_id = ? AND lt.seller_id = ? AND lt.product_id = ?",
                orderId, sellerId, productId);
    }

    private int transactionCount(String orderId, Long sellerId, String productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_transactions WHERE order_id = ? AND seller_id = ? AND product_id = ?",
                Integer.class, orderId, sellerId, productId);
    }

    @Test
    @DisplayName("정상 기록: CREDIT은 REVENUE로, DEBIT은 ITEM_BUYER로 같은 금액이 쌍으로 기록된다")
    void record_success_createsBalancedDoubleEntry() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        long sellerId = 1L;
        String productId = "isbn-001";

        ledgerLineRecorder.record(orderId, sellerId, productId, new BigDecimal("12000"));

        List<Map<String, Object>> entries = entriesOf(orderId, sellerId, productId);
        assertThat(entries).hasSize(2);

        Map<String, Object> credit = entries.stream()
                .filter(e -> "CREDIT".equals(e.get("type"))).findFirst().orElseThrow();
        Map<String, Object> debit = entries.stream()
                .filter(e -> "DEBIT".equals(e.get("type"))).findFirst().orElseThrow();

        assertThat(credit.get("account_name")).isEqualTo("REVENUE");
        assertThat(debit.get("account_name")).isEqualTo("ITEM_BUYER");
        assertThat(((BigDecimal) credit.get("amount"))).isEqualByComparingTo("12000");
        assertThat(((BigDecimal) debit.get("amount"))).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("멱등성: 같은 (주문,판매자,상품) 줄을 두 번 처리해도 한 번만 기록된다")
    void record_duplicateLine_recordsOnlyOnce() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        long sellerId = 2L;
        String productId = "isbn-002";

        ledgerLineRecorder.record(orderId, sellerId, productId, new BigDecimal("5000"));
        ledgerLineRecorder.record(orderId, sellerId, productId, new BigDecimal("5000")); // 재전달 시뮬레이션

        assertThat(transactionCount(orderId, sellerId, productId)).isEqualTo(1);
        assertThat(entriesOf(orderId, sellerId, productId)).hasSize(2); // 4가 아니라 2
    }

    @Test
    @DisplayName("같은 판매자의 서로 다른 상품 2건 — 합산하지 않고 각각 독립된 분개로 기록된다")
    void record_sameSellerDifferentProducts_recordsIndependently() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        long sellerId = 3L;

        ledgerLineRecorder.record(orderId, sellerId, "isbn-a", new BigDecimal("3000"));
        ledgerLineRecorder.record(orderId, sellerId, "isbn-b", new BigDecimal("4000"));

        assertThat(entriesOf(orderId, sellerId, "isbn-a")).hasSize(2);
        assertThat(entriesOf(orderId, sellerId, "isbn-b")).hasSize(2);
    }

    @Test
    @DisplayName("동시성/멱등: 같은 (주문,판매자,상품) 줄이 20번 동시 전달돼도 정확히 1회만 기록된다")
    void record_concurrentDuplicateDelivery_recordsExactlyOnce() throws InterruptedException {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        long sellerId = 4L;
        String productId = "isbn-concurrent";
        int threads = 20;

        runConcurrently(threads, i ->
                ledgerLineRecorder.record(orderId, sellerId, productId, new BigDecimal("1000")));

        assertThat(transactionCount(orderId, sellerId, productId)).isEqualTo(1);
        assertThat(entriesOf(orderId, sellerId, productId)).hasSize(2);
    }

    private void runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(idx);
                } catch (Exception ignored) {
                    // 성공/실패 집계는 task 안에서 수행
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }
}
