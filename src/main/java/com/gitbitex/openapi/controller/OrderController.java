package com.gitbitex.openapi.controller;

import com.gitbitex.enums.OrderSide;
import com.gitbitex.enums.OrderStatus;
import com.gitbitex.enums.OrderType;
import com.gitbitex.enums.TimeInForce;
import com.gitbitex.feed.message.OrderFeedMessage;
import com.gitbitex.marketdata.entity.Order;
import com.gitbitex.marketdata.entity.Product;
import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.marketdata.entity.User;
import com.gitbitex.marketdata.repository.OrderRepository;
import com.gitbitex.marketdata.repository.ProductRepository;
import com.gitbitex.marketdata.repository.TradeEmitRepository;
import com.gitbitex.matchingengine.PlaceCronOrder;
import com.gitbitex.matchingengine.TradeEmit;
import com.gitbitex.matchingengine.command.CancelOrderCommand;
import com.gitbitex.matchingengine.command.MatchingEngineCommandProducer;
import com.gitbitex.matchingengine.command.PlaceOrderCommand;
import com.gitbitex.matchingengine.message.OrderMessage;
import com.gitbitex.openapi.model.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {
    private final OrderRepository orderRepository;
    private final MatchingEngineCommandProducer matchingEngineCommandProducer;
    private final ProductRepository productRepository;
    private final TradeEmitRepository tradeEmitRepository;


    @PostMapping(value = "/orders/{id}")
    public OrderDto placeOrder(@RequestBody @Valid PlaceOrderRequest request,@PathVariable String id) {
        Product product = productRepository.findById(request.getProductId());
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + request.getProductId());
        }

        request.setClientOid(UUID.randomUUID().toString());
        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        BigDecimal size = new BigDecimal(request.getSize());
        BigDecimal price = request.getPrice() != null ? new BigDecimal(String.valueOf(request.getPrice())) : null;
        BigDecimal funds = request.getFunds() != null ? new BigDecimal(request.getFunds()) : null;
        TimeInForce timeInForce = request.getTimeInForce() != null
                ? TimeInForce.valueOf(request.getTimeInForce().toUpperCase())
                : null;

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setProductId(request.getProductId());
        command.setOrderId(UUID.randomUUID().toString());
        command.setUserId(id.toString());
        command.setOrderType(type);
        command.setOrderSide(side);
        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
        command.setTime(new Date());
        command.setLast_trade_id(request.getLast_trade_id());
        command.setSpread(product.getSpread());

        formatPlaceOrderCommand(command, product);
        validatePlaceOrderCommand(command);
        matchingEngineCommandProducer.send(command, null);
        OrderFeedMessage msg = new OrderFeedMessage();
        OrderMessage ordermsg = new OrderMessage();
        OrderDto orderDto = new OrderDto();

        orderDto.setId(command.getOrderId());
        orderDto.setCreatedAt(String.valueOf(command.getTime()));
        orderDto.setProductId(command.getProductId());
        orderDto.setUserId(command.getUserId());
        //orderDto.setUpdatedAt(String.valueOf(currentUser.getUpdatedAt()));
        orderDto.setClientOid(request.getClientOid());
        orderDto.setSize(String.valueOf(command.getSize()));
        orderDto.setFunds(String.valueOf(command.getFunds()));
        orderDto.setFilledSize(request.getSize());
        BigDecimal fund = ordermsg.getFunds();
        BigDecimal remainingFunds = ordermsg.getRemainingFunds();

        BigDecimal executedValue = null;
        if (fund != null && remainingFunds != null) {
            executedValue = fund.subtract(remainingFunds).stripTrailingZeros();
            orderDto.setExecutedValue(executedValue.toPlainString());
        } else {
            orderDto.setExecutedValue("0");
        }
        orderDto.setPrice(String.valueOf(command.getPrice() != null ? command.getPrice() : BigDecimal.ZERO));
        orderDto.setFillFees(ordermsg.getFillFees() != null ? ordermsg.getFillFees().stripTrailingZeros().toPlainString() : "0");
        orderDto.setType(String.valueOf(command.getOrderType()));
        orderDto.setSide(String.valueOf(command.getOrderSide()));
        orderDto.setTimeInForce(request.getTimeInForce());
  //      orderDto.setStatus(String.valueOf(OrderStatus.FILLED));
        orderDto.setSettled(msg.isSettled());

        Order order = new Order();
        order.setId(orderDto.getId());
        order.setCreatedAt(parseDate(orderDto.getCreatedAt()));
        order.setUpdatedAt(parseDate(orderDto.getUpdatedAt()));
        order.setProductId(orderDto.getProductId());
        order.setFunds(orderDto.getFunds());
        order.setPrice(new BigDecimal(orderDto.getPrice()));
        order.setSize(new BigDecimal(orderDto.getSize()));
        order.setFilledSize(new BigDecimal(orderDto.getFilledSize()));
        order.setExecutedValue(new BigDecimal(orderDto.getExecutedValue()));
        order.setFillFees(new BigDecimal(orderDto.getFillFees()));
        order.setType(OrderType.valueOf(orderDto.getType()));
        order.setSide(OrderSide.valueOf(orderDto.getSide()));
  //      order.setStatus(OrderStatus.valueOf(orderDto.getStatus()));
        order.setTimeInForce(orderDto.getTimeInForce());
        order.setExecutedValue(executedValue);
        order.setSettled(orderDto.isSettled());
        order.setRole("USER");
        orderRepository.save(order);

        return orderDto;
    }



    public OrderDto placeOrder(PlaceOrderRequest request, String id,BigDecimal Spread) {
        Product product = productRepository.findById(request.getProductId());
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + request.getProductId());
        }
        request.setClientOid(UUID.randomUUID().toString());
        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        BigDecimal size = new BigDecimal(request.getSize());
        BigDecimal price = request.getPrice() != null ? new BigDecimal(String.valueOf(request.getPrice())) : null;
        BigDecimal funds = request.getFunds() != null ? new BigDecimal(request.getFunds()) : null;
        TimeInForce timeInForce = request.getTimeInForce() != null
                ? TimeInForce.valueOf(request.getTimeInForce().toUpperCase())
                : null;

        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setProductId(request.getProductId());
        command.setOrderId(UUID.randomUUID().toString());
        command.setUserId(id.toString());
        command.setOrderType(type);
        command.setOrderSide(side);
        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
        command.setTime(new Date());
        command.setLast_trade_id(request.getLast_trade_id());
        command.setSpread(Spread);

        formatPlaceOrderCommand(command, product);
        validatePlaceOrderCommand(command);
        matchingEngineCommandProducer.send(command, null);
        OrderFeedMessage msg = new OrderFeedMessage();
        OrderMessage ordermsg = new OrderMessage();
        OrderDto orderDto = new OrderDto();

        orderDto.setId(command.getOrderId());
        orderDto.setCreatedAt(String.valueOf(command.getTime()));
        orderDto.setProductId(command.getProductId());
        orderDto.setUserId(command.getUserId());
        //orderDto.setUpdatedAt(String.valueOf(currentUser.getUpdatedAt()));
        orderDto.setClientOid(request.getClientOid());
        orderDto.setSize(String.valueOf(command.getSize()));
        orderDto.setFunds(String.valueOf(command.getFunds()));
        orderDto.setFilledSize(request.getSize());
        BigDecimal fund = ordermsg.getFunds();
        BigDecimal remainingFunds = ordermsg.getRemainingFunds();

        BigDecimal executedValue = null;
        if (fund != null && remainingFunds != null) {
            executedValue = fund.subtract(remainingFunds).stripTrailingZeros();
            orderDto.setExecutedValue(executedValue.toPlainString());
        } else {
            orderDto.setExecutedValue("0");
        }
        orderDto.setPrice(String.valueOf(command.getPrice() != null ? command.getPrice() : BigDecimal.ZERO));
        orderDto.setFillFees(ordermsg.getFillFees() != null ? ordermsg.getFillFees().stripTrailingZeros().toPlainString() : "0");
        orderDto.setType(String.valueOf(command.getOrderType()));
        orderDto.setSide(String.valueOf(command.getOrderSide()));
        orderDto.setTimeInForce(request.getTimeInForce());
        //      orderDto.setStatus(String.valueOf(OrderStatus.FILLED));
        orderDto.setSettled(msg.isSettled());

        Order order = new Order();
        order.setId(orderDto.getId());
        order.setCreatedAt(parseDate(orderDto.getCreatedAt()));
        order.setUpdatedAt(parseDate(orderDto.getUpdatedAt()));
        order.setProductId(orderDto.getProductId());
        order.setFunds(orderDto.getFunds());
        order.setPrice(new BigDecimal(orderDto.getPrice()));
        order.setSize(new BigDecimal(orderDto.getSize()));
        order.setFilledSize(new BigDecimal(orderDto.getFilledSize()));
        order.setExecutedValue(new BigDecimal(orderDto.getExecutedValue()));
        order.setFillFees(new BigDecimal(orderDto.getFillFees()));
        order.setType(OrderType.valueOf(orderDto.getType()));
        order.setSide(OrderSide.valueOf(orderDto.getSide()));
        //      order.setStatus(OrderStatus.valueOf(orderDto.getStatus()));
        order.setTimeInForce(orderDto.getTimeInForce());
        order.setExecutedValue(executedValue);
        order.setSettled(orderDto.isSettled());
        order.setRole("BOT");
        orderRepository.save(order);

        return orderDto;
    }



    private Date parseDate(String dateString) {
        if (dateString == null) {
            // If the date string is null, set the date to the current date and time
            return Calendar.getInstance().getTime();
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            // Handle the exception appropriately, e.g., log an error or return null
            e.printStackTrace();
            return null;
        }
    }

    //  @PostMapping(value = "/orderEmit/{id}")
    public OrderDto placeOrderBaseOnVolume(@RequestBody @Valid PlaceCronOrder request, @PathVariable String id) {
        // Check if the product exists
        Product product = productRepository.findById(request.getProductId());
//        if (product == null) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found: " + request.getProductId());
//        }


    /*    String timeInForceString = request.getTimeInForce().toUpperCase();
        TimeInForce timeInForce;

        try {
            timeInForce = TimeInForce.valueOf(timeInForceString);
        } catch (IllegalArgumentException e) {
            // Handle the case where the string does not match any enum constant
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TimeInForce: " + timeInForceString);
        }
*/
        // Check if the requested volume is within the allowed threshold
        BigDecimal volumeThreshold = BigDecimal.valueOf(300000);
        BigDecimal requestVolume = new BigDecimal(String.valueOf(request.getVolume()));

        if (requestVolume.compareTo(volumeThreshold) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Volume is too high: " + requestVolume);
        }

        // Process the order
        OrderType type = OrderType.valueOf(request.getType().toUpperCase());
        OrderSide side = OrderSide.valueOf(request.getSide().toUpperCase());
        BigDecimal size = new BigDecimal(String.valueOf(request.getSize()));
        BigDecimal price = request.getPrice() != null ? new BigDecimal(String.valueOf(request.getPrice())) : null;
        BigDecimal funds = request.getFunds() != null ? new BigDecimal(String.valueOf(request.getFunds())) : null;


        PlaceOrderCommand command = new PlaceOrderCommand();
        command.setProductId(request.getProductId());
        command.setOrderId(UUID.randomUUID().toString());
        command.setUserId(id);
        command.setOrderType(type);
        command.setOrderSide(side);
        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
        command.setTime(new Date());
        command.setLast_trade_id(request.getLast_trade_id());


        formatPlaceOrderCommand(command, product);
        validatePlaceOrderCommand(command);
        matchingEngineCommandProducer.send(command, null);

        // Create the OrderDto
        OrderFeedMessage msg = new OrderFeedMessage();
        OrderMessage ordermsg = new OrderMessage();
        OrderDto orderDto = new OrderDto();

        orderDto.setId(command.getOrderId());
        orderDto.setCreatedAt(String.valueOf(command.getTime()));
        orderDto.setProductId(command.getProductId());
        orderDto.setUserId(command.getUserId());
        orderDto.setClientOid(request.getClientOid());
        orderDto.setSize(String.valueOf(command.getSize()));
        orderDto.setFunds(String.valueOf(command.getFunds()));
        orderDto.setFilledSize(String.valueOf(request.getSize()));

        BigDecimal fund = ordermsg.getFunds();
        BigDecimal remainingFunds = ordermsg.getRemainingFunds();

        if (fund != null && remainingFunds != null) {
            BigDecimal executedValue = fund.subtract(remainingFunds).stripTrailingZeros();
            orderDto.setExecutedValue(executedValue.toPlainString());
        } else {
            orderDto.setExecutedValue("0");
        }

        orderDto.setPrice(String.valueOf(command.getPrice() != null ? command.getPrice() : BigDecimal.ZERO));
        orderDto.setFillFees(ordermsg.getFillFees() != null ? ordermsg.getFillFees().stripTrailingZeros().toPlainString() : "0");
        orderDto.setType(String.valueOf(command.getOrderType()));
        orderDto.setSide(String.valueOf(command.getOrderSide()));
    //    orderDto.setTimeInForce(timeInForceString);
        orderDto.setStatus("new");
        orderDto.setSettled(msg.isSettled());

        return orderDto;
    }

    @GetMapping("/orderstatus/{orderId}")
    public ResponseEntity<Object> orderList(@PathVariable String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            return ResponseEntity.badRequest().body(new OrderCancellationResponse("failed", "nil"));
        }
            return ResponseEntity.ok(order);
        }





    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<Object> cancelOrder(@PathVariable String orderId) {
        Order order = orderRepository.findByOrderId(orderId);
        if (order == null) {
            return ResponseEntity.badRequest().body(new OrderCancellationResponse("failed", "cancelled"));
        }
        if (order.getStatus().equals(OrderStatus.CANCELLED)) {

            return ResponseEntity.badRequest().body(new OrderCancellationResponse("failed", "cancelled"));
        }else {

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.updateOrderStatus(order.getId(), order.getStatus());
            // Process the order cancellation here
            CancelOrderCommand command = new CancelOrderCommand();
            command.setProductId(order.getProductId());
            command.setOrderId(order.getId());
            matchingEngineCommandProducer.send(command, null);
        }
        // If cancellation is successful, return a success response
        return ResponseEntity.ok(new OrderCancellationResponse("success", "cancelling"));
         }
    // Response class for JSON structure
    private static class OrderCancellationResponse {
        private final String status;
        private final String type;

        public OrderCancellationResponse(String status, String type) {
            this.status = status;
            this.type = type;
        }

        public String getStatus() {
            return status;
        }

        public String getType() {
            return type;
        }
    }







    @GetMapping("/orderbooksnap")
    public ResponseEntity<String> getOrderBookSnapshot(@RequestParam String productId) {
        String orderBookSnapshot = orderRepository.getOrderBookSnapshot(productId);

        if (orderBookSnapshot != null) {
            return ResponseEntity.ok(orderBookSnapshot);
        } else {
            // Construct a JSON response with status "false" and message "no-product found"
            String jsonResponse = "{\"status\": false, \"message\": \"no-product found\"}";
            return ResponseEntity.status(404).body(jsonResponse);
        }
    }



