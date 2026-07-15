package com.bookService.ledger.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMqConfig {

    // core-spa(RabbitMqConfig)와 이름을 반드시 일치시켜야 한다. 두 프로젝트가
    // 서로 다른 배포 단위라 상수를 공유하지 않으므로 문자열로 중복 선언한다.
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_CONFIRMED_ROUTING_KEY = "payment.confirmed";

    // 이 워커 전용 큐 — 알림/재고차감/정산과 같은 routing key에 각자 독립 바인딩되는 팬아웃.
    public static final String LEDGER_RECORDING_QUEUE = "ledger.recording.queue";

    // 장부 기록 완결 통지. core-spa(M4)가 구독해 is_ledger_updated → completeIfDone()에 반영할 예정.
    public static final String SETTLEMENT_LEDGER_COMPLETED_ROUTING_KEY = "settlement.ledger.completed";
    public static final String SETTLEMENT_LEDGER_COMPLETED_QUEUE = "settlement.ledger.completed.queue";

    // core-spa(RabbitMqConfig)와 이름·인자를 반드시 일치시켜야 한다 — DLX/DLQ 이름도 동일.
    public static final String DLX_EXCHANGE = "payment.dlx";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue ledgerRecordingQueue() {
        return QueueBuilder.durable(LEDGER_RECORDING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", LEDGER_RECORDING_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue ledgerRecordingDlq() {
        return QueueBuilder.durable(LEDGER_RECORDING_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding ledgerRecordingDlqBinding(Queue ledgerRecordingDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(ledgerRecordingDlq).to(dlxExchange).with(LEDGER_RECORDING_QUEUE + ".dlq");
    }

    @Bean
    public Binding ledgerRecordingBinding(Queue ledgerRecordingQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(ledgerRecordingQueue).to(paymentExchange).with(PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    // core-spa도 이 큐를 선언한다(M4) — 인자를 반드시 동일하게 유지.
    @Bean
    public Queue settlementLedgerCompletedQueue() {
        return QueueBuilder.durable(SETTLEMENT_LEDGER_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SETTLEMENT_LEDGER_COMPLETED_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue settlementLedgerCompletedDlq() {
        return QueueBuilder.durable(SETTLEMENT_LEDGER_COMPLETED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding settlementLedgerCompletedDlqBinding(Queue settlementLedgerCompletedDlq, DirectExchange dlxExchange) {
        return BindingBuilder.bind(settlementLedgerCompletedDlq).to(dlxExchange)
                .with(SETTLEMENT_LEDGER_COMPLETED_QUEUE + ".dlq");
    }

    @Bean
    public Binding settlementLedgerCompletedBinding(Queue settlementLedgerCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(settlementLedgerCompletedQueue).to(paymentExchange)
                .with(SETTLEMENT_LEDGER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    // 프로듀서(core-spa)가 실어보내는 __TypeId__ 헤더는 이 프로젝트에 없는 클래스라서
    // INFERRED로 두어 @RabbitListener 파라미터 타입으로만 역직렬화한다.
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("발행 confirm 실패(broker nack) — 메시지가 브로커에 반영되지 않았을 수 있음. cause={}", cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "발행 메시지가 어떤 큐에도 라우팅되지 못해 반환됨(unroutable). exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return rabbitTemplate;
    }
}
