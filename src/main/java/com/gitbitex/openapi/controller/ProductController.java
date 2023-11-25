package com.gitbitex.openapi.controller;

import com.gitbitex.enums.OrderSide;
import com.gitbitex.enums.OrderType;
import com.gitbitex.enums.TimeInForce;
import com.gitbitex.feed.message.OrderFeedMessage;
import com.gitbitex.marketdata.entity.Candle;
import com.gitbitex.marketdata.entity.Order;
import com.gitbitex.marketdata.entity.Product;
import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.marketdata.repository.CandleRepository;
import com.gitbitex.marketdata.repository.OrderRepository;
import com.gitbitex.marketdata.repository.ProductRepository;
import com.gitbitex.marketdata.repository.TradeRepository;
import com.gitbitex.matchingengine.OrderBookSnapshotStore;
import com.gitbitex.matchingengine.command.MatchingEngineCommandProducer;
import com.gitbitex.matchingengine.command.PlaceOrderCommand;
import com.gitbitex.matchingengine.command.PutProductCommand;
import com.gitbitex.matchingengine.message.OrderMatchMessage;
import com.gitbitex.matchingengine.message.OrderMessage;
import com.gitbitex.openapi.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController()
@RequiredArgsConstructor
public class ProductController {
    private final OrderBookSnapshotStore orderBookSnapshotStore;
    private final ProductRepository productRepository;
    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final CandleRepository candleRepository;
    private final MatchingEngineCommandProducer producer;

    @GetMapping("/api/products")
    public List<ProductDto> getProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::productDto).collect(Collectors.toList());
    }
    @PostMapping("/api/addproducts")
    public Product addProduct(@RequestBody ProductDto request) {
        String productId = request.getBaseCurrency() + "-" + request.getQuoteCurrency();
        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.OK, "product is Added: " + productId);
        } else if (product != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product Already there: " + productId);
        }
        product.setId(productId);
        Date currentDate = new Date();
        product.setCreatedAt(currentDate);
        product.setUpdatedAt(currentDate);
        product.setBaseCurrency(request.getBaseCurrency());
        product.setQuoteCurrency(request.getQuoteCurrency());
        product.setBaseScale(6);
        product.setQuoteScale(2);
        product.setBaseMinSize(BigDecimal.ZERO);
        product.setBaseMaxSize(new BigDecimal("100000000"));
        product.setQuoteMinSize(BigDecimal.ZERO);
        product.setQuoteMaxSize(new BigDecimal("10000000000"));
        product.setQuoteIncrement(request.getQuoteIncrement());
        productRepository.save(product); // Assuming you have a method to save the product in your repository
        return product;
    }

