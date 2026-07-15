package com.bookService.ledger.listener;

import com.bookService.ledger.config.RabbitMqConfig;
import com.bookService.ledger.dto.LedgerCompletedMessage;
import com.bookService.ledger.dto.PaymentEventMessage;
import com.bookService.ledger.publisher.LedgerCompletedPublisher;
import com.bookService.ledger.service.LedgerRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerListener {

    private final LedgerRecordingService ledgerRecordingService;
    private final LedgerCompletedPublisher ledgerCompletedPublisher;

    @RabbitListener(queues = RabbitMqConfig.LEDGER_RECORDING_QUEUE)
    public void onPaymentConfirmed(PaymentEventMessage message) {
        log.info("장부 기록 대상 결제 확정 이벤트 수신: orderId={}", message.getPayload().get("orderId"));

        LedgerCompletedMessage completed = ledgerRecordingService.record(message);
        ledgerCompletedPublisher.publish(completed);
    }
}
