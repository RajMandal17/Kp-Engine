package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Getter
@Setter
public class PlaceOrderRequest {

    private String clientOid;

    @NotBlank
    private String productId;

    @NotBlank
    private String size;

    private String funds;

    private String price;

    private String last_trade_id;
    @NotBlank
    private String side;

    @NotBlank
    private String type;
    /**
     * [optional] GTC, GTT, IOC, or FOK (default is GTC)
     */
    private String TimeInForce;



}
