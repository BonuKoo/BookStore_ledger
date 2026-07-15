package com.bookService.ledger.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue ledgerRecordingQueue() {
        return QueueBuilder.durable(LEDGER_RECORDING_QUEUE).build();
    }

    @Bean
    public Binding ledgerRecordingBinding(Queue ledgerRecordingQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(ledgerRecordingQueue).to(paymentExchange).with(PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Queue settlementLedgerCompletedQueue() {
        return QueueBuilder.durable(SETTLEMENT_LEDGER_COMPLETED_QUEUE).build();
    }

    @Bean
    public Binding settlementLedgerCompletedBinding(Queue settlementLedgerCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(settlementLedgerCompletedQueue).to(paymentExchange)
                .with(SETTLEMENT_LEDGER_COMPLETED_ROUTING_KEY);
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
        return rabbitTemplate;
    }
}
