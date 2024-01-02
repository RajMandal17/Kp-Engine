package com.gitbitex.matchingengine;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbitex.kafka.KafkaMessageProducer;
import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.marketdata.repository.OrderRepository;
import com.gitbitex.marketdata.repository.TradeEmitRepository;
import com.gitbitex.marketdata.repository.TradeRepository;
import com.gitbitex.matchingengine.command.Command;
import com.gitbitex.matchingengine.message.OrderBookMessage;
import com.gitbitex.stripexecutor.StripedExecutorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ModifiedObjectWriter implements EngineListener {
    private final KafkaMessageProducer producer;
    private final Counter modifiedObjectCreatedCounter = Counter
            .builder("gbe.matching-engine.modified-object.created")
            .register(Metrics.globalRegistry);
    private final Counter modifiedObjectSavedCounter = Counter
            .builder("gbe.matching-engine.modified-object.saved")
            .register(Metrics.globalRegistry);
    private final RTopic accountTopic;
    private final TradeEmitRepository tradeEmitRepository;
    private  final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final RTopic orderTopic;
    private final RTopic orderBookMessageTopic;
    private final ConcurrentLinkedQueue<ModifiedObjectList> modifiedObjectsQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService mainExecutor = Executors.newScheduledThreadPool(1);
    private final StripedExecutorService kafkaExecutor = new StripedExecutorService(Runtime.getRuntime().availableProcessors());
    private final StripedExecutorService redisExecutor = new StripedExecutorService(Runtime.getRuntime().availableProcessors());
    private long lastCommandOffset;

    public ModifiedObjectWriter(KafkaMessageProducer producer, RedissonClient redissonClient, TradeEmitRepository tradeEmitRepository, TradeRepository tradeRepository, OrderRepository orderRepository) {
        this.producer = producer;
        this.accountTopic = redissonClient.getTopic("account", StringCodec.INSTANCE);
        this.orderTopic = redissonClient.getTopic("order", StringCodec.INSTANCE);
        this.orderBookMessageTopic = redissonClient.getTopic("orderBookLog", StringCodec.INSTANCE);
        this.tradeEmitRepository = tradeEmitRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        startMainTask();
    }

    @PreDestroy
    public void close() {
        mainExecutor.shutdown();
        kafkaExecutor.shutdown();
        redisExecutor.shutdown();
    }

    @Override
    public void onCommandExecuted(Command command, ModifiedObjectList modifiedObjects) {
        if (lastCommandOffset != 0 && modifiedObjects.getCommandOffset() <= lastCommandOffset) {
            logger.info("received processed message: {}", modifiedObjects.getCommandOffset());
            return;
        }
        lastCommandOffset = modifiedObjects.getCommandOffset();
        modifiedObjectsQueue.offer(modifiedObjects);
    }

    private void save(ModifiedObjectList modifiedObjects) {
        modifiedObjectCreatedCounter.increment(modifiedObjects.size());
        modifiedObjects.forEach(obj -> {
            if (obj instanceof com.gitbitex.marketdata.entity.Order) {
                save(modifiedObjects.getSavedCounter(), (com.gitbitex.marketdata.entity.Order) obj);
            } else if (obj instanceof Account) {
                save(modifiedObjects.getSavedCounter(), (Account) obj);
            } else if (obj instanceof Trade) {
                save(modifiedObjects.getSavedCounter(), (Trade) obj);
            } else if (obj instanceof OrderBookMessage) {
                save(modifiedObjects.getSavedCounter(), (OrderBookMessage) obj);
            } else {
                modifiedObjects.getSavedCounter().incrementAndGet();
                modifiedObjectSavedCounter.increment();
            }
        });
    }

    private void save(AtomicLong savedCounter, Account account) {
        kafkaExecutor.execute(account.getUserId(), () -> {
            String data = JSON.toJSONString(account);
            producer.sendAccount(account, (m, e) -> {
                savedCounter.incrementAndGet();
                modifiedObjectSavedCounter.increment();
            });
            redisExecutor.execute(account.getUserId(), () -> {
                accountTopic.publishAsync(data);
            });
        });
    }

    private void save(AtomicLong savedCounter, com.gitbitex.marketdata.entity.Order order) {
        String productId = order.getProductId();
        kafkaExecutor.execute(productId, () -> {
            String data = JSON.toJSONString(order);
            producer.sendOrder(order, (m, e) -> {
                savedCounter.incrementAndGet();
                modifiedObjectSavedCounter.increment();
            });
            redisExecutor.execute(order.getUserId(), () -> {
                orderTopic.publishAsync(data);
                orderRepository.save(order);
            });
        });
    }


    private void save(AtomicLong savedCounter, Trade trade) {
        kafkaExecutor.execute(trade.getProductId(), () -> {
            try {
                producer.sendTrade(trade, (m, e) -> {
                    savedCounter.incrementAndGet();
                    modifiedObjectSavedCounter.increment();
                });

                // Check if the API is reachable
                if (isApiReachable("http://localhost:3000/api/tradehistory")) {
                    RestTemplate restTemplate = new RestTemplate();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Trade> requestEntity = new HttpEntity<>(trade, headers);
                    trade.setStatus("0");
                    String apiUrl = "http://localhost:3000/api/tradehistory";

                    ResponseEntity<String> responseEntity = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

                    if (responseEntity.getStatusCode() == HttpStatus.OK) {
                        // Assuming the response is a JSON object with a "status" field
                        String responseBody = responseEntity.getBody();
                        JsonNode jsonNode = new ObjectMapper().readTree(responseBody);

                        if (jsonNode.has("status") && jsonNode.get("status").asInt() == 200) {
                            // Response status is 200, save to tradeRepository
                            tradeRepository.save(trade);
                            logger.info("Trade saved to tradeRepository: {}", trade);
                        } else {
                            logger.info("else condition: {}", trade);
                        }
                    }
                } else {
                    // API not reachable, log and save to both repositories
                    logger.warn("API not reachable. Saving to both repositories.");
                    tradeRepository.save(trade);
                    tradeEmitRepository.save(TradeEmitDto(trade));

                    logger.info("Trade saved to tradeEmitRepository: {}", trade);
                }
            } catch (HttpClientErrorException e) {
                handleHttpClientErrorException(e, trade);
            } catch (HttpServerErrorException e) {
                handleHttpServerErrorException(e, trade);
            } catch (Exception e) {
                handleGeneralException(e, trade);
            }
        });
    }


    private boolean isApiReachable(String apiUrl) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2000); // Set timeout to 2 seconds
            connection.connect();
            connection.disconnect();
            return true;
        } catch (IOException e) {
            // Log or handle the exception if needed
            return false;
        }
    }


    private TradeEmit TradeEmitDto(Trade trade) {

        TradeEmit tradeEmit = new TradeEmit();
        tradeEmit.setTradeEmitId(UUID.randomUUID().toString());
        tradeEmit.setProductId(trade.getProductId());
        tradeEmit.setSize(trade.getSize());
        tradeEmit.setPrice(trade.getPrice());
        tradeEmit.setFunds(trade.getFunds());
        tradeEmit.setSequence(trade.getSequence());
        tradeEmit.setSide(trade.getSide());
        tradeEmit.setTime(trade.getTime());
        tradeEmit.setStatus("0");
        tradeEmit.setMakerOrderId(trade.getMakerOrderId());
        tradeEmit.setTakerOrderId(trade.getTakerOrderId());


       return tradeEmit;
    }

    private void handleHttpClientErrorException(HttpClientErrorException e, Trade trade) {

        logger.error("HTTP client error occurred: {}", e.getMessage(), e);
    }

    private void handleHttpServerErrorException(HttpServerErrorException e, Trade trade) {

        logger.error("HTTP server error occurred: {}", e.getMessage(), e);
    }

    private void handleGeneralException(Exception e, Trade trade) {

        logger.error("Exception occurred during save: {}", e.getMessage(), e);
    }


    private void save(AtomicLong savedCounter, OrderBookMessage orderBookMessage) {
        savedCounter.incrementAndGet();
        modifiedObjectSavedCounter.increment();
        redisExecutor.execute(orderBookMessage.getProductId(), () -> {
            orderBookMessageTopic.publishAsync(JSON.toJSONString(orderBookMessage));
        });
    }

    private void startMainTask() {
        mainExecutor.scheduleWithFixedDelay(() -> {
            while (true) {
                ModifiedObjectList modifiedObjects = modifiedObjectsQueue.poll();
                if (modifiedObjects == null) {
                    break;
                }
                save(modifiedObjects);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }
}
