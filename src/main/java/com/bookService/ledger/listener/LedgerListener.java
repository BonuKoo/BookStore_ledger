package com.bookService.ledger.listener;

import com.bookService.ledger.config.RabbitMqConfig;
import com.bookService.ledger.dto.LedgerCompletedMessage;
import com.bookService.ledger.dto.PaymentEventMessage;
import com.bookService.ledger.publisher.LedgerCompletedPublisher;
import com.bookService.ledger.service.LedgerRecordingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerListener {

    private final LedgerRecordingService ledgerRecordingService;
    private final LedgerCompletedPublisher ledgerCompletedPublisher;

    @RabbitListener(queues = RabbitMqConfig.LEDGER_RECORDING_QUEUE)
    public void onPaymentConfirmed(PaymentEventMessage message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        log.info("장부 기록 대상 결제 확정 이벤트 수신: orderId={}", message.getPayload().get("orderId"));

        try {
            LedgerCompletedMessage completed = ledgerRecordingService.record(message);
            ledgerCompletedPublisher.publish(completed);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("장부 기록 처리 실패 — DLQ 적재: orderId={}", message.getPayload().get("orderId"), e);
            channel.basicNack(tag, false, false);
        }
    }
}
