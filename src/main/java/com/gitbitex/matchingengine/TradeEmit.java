package com.gitbitex.matchingengine;

import com.gitbitex.enums.OrderSide;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class TradeEmit {
    private String tradeEmitId;
    private String productId;
    private long sequence;
    private BigDecimal size;
    private BigDecimal funds;
    private BigDecimal price;
    private Date time;
    private OrderSide side;
    private String takerOrderId;
    private String makerOrderId;
    private String status;
}

