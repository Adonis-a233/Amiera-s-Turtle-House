package com.example.community.im.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ImMqConfig {

    public static final String EXCHANGE     = "im.exchange";
    public static final String ROUTING_KEY  = "im.message";
    public static final String QUEUE        = "im.message.queue";
    /** 供 @RabbitListener(queues = ImMqConfig.IM_QUEUE) 引用 */
    public static final String IM_QUEUE     = QUEUE;

    public static final String DLX          = "im.dlx";
    public static final String DLQ          = "im.message.dlq";
    public static final String DLQ_RK       = "im.dlq";

    @Bean
    public DirectExchange imExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange imDlx() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue imQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX);
        args.put("x-dead-letter-routing-key", DLQ_RK);
        return QueueBuilder.durable(QUEUE).withArguments(args).build();
    }

    @Bean
    public Queue imDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding imBinding() {
        return BindingBuilder.bind(imQueue()).to(imExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding imDlqBinding() {
        return BindingBuilder.bind(imDlq()).to(imDlx()).with(DLQ_RK);
    }
}
