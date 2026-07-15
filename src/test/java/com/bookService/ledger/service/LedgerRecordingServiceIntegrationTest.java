package com.bookService.ledger.service;

import com.bookService.ledger.dto.LedgerCompletedMessage;
import com.bookService.ledger.dto.PaymentEventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LedgerRecordingService의 오케스트레이션 계약 검증: payment.confirmed 페이로드의
 * 각 항목을 독립적으로 기록하고, 결과와 무관하게 항상 완결 통지 메시지를 반환하는지
 * 확인한다.
 */
@SpringBootTest
class LedgerRecordingServiceIntegrationTest {

    private static final String ORDER_PREFIX = "ledger-svc-test-";

    @Autowired private LedgerRecordingService ledgerRecordingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE le FROM ledger_entries le "
                        + "JOIN ledger_transactions lt ON le.transaction_id = lt.id "
                        + "WHERE lt.order_id LIKE ?", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM ledger_transactions WHERE order_id LIKE ?", ORDER_PREFIX + "%");
    }

    private int entryCount(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries le "
                        + "JOIN ledger_transactions lt ON le.transaction_id = lt.id "
                        + "WHERE lt.order_id = ?", Integer.class, orderId);
    }

    private PaymentEventMessage successMessage(String orderId, List<Map<String, Object>> items) {
        PaymentEventMessage message = new PaymentEventMessage();
        message.setMessageType("PAYMENT_CONFIRMATION_SUCCESS");
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("items", items);
        message.setPayload(payload);
        return message;
    }

    @Test
    @DisplayName("항목 2개(서로 다른 판매자) → 각각 독립된 분개(2쌍, 4행)로 기록되고 완결 통지에 orderId가 담긴다")
    void record_multipleItems_recordsEachIndependently() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();

        PaymentEventMessage message = successMessage(orderId, List.of(
                Map.of("sellerId", 101, "amount", 12000, "productId", "isbn-1", "quantity", 1),
                Map.of("sellerId", 102, "amount", 8000, "productId", "isbn-2", "quantity", 1)
        ));

        LedgerCompletedMessage completed = ledgerRecordingService.record(message);

        assertThat(entryCount(orderId)).isEqualTo(4); // 항목 2개 * (CREDIT+DEBIT)
        assertThat(completed.getPayload().get("orderId")).isEqualTo(orderId);
    }

    @Test
    @DisplayName("items가 비어 있어도 크래시 없이 완결 통지는 반환된다")
    void record_emptyItems_stillReturnsCompletedMessage() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        PaymentEventMessage message = successMessage(orderId, List.of());

        LedgerCompletedMessage completed = ledgerRecordingService.record(message);

        assertThat(completed.getPayload().get("orderId")).isEqualTo(orderId);
        assertThat(entryCount(orderId)).isZero();
    }

    @Test
    @DisplayName("중복 전달(재처리)이어도 분개는 중복 기록되지 않고 완결 통지는 다시 반환된다")
    void record_duplicateMessage_doesNotDuplicateEntries_butStillNotifies() {
        String orderId = ORDER_PREFIX + UUID.randomUUID();
        PaymentEventMessage message = successMessage(orderId, List.of(
                Map.of("sellerId", 201, "amount", 7000, "productId", "isbn-dup", "quantity", 1)
        ));

        ledgerRecordingService.record(message);
        LedgerCompletedMessage secondAttempt = ledgerRecordingService.record(message); // 재전달 시뮬레이션

        assertThat(entryCount(orderId)).isEqualTo(2); // 4가 아니라 2
        assertThat(secondAttempt.getPayload().get("orderId")).isEqualTo(orderId); // 그래도 통지는 됨
    }
}
