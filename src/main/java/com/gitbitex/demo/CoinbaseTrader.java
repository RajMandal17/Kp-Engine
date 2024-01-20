package com.gitbitex.demo;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbitex.marketdata.entity.User;
import com.gitbitex.marketdata.entity.mm;
import com.gitbitex.marketdata.entity.mmPrice;
import com.gitbitex.marketdata.repository.MMRepository;
import com.gitbitex.marketdata.repository.*;
import com.gitbitex.marketdata.repository.TradeRepository;
import com.gitbitex.openapi.controller.AdminController;
import com.gitbitex.openapi.controller.OrderController;
import com.gitbitex.openapi.model.PlaceOrderRequest;
import com.google.common.util.concurrent.RateLimiter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class CoinbaseTrader {
    private static final RateLimiter rateLimiter = RateLimiter.create(10);
    private final OrderController orderController;
    private final MMRepository mmRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private  ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    private final AdminController adminController;
    private final TradeRepository tradeRepository;
    private final MmPriceRepository mmPriceRepository;

    private final Object lock = new Object();  // Use an object for synchronization
    private long cronTimeInSeconds;
    private volatile boolean initRunning = false;

    private MyClient client = null;

    @PostConstruct
    public void init() {
        logger.info("start 2nd cron");

        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                synchronized (lock) {
                    try {
                        List<mm> product = mmRepository.findAll();
                        if (product.isEmpty()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Products not found");
                        } else {
                            for (mm pro : product) {
                                String productId = pro.getBaseCurrency() + pro.getQuoteCurrency();
                                String apiUrl = "http://65.20.85.175/api/binance_ticker?symbol=" + productId;
                                RestTemplate restTemplate = new RestTemplate();
                                String jsonResponse = restTemplate.getForObject(apiUrl, String.class);

                                try {
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    JsonNode jsonNode = objectMapper.readTree(jsonResponse);
                                    BigDecimal randomPrice = new BigDecimal(jsonNode.get("price").asText());

                                    mmPrice mmPrice = new mmPrice();
                                    mmPrice.setRandomPrice(randomPrice);
                                    mmPrice.setProductId(pro.getId());
                                    mmPrice.setTimeInForce();
                                    mmPriceRepository.updateRandomPrice(mmPrice.getProductId(), mmPrice.getRandomPrice());
                                } catch (Exception e) {
                                    logger.error("Scheduled task inner error: {}", e.getMessage(), e);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("Scheduled task outer error: {}", ex.getMessage(), ex);
                    }
                }
            } catch (Exception e) {
                logger.error("Scheduled task error: {}", e.getMessage(), e);
            }
        }, 0, 300, TimeUnit.SECONDS);
    }





    public void initWithUser() {
        logger.info("Running order cron");

        User user = adminController.createUser("test@test.com", "12345678");

        try {
            client = new MyClient(new URI("wss://ws-feed.exchange.coinbase.com"), user);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try {
            synchronized (lock) {
                List<mm> product = mmRepository.findAll();

                if (product == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + product);
                } else {
                    for (mm pro : product) {
                        volumeBaseOrder(user, pro);
                    }
                }

                if (true) {
                    return;
                }

                if (!client.isOpen()) {
                    try {
                        if (client.getReadyState().equals(ReadyState.NOT_YET_CONNECTED)) {
                            logger.info("connecting...: {}", client.getURI());
                            client.connectBlocking();
                        } else if (client.getReadyState().equals(ReadyState.CLOSING) || client.getReadyState().equals(
                                ReadyState.CLOSED)) {
                            logger.info("reconnecting...: {}", client.getURI());
                            client.reconnectBlocking();
                        }
                    } catch (Exception e) {
                        logger.error("ws error ", e);
                    }
                } else {
                    client.sendPing();
                }
            }
        } catch (Exception e) {
            logger.error("Error in scheduled task: {}", e.getMessage(), e);
        }
    }


    public void startInit(long id) {
        synchronized (lock) {
            if (!initRunning) {
                initRunning = true;

                // Recreate the ScheduledExecutorService if it was shut down
                if (scheduledExecutor.isShutdown() || scheduledExecutor.isTerminated()) {
                    scheduledExecutor = Executors.newScheduledThreadPool(1);
                }

                scheduledExecutor.scheduleAtFixedRate(this::initWithUser, 0, id, TimeUnit.SECONDS);
                logger.info("Initialization started");
            } else {
                logger.warn("Initialization is already running");
            }
        }
    }

    public void stopInit() {
        synchronized (lock) {
            if (initRunning) {
                initRunning = false;
                scheduledExecutor.shutdown();
                try {
                    if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduledExecutor.shutdownNow();
                        if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            logger.error("Scheduler did not terminate");
                        }
                    }
                } catch (InterruptedException e) {
                    scheduledExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                logger.info("Initialization stopped");
            } else {
                logger.warn("Initialization is not running");
            }
        }
    }



    @PreDestroy
    public void destroy() {
        executor.shutdown();
        scheduledExecutor.shutdown();
    }

    public void test(User user) {
        PlaceOrderRequest order = new PlaceOrderRequest();
        order.setProductId("BTC-USDT");
        order.setClientOid(UUID.randomUUID().toString());
        order.setPrice(String.valueOf(BigDecimal.valueOf(new Random().nextInt(10) + 1)));
        order.setSize(String.valueOf(new Random().nextInt(10) + 1));
        order.setFunds(String.valueOf(new Random().nextInt(10) + 1));
        order.setSide(new Random().nextBoolean() ? "BUY" : "SELL");
        order.setType("limit");
        String objectAsString = user.toString();
        //      orderController.placeOrder(order, objectAsString);
    }
    public void volumeBaseOrder(User user , mm product) {
        BigDecimal randomPrice = null;
        try {
            mmPrice mmPriceList = mmPriceRepository.findById(product.getId());
            int currentMinute=LocalDateTime.now().getMinute();
            //    for (mmPrice mmPrice : mmPriceList) {
            if (mmPriceList.getTimeInForce() != null  ) {
                randomPrice = mmPriceList.getRandomPrice();

            } else {

                System.out.println("Skipped mmPrice take hardcoded price: " + mmPriceList.toString());
            }
            //}

            BigDecimal volume = BigDecimal.valueOf(tradeRepository.countTradesLast24Hours(String.valueOf(0)));
            BigDecimal maxSize = product.getOrderSizeMax();
            BigDecimal minSize = product.getOrderSizeMin();
            BigDecimal maxPrice = product.getMaxPriceRatio();
            BigDecimal minPrice = product.getMinPriceRatio();
            BigDecimal randomSize = minSize
                    .add(new BigDecimal(Math.random()).multiply(maxSize.subtract(minSize))).setScale(8, RoundingMode.HALF_UP);;
            BigDecimal randomMultiplier = minPrice
                    .add(new BigDecimal(Math.random()).multiply(maxPrice.subtract(minPrice)))
                    .setScale(8, RoundingMode.HALF_UP);
            BigDecimal finalPrice = randomPrice.multiply(randomMultiplier).setScale(8, RoundingMode.HALF_UP);


            PlaceOrderRequest orders = new PlaceOrderRequest();
            orders.setProductId(product.getId());
       //     orders.setClientOid(UUID.randomUUID().toString());
            orders.setPrice(String.valueOf(finalPrice.add(randomPrice)));
            orders.setSize(String.valueOf(randomSize));
            orders.setType("limit");
            orders.setFunds(String.valueOf(new Random().nextInt(10) + 1));
            orders.setSide(new Random().nextBoolean() ? "BUY" : "SELL");
         //   orders.setSpread(product.getSpread());

            String objectAsString = user.toString();
            orderController.placeOrder(orders, objectAsString,product.getSpread());
        } catch (Exception e) {
            e.printStackTrace();
        }}

    @Getter
    @Setter
    public static class ChannelMessage {
        private String type;
        private String product_id;
        private long tradeId;
        private long sequence;
        private String taker_order_id;
        private String maker_order_id;
        private String time;
        private String size;
        private String price;
        private String side;
        private String orderId;
        private String remaining_size;
        private String funds;
        private String order_type;
        private String reason;
    }

    public class MyClient extends WebSocketClient {
        private final User user;

        public MyClient(URI serverUri, User user) {
            super(serverUri, new Draft_6455(), null, 1000);
            this.user = user;
        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {
            logger.info("open");

            send("{\"type\":\"subscribe\",\"product_ids\":[\"BTC-USD\"],\"channels\":[\"full\"],\"token\":\"\"}");
            //       send("{\"type\":\"subscribe\",\"product_ids\":[\"BCD-USD\"],\"channels\":[\"full\"],\"token\":\"\"}");

        }

        @Override
        public void onMessage(String s) {
            if (!rateLimiter.tryAcquire()) {
                return;
            }
            executor.execute(() -> {
                try {
                    ChannelMessage message = JSON.parseObject(s, ChannelMessage.class);
                    String productId = message.getProduct_id() + "T";
                    switch (message.getType()) {
                        case "received":
                            logger.info(JSON.toJSONString(message));
                            if (message.getPrice() != null) {
                                PlaceOrderRequest order = new PlaceOrderRequest();
                                order.setProductId(productId);
                                order.setClientOid(UUID.randomUUID().toString());
                                order.setPrice(String.valueOf(new BigDecimal(message.getPrice())));//new BigDecimal(String.valueOf(request.getPrice()
                                order.setSize(message.getSize());
                                order.setFunds(message.getFunds());
                                order.setSide(message.getSide().toLowerCase());
                                order.setType("limit");
                                String objectAsString = user.toString();
                                orderController.placeOrder(order, objectAsString);

                            }
                            break;
                        case "done":
                            adminController.cancelOrder(message.getOrderId(), productId);
                            break;
                        default:
                    }
                } catch (Exception e) {
                    logger.error("error: {}", e.getMessage(), e);
                }
            });
        }

        @Override
        public void onClose(int i, String s, boolean b) {
            logger.info("connection closed");
        }

        @Override
        public void onError(Exception e) {
            logger.error("error", e);
        }
    }
}
