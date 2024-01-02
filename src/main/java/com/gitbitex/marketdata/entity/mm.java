package com.gitbitex.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
@Getter
@Setter
public class mm {
    private String id;
    private String productId;
    private Date createdAt;
    private Date updatedAt;
    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal baseMinSize;
    private BigDecimal baseMaxSize;
    private BigDecimal quoteMinSize;
    private BigDecimal quoteMaxSize;
    private int baseScale;
    private int quoteScale;
    private float quoteIncrement;

    private BigDecimal orderSizeMin;
    private BigDecimal orderSizeMax;
    private BigDecimal spread;
}
