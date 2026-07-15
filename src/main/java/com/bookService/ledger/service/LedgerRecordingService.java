package com.bookService.ledger.service;

import com.bookService.ledger.dto.LedgerCompletedMessage;
import com.bookService.ledger.dto.PaymentEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * payment.confirmed 이벤트의 각 항목(판매자-상품 줄)을 독립적으로 장부에 기록한다.
 *
 * 원본(paymentModel ledger)의 Ledger.createDoubleLedgerEntry는 항목별로 분개를
 * 만들었다(판매자 단위로 합산하지 않음) — settlement-worker(M2)가 판매자별 잔액을
 * 합산하는 것과 달리, 장부는 항목 단위 감사 추적성이 중요하므로 여기서도 합산하지
 * 않고 항목마다 독립 처리한다({@link LedgerLineRecorder}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerRecordingService {

    private final LedgerLineRecorder ledgerLineRecorder;

    public LedgerCompletedMessage record(PaymentEventMessage message) {
        String orderId = String.valueOf(message.getPayload().get("orderId"));

        for (Map<String, Object> item : items(message.getPayload())) {
            Long sellerId = ((Number) item.get("sellerId")).longValue();
            String productId = String.valueOf(item.get("productId"));
            BigDecimal amount = toBigDecimal(item.get("amount"));

            try {
                ledgerLineRecorder.record(orderId, sellerId, productId, amount);
            } catch (Exception e) {
                log.error("장부 기록 실패 — 다음 재전달에서 재시도됨. orderId={}, sellerId={}, productId={}",
                        orderId, sellerId, productId, e);
            }
        }

        // 원본 설계 유지: 이미 전부 처리된(중복 전달) 주문이어도 완결 통지는 다시 발행한다.
        return new LedgerCompletedMessage(Map.of("orderId", orderId));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> payload) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        return items == null ? List.of() : items;
    }

    private BigDecimal toBigDecimal(Object amount) {
        if (amount instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(amount));
    }
}
