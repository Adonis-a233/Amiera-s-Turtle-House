package com.example.community.im.mq;

import com.example.community.im.config.ImMqConfig;
import com.example.community.im.entity.ImMessage;
import com.example.community.im.mapper.MessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ImMessageConsumer {

    private static final int MAX_RETRY = 3;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /** 批量缓冲区，存储 (ImMessage, Channel, deliveryTag, rawBody, headers) */
    private final List<PendingMsg> buffer = new ArrayList<>();

    @RabbitListener(queues = ImMqConfig.IM_QUEUE)
    public void onMessage(Message rawMsg, Channel channel) throws Exception {
        long tag = rawMsg.getMessageProperties().getDeliveryTag();
        try {
            ImMessage msg = objectMapper.readValue(rawMsg.getBody(), ImMessage.class);
            synchronized (buffer) {
                buffer.add(new PendingMsg(
                        msg, channel, tag,
                        rawMsg.getBody(),
                        new HashMap<>(rawMsg.getMessageProperties().getHeaders())));
                if (buffer.size() >= 20) {
                    flush();
                }
            }
        } catch (Exception e) {
            // 反序列化失败是永久性错误（坏数据无法修复），直接进 DLQ，不无限 requeue
            log.error("IM消息反序列化失败，直接进DLQ，tag={}", tag, e);
            channel.basicNack(tag, false, false);
        }
    }

    /** 每 500ms 触发一次兜底刷盘，防止消息长时间驻留缓冲 */
    @Scheduled(fixedDelay = 500)
    public void scheduledFlush() {
        synchronized (buffer) {
            flush();
        }
    }

    /** 调用方须持有 buffer 锁 */
    private void flush() {
        if (buffer.isEmpty()) return;
        List<PendingMsg> batch = new ArrayList<>(buffer);
        buffer.clear();
        List<ImMessage> msgs = new ArrayList<>();
        for (PendingMsg p : batch) msgs.add(p.msg());
        try {
            messageMapper.batchInsert(msgs);
            for (PendingMsg p : batch) {
                try { p.channel().basicAck(p.tag(), false); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("IM消息批量持久化失败，按重试次数决定重发或进DLQ", e);
            for (PendingMsg p : batch) {
                try {
                    int retryCount = getCustomRetryCount(p.headers());
                    if (retryCount >= MAX_RETRY) {
                        // 超过最大重试次数，ACK 原消息并路由进 DLQ
                        p.channel().basicAck(p.tag(), false);
                        republishToDlq(p);
                    } else {
                        // ACK 原消息，重新发布到工作队列，带递增的 x-retry-count header
                        p.channel().basicAck(p.tag(), false);
                        republishWithRetry(p, retryCount + 1);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 从消息 headers 中读取自定义重试计数（x-retry-count）。
     * 普通 requeue 不会产生 x-death header，所以原来的 getRetryCount 读 x-death 永远返回 0。
     * 此处改为读 x-retry-count，由重发时自己维护。
     */
    private int getCustomRetryCount(Map<String, Object> headers) {
        Object count = headers.get("x-retry-count");
        if (count instanceof Number n) return n.intValue();
        return 0;
    }

    private void republishWithRetry(PendingMsg p, int newRetryCount) {
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(p.headers());
        props.getHeaders().put("x-retry-count", newRetryCount);
        rabbitTemplate.send(ImMqConfig.EXCHANGE, ImMqConfig.ROUTING_KEY,
                new Message(p.rawBody(), props));
    }

    private void republishToDlq(PendingMsg p) {
        log.warn("IM消息达到最大重试次数，路由进DLQ");
        MessageProperties props = new MessageProperties();
        props.getHeaders().putAll(p.headers());
        rabbitTemplate.send(ImMqConfig.DLX, ImMqConfig.DLQ_RK,
                new Message(p.rawBody(), props));
    }

    private record PendingMsg(ImMessage msg, Channel channel, long tag,
                              byte[] rawBody, Map<String, Object> headers) {}
}