//
//    @DeleteMapping("/orders")
//    @SneakyThrows
//    public void cancelOrders(String productId, String side, @RequestAttribute(required = false) User currentUser) {
//        if (currentUser == null) {
//            throw new ResponseStatusException(HttpStatus.OK);
//        }
//
//        OrderSide orderSide = side != null ? OrderSide.valueOf(side.toUpperCase()) : null;
//
//        PagedList<Order> orderPage = orderRepository.findAll(currentUser.getId(), productId, OrderStatus.OPEN,
//                orderSide, 1, 20000);
//
//        for (Order order : orderPage.getItems()) {
//            CancelOrderCommand command = new CancelOrderCommand();
//            command.setProductId(order.getProductId());
//            command.setOrderId(order.getId());
//            matchingEngineCommandProducer.send(command, null);
//        }
//    }
//
    @GetMapping("/orders")
    public PagedList<OrderDto> listOrders(@RequestParam(required = false) String productId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int pageSize,
                                          @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.OK);
        }

        OrderStatus orderStatus = status != null ? OrderStatus.valueOf(status.toUpperCase()) : null;

        PagedList<Order> orderPage = orderRepository.findAll(currentUser.getId(), productId, orderStatus, null,
                page, pageSize);
        return new PagedList<>(
                orderPage.getItems().stream().map(this::orderDto).collect(Collectors.toList()),
                orderPage.getCount());
    }
    @GetMapping("/cancelorderslist")
    public PagedList<OrderDto> listCancelOrders(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int pageSize) {

         String status="CANCELLED";
        OrderStatus orderStatus = status != null ? OrderStatus.valueOf(status.toUpperCase()) : null;

        PagedList<Order> orderPage = orderRepository.findAll( orderStatus, page, pageSize);
        return new PagedList<>(
                orderPage.getItems().stream().map(this::orderDto).collect(Collectors.toList()),
                orderPage.getCount());
    }

    private OrderDto orderDto(Order order) {
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
       orderDto.setUserId(order.getUserId());
       orderDto.setFillFees(String.valueOf(order.getFilledSize() != null ? order.getFilledSize(): BigDecimal.ZERO));
       orderDto.setUpdatedAt(String.valueOf(order.getUpdatedAt()));
       orderDto.setClientOid(order.getClientOid());
       orderDto.setTimeInForce(order.getTimeInForce());
        orderDto.setPrice(String.valueOf(order.getPrice() != null ? order.getPrice(): BigDecimal.ZERO));
        orderDto.setSize(order.getSize().toPlainString());
        orderDto.setFilledSize(String.valueOf(order.getFilledSize() != null ? order.getFilledSize(): BigDecimal.ZERO));
        orderDto.setFunds(String.valueOf(order.getFunds() != null ? order.getFunds() : BigDecimal.ZERO));

        orderDto.setExecutedValue(String.valueOf(order.getExecutedValue() != null ? order.getExecutedValue(): BigDecimal.ZERO));
        orderDto.setSide(order.getSide().name().toLowerCase());
        orderDto.setProductId(order.getProductId());
        orderDto.setType(order.getType().name().toLowerCase());
        if (order.getCreatedAt() != null) {
            orderDto.setCreatedAt(order.getCreatedAt().toInstant().toString());
        }
        if (order.getStatus() != null) {
            orderDto.setStatus(order.getStatus().name().toLowerCase());
        }
        return orderDto;
    }

    private void formatPlaceOrderCommand(PlaceOrderCommand command, Product product) {
        BigDecimal size = command.getSize();
        BigDecimal price = new BigDecimal(String.valueOf(command.getPrice()));
        BigDecimal funds = new BigDecimal(String.valueOf(command.getFunds()));
        OrderSide side =  command.getOrderSide();

        switch (command.getOrderType()) {
            case LIMIT -> {
              //  size = size.setScale(product.getBaseScale() );
              //  price = price.setScale(product.getQuoteScale());
                funds = side == OrderSide.BUY ? size.multiply(price) : BigDecimal.ZERO;
            }
            case MARKET -> {
                price = BigDecimal.ZERO;
                if (side == OrderSide.BUY) {
                    size = BigDecimal.ZERO;
                    funds = funds.setScale(product.getQuoteScale(), RoundingMode.DOWN);
                } else {
                    size = size.setScale(product.getBaseScale(), RoundingMode.DOWN);
                    funds = BigDecimal.ZERO;
                }
            }
            default -> throw new RuntimeException("unknown order type: " + command.getType());
        }

        command.setSize(size);
        command.setPrice(price);
        command.setFunds(funds);
    }

    private void validatePlaceOrderCommand(PlaceOrderCommand command) {
        BigDecimal size = command.getSize();
        BigDecimal funds = command.getFunds();
        OrderSide side = command.getOrderSide();

        if (side == OrderSide.SELL) {
            if (size.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("bad SELL order: size must be positive");
            }
        } else {
            if (funds.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("bad BUY order: funds must be positive");
            }
        }
    }

}
