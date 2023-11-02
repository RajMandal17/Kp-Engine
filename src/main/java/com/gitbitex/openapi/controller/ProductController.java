package com.gitbitex.openapi.controller;

import com.gitbitex.enums.OrderSide;
import com.gitbitex.enums.OrderType;
import com.gitbitex.enums.TimeInForce;
import com.gitbitex.feed.message.OrderFeedMessage;
import com.gitbitex.marketdata.entity.Candle;
import com.gitbitex.marketdata.entity.Product;
import com.gitbitex.marketdata.entity.Trade;
import com.gitbitex.marketdata.repository.CandleRepository;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController()
@RequiredArgsConstructor
public class ProductController {
    private final OrderBookSnapshotStore orderBookSnapshotStore;
    private final ProductRepository productRepository;
    private final TradeRepository tradeRepository;
    private final CandleRepository candleRepository;
    private final MatchingEngineCommandProducer producer;

    @GetMapping("/api/products")
    public List<ProductDto> getProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::productDto).collect(Collectors.toList());
    }
    @PostMapping("/api/addproducts")
    public Product addProduct(@RequestBody ProductDto request) {
        Product product = new Product();
        String productId = request.getId();
        product.setId(productId);
        // Set createdAt and updatedAt to the current date and time
        Date currentDate = new Date();
        product.setCreatedAt(currentDate);
        product.setUpdatedAt(currentDate);
        // Set other properties
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
        PutProductCommand putProductCommand = new PutProductCommand();
        putProductCommand.setProductId(product.getId());
        putProductCommand.setBaseCurrency(product.getBaseCurrency());
        putProductCommand.setQuoteCurrency(product.getQuoteCurrency());
        producer.send(putProductCommand, null);

        return product;
    }




@GetMapping("/api/products/{productId}/trades")
    public List<TradeDto> getProductTrades(@PathVariable String productId) {
        List<Trade> trades = tradeRepository.findByProductId(productId, 50);

        List<TradeDto> tradeDtos = new ArrayList<TradeDto>();
        for (Trade trade : trades) {

            tradeDtos.add(tradeDto(trade));
        }

        return tradeDtos;
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
        var message = new OrderMatchMessage();
        tradeDto.setSequence(trade.getSequence());
        tradeDto.setTime(trade.getTime().toInstant().toString());
        tradeDto.setPrice(trade.getPrice().toPlainString());
        tradeDto.setSize(trade.getSize().toPlainString());
        tradeDto.setSide(trade.getSide().name().toLowerCase());
      //  tradeDto.setTakeruserId();
        return tradeDto;
    }
}
