package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeDto {
    private long sequence;

    private String price;

    private String takeruserId;
    private String makeruserId;
    private String time;
    private String tradeId;
    private String takerOrderId;
    private String makerOrderId;
    private String logOffset;
    private String size;
    private String side;
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