//
@GetMapping("/api/tradestatus/{tradeId}")
    public ResponseEntity<Object> tradeList(@PathVariable String tradeId) {
        Trade trade = tradeRepository.findByTradeId(tradeId);

    if (trade == null) {
        return ResponseEntity.badRequest().body(new ProductController.tradeStatusResponse("failed", "nil"));
    }
    return ResponseEntity.ok(trade);
}
//
@GetMapping("/api/products/{productId}/trades")
    public List<TradeDto> getProductTrades(@PathVariable String productId) {
        List<Trade> trades = tradeRepository.findByProductId(productId, 50);
        List<TradeDto> tradeDtos = new ArrayList<TradeDto>();
        for (Trade trade : trades) {
            tradeDtos.add(tradeDto(trade));
        }
        return tradeDtos;
    }

    @PostMapping("/api/trade/{id}")
    public ResponseEntity<Map<String, String>> searchTradeByProductId(@RequestBody @Valid TradeStatus request, @PathVariable String id) {
        List<Trade> trades = tradeRepository.findByProductId(id);

        if (!trades.isEmpty()) {
            for (Trade trade : trades) {
                trade.setStatus(request.getStatus());
                tradeRepository.save(trade);
            }
            Map<String, String> response = new HashMap<>();
            response.put("status", "200");
            response.put("id", id);
            response.put("message", "updated successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "400");
            errorResponse.put("id", id);
            errorResponse.put("message", "Not updated");
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

    }


    @GetMapping("/api/trade")
    public List<TradeDto> getTrades( ) {
        List<Trade> trades = tradeRepository.findAllTrade( "0",50);
        List<TradeDto> tradeDtos = new ArrayList<TradeDto>();
        for (Trade trade : trades) {
//            if(!trade.getStatus().equals("0")){
            tradeDtos.add(tradeDto(trade));

        }
//        }
        return tradeDtos;

    }

    @GetMapping("/api/trade/{orderId}")
    public ResponseEntity<?> getTradesByOrder(@PathVariable String orderId) {
        List<Trade> trades = tradeRepository.findTradeByOrderId(orderId, 50);

        if (trades.isEmpty()) {
            // If no trades found, construct a JSON response with status "false" and message "no-order found"
            Map<String, Object> response = new HashMap<>();
            response.put("status", false);
            response.put("message", "no-order found");
            return ResponseEntity.status(404).body(response);
        } else {
            // If trades found, convert them to DTOs and return
            List<TradeDto> tradeDtos = trades.stream().map(this::tradeDto).collect(Collectors.toList());
            return ResponseEntity.ok(tradeDtos);
        }
    }


    @GetMapping("/api/products/{productId}/candles")
    public List<List<Object>> getProductCandles(@PathVariable String productId, @RequestParam int granularity,
                                                @RequestParam(defaultValue = "1000") int limit) {
        PagedList<Candle> candlePage = candleRepository.findAll(productId, granularity / 60, 1, limit);

        //[
        //    [ time, low, high, open, close, volume ],
        //    [ 1415398768, 0.32, 4.2, 0.35, 4.2, 12.3 ],
        //]
        List<List<Object>> lines = new ArrayList<>();
        candlePage.getItems().forEach(x -> {
            List<Object> line = new ArrayList<>();
            line.add(x.getTime());
            line.add(x.getLow().stripTrailingZeros());
            line.add(x.getHigh().stripTrailingZeros());
            line.add(x.getOpen().stripTrailingZeros());
            line.add(x.getClose().stripTrailingZeros());
            line.add(x.getVolume().stripTrailingZeros());
            lines.add(line);
        });
        return lines;
    }

    @GetMapping("/api/products/{productId}/book")
    public Object getProductBook(@PathVariable String productId, @RequestParam(defaultValue = "2") int level) {
        return switch (level) {
            case 1 -> orderBookSnapshotStore.getL1OrderBook(productId);
            case 2 -> orderBookSnapshotStore.getL2OrderBook(productId);
            case 3 -> orderBookSnapshotStore.getL3OrderBook(productId);
            default -> null;
        };
    }

    private ProductDto productDto(Product product) {
        ProductDto productDto = new ProductDto();
        BeanUtils.copyProperties(product, productDto);
        productDto.setId(product.getId());
        productDto.setQuoteIncrement(Float.parseFloat(String.valueOf(product.getQuoteIncrement())));
        return productDto;
    }

    private TradeDto tradeDto(Trade trade) {
        TradeDto tradeDto = new TradeDto();
        tradeDto.setProductId(trade.getProductId());
        tradeDto.setTakerOrderId(trade.getTakerOrderId());
        tradeDto.setMakerOrderId(trade.getMakerOrderId());
        tradeDto.setStatus(trade.getStatus());
        tradeDto.setSequence(trade.getSequence());
        tradeDto.setTime(trade.getTime());
        tradeDto.setPrice(trade.getPrice());
        tradeDto.setSize(trade.getSize());
        tradeDto.setSide(trade.getSide());
        tradeDto.setTradeId(trade.getId());
        Order makerorder = orderRepository.findByOrderId(trade.getMakerOrderId());
        tradeDto.setMakeruserId(makerorder.getUserId());
        tradeDto.setMakerfunds(String.valueOf(makerorder.getFunds()));
        tradeDto.setMakerfillFees(makerorder.getFillFees() != null ? makerorder.getFillFees().stripTrailingZeros().toPlainString() : "0");
        tradeDto.setMakerfilledSize(String.valueOf(makerorder.getFilledSize()));
        tradeDto.setMakerexecutedValue(String.valueOf(makerorder.getExecutedValue()));
        tradeDto.setMakerstatus(String.valueOf(makerorder.getStatus()));
        Order order = orderRepository.findByOrderId(trade.getTakerOrderId());
        tradeDto.setTakeruserId(order.getUserId());
        tradeDto.setTakerfunds(String.valueOf(order.getFunds()));
        tradeDto.setTakerfillFees(order.getFillFees() != null ? order.getFillFees().stripTrailingZeros().toPlainString() : "0");
        tradeDto.setTakerfilledSize(String.valueOf(order.getFilledSize()));
        tradeDto.setTakerexecutedValue(String.valueOf(makerorder.getExecutedValue()));
        tradeDto.setTakerstatus(String.valueOf(order.getStatus()));
        if (trade.getStatus() == null ){
            tradeDto.setStatus(String.valueOf(order.getStatus()));
        }
        else {
            tradeDto.setStatus(trade.getStatus());
        }
        return tradeDto;
    }


//
//    private TradeDto tradeDto(Trade trade) {
//        TradeDto tradeDto = new TradeDto();
//        tradeDto.setProductId(trade.getProductId());
//        tradeDto.setTakerOrderId(trade.getTakerOrderId());
//        tradeDto.setMakerOrderId(trade.getMakerOrderId());
//        tradeDto.setStatus(trade.getStatus());
//        tradeDto.setSequence(trade.getSequence());
//        tradeDto.setTime(trade.getTime());
//        tradeDto.setPrice(trade.getPrice());
//        tradeDto.setSize(trade.getSize());
//        tradeDto.setSide(trade.getSide());
//        Order order = orderRepository.findByOrderId(trade.getTakerOrderId());
//        tradeDto.setTradeId(order.getId());
//        tradeDto.setTakeruserId(trade.getTakerOrderId());
//        tradeDto.setMakeruserId(trade.getMakerOrderId());
//        tradeDto.setMakerfunds(String.valueOf(trade.getPrice()));
//        tradeDto.setMakerfillFees( order.getFillFees() != null ? order.getFillFees().stripTrailingZeros().toPlainString() : "0");
//        tradeDto.setMakerfilledSize(String.valueOf(order.getFilledSize()));
//        tradeDto.setMakerexecutedValue(String.valueOf(order.getExecutedValue()));
//        tradeDto.setMakerstatus(String.valueOf(order.getStatus()));
//        tradeDto.setTakerfunds(String.valueOf(order.getFunds()));
//        tradeDto.setTakerfillFees(String.valueOf(trade.getPrice()));
//        tradeDto.setTakerfilledSize(String.valueOf(order.getFilledSize()));
//        tradeDto.setTakerexecutedValue(String.valueOf(order.getExecutedValue()));
//        tradeDto.setTakerstatus(String.valueOf(order.getStatus()));
//        if (trade.getStatus() == null ){
//        tradeDto.setStatus(String.valueOf(order.getStatus()));
//        }
//        else {
//            tradeDto.setStatus(trade.getStatus());
//        }
//        return tradeDto;
//    }

    public class tradeStatusResponse {
        private final String status;
        private final String type;
        public tradeStatusResponse(String status, String type) {
            this.status = status;
            this.type = type;
        }
    }



}


