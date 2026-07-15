package com.bookService.ledger.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * core-spa(프로듀서)가 발행하는 PaymentEventMessage 봉투와 동일한 형태를
 * 컨슈머 쪽에서 독립적으로 정의한 것이다. 두 프로젝트가 별도 배포 단위(PC1/PC3)이므로
 * 클래스를 공유하지 않고 JSON 계약(스키마)만 맞춘다.
 *
 * ledger.recording.queue는 payment.confirmed 라우팅 키에만 바인딩되므로
 * messageType은 항상 PAYMENT_CONFIRMATION_SUCCESS다.
 */
@Data
@NoArgsConstructor
public class PaymentEventMessage {

    private String messageType;
    private Map<String, Object> payload;
    private Map<String, Object> metadata;
}
