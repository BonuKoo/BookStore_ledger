package com.bookService.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 장부 기록 완결 통지. core-spa(M4, 아직 미구현)가 구독해 is_ledger_updated 갱신 →
 * completeIfDone()에 반영할 예정이다. settlement-worker(M2)의 WalletCompletedMessage와
 * 같은 설계: 이미 처리된(중복 전달) 주문이어도 다시 발행한다 — "기록은 끝났는데 통지 발행
 * 직전에 크래시"난 경우를 재전달(at-least-once)로 복구하기 위함.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerCompletedMessage {
    private Map<String, Object> payload;
}
