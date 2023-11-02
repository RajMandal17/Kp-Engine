package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductDto {
    private String id;
    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal baseMinSize;
    private BigDecimal baseMaxSize;
    private BigDecimal quoteMinSize;
    private BigDecimal quoteMaxSize;
    private float quoteIncrement;
    private int baseScale;
    private int quoteScale;
}
