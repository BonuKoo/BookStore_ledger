# ledger-worker

> core-spa가 발행하는 결제 확정 이벤트를 받아, 주문의 각 항목(판매자-상품 줄)을
> 복식부기 장부로 기록하고, 다 기록하면 완결 통지를 되쏘는 워커.
> 분산 결제 파이프라인([core-spa](https://github.com/BonuKoo/BookStore))의 구성요소.

---

## 기술 스택

| 구분 | 기술 |
| :--- | :--- |
| Language / Framework | Java 21, Spring Boot 3.4.8 |
| Messaging | RabbitMQ (Spring AMQP), Manual ACK, DLX/DLQ, Publisher Confirms |
| Persistence | Spring Data JPA, MySQL 8 |
| Build | Gradle |

---

## 흐름

```mermaid
flowchart LR
    EX{{"payment.exchange (topic)<br/>PC2 브로커"}}
    subgraph LW ["ledger-worker (PC3)"]
        L[LedgerListener] --> S[LedgerRecordingService<br/>항목별 루프]
        S --> R[LedgerLineRecorder<br/>줄 단위 @Transactional]
        R --> DB[(MySQL core2_spa<br/>ledger_transactions / ledger_entries)]
        S --> P[LedgerCompletedPublisher]
    end
    EX -->|"payment.confirmed"| L
    P -->|"settlement.ledger.completed"| EX
    EX -.->|완결 통지| CMP[core-spa<br/>PaymentCompletionListener]
```

`payment.confirmed` 하나가 나가면 알림, 재고차감, 정산, 장부 워커가 각자 자기 큐로 받아 간다(topic 팬아웃).
이 워커는 장부 담당이다. 정산은 판매자별로 금액을 묶지만, 장부는 안 묶는다. 항목 한 줄이 곧 분개 한 건이고,
감사할 땐 그 줄을 그대로 되짚어야 한다.

---

## 메시지 계약

| 구분 | 값 |
| :--- | :--- |
| 구독 큐 | `ledger.recording.queue` (`payment.exchange` topic, routing key `payment.confirmed`) |
| 발행 라우팅키 | `settlement.ledger.completed` → `settlement.ledger.completed.queue` (core-spa가 소비) |
| DLX / DLQ | `payment.dlx` (direct) + `ledger.recording.queue.dlq`, `settlement.ledger.completed.queue.dlq` |
| 수신 페이로드 | `{ messageType, payload{ orderId, items[{ sellerId, productId, amount, quantity }] }, metadata }` |
| 발행 페이로드 | `{ payload: { orderId } }` |

프로듀서가 붙이는 `__TypeId__` 헤더는 여기 없는 클래스를 가리킨다. 그래서 타입 매퍼를 `INFERRED`로 두고
`@RabbitListener` 파라미터 타입으로만 푼다. DLX와 DLQ 인자는 조심해야 한다. core-spa든 다른 워커든,
한 글자만 달라도 RabbitMQ가 큐 재선언을 거부한다.

