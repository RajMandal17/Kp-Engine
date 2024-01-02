package com.gitbitex.kafka;

import com.alibaba.fastjson.JSON;
import com.gitbitex.AppProperties;
import com.gitbitex.marketdata.entity.Order;
import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.matchingengine.Account;
import com.gitbitex.matchingengine.TradeEmit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

@Slf4j
public class KafkaMessageProducer extends KafkaProducer<String, String> {
    private final AppProperties appProperties;

    public KafkaMessageProducer(Properties kafkaProperties, AppProperties appProperties) {
        super(kafkaProperties);
        this.appProperties = appProperties;
    }

    public void sendOrder(Order order, Callback callback) {
        send(new ProducerRecord<>(appProperties.getOrderMessageTopic(), order.getId(),
                JSON.toJSONString(order)), (m, e) -> {
            if (e != null) {
                throw new RuntimeException(e);
            }
            callback.onCompletion(m, null);
        });
    }

    public void sendAccount(Account account, Callback callback) {
        send(new ProducerRecord<>(appProperties.getAccountMessageTopic(), account.getUserId(),
                JSON.toJSONString(account)), (m, e) -> {
            if (e != null) {
                throw new RuntimeException(e);
            }
            callback.onCompletion(m, null);
        });
    }

    public void sendTrade(Trade trade, Callback callback) {
        send(new ProducerRecord<>(appProperties.getTradeMessageTopic(), trade.getProductId(),
                JSON.toJSONString(trade)), (m, e) -> {
            if (e != null) {
                throw new RuntimeException(e);
            }
            callback.onCompletion(m, null);
        });
    }
    public void sendTradeEmit(TradeEmit tradeEmit, Callback callback) {
        send(new ProducerRecord<>(appProperties.getTradeMessageTopic(), tradeEmit.getProductId(),
                JSON.toJSONString(tradeEmit)), (m, e) -> {
            if (e != null) {
                throw new RuntimeException(e);
            }
            callback.onCompletion(m, null);
        });
    }



}
