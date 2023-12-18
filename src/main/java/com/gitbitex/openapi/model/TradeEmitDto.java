
package com.gitbitex.openapi.model;

import com.gitbitex.enums.OrderSide;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class TradeEmitDto {


    private long sequence;
    private String productId;
    private String takerOrderId;
    private String makerOrderId;
    private BigDecimal price;
    private BigDecimal size;
    private OrderSide side;
    private Date time;
    private String status;
    private String takeruserId;
    private String makeruserId;
    private String tradeId;
    private String makerfunds;
    private String makerfillFees;
    private String makerfilledSize;
    private String makerexecutedValue;
    private String makerstatus;
    private String takerfunds;
    private String takerfillFees;
    private String takerfilledSize;
    private String takerexecutedValue;
    private String takerstatus;



}
