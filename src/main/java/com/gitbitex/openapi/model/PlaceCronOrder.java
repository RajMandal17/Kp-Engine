
package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Getter
@Setter
public class PlaceCronOrder {


    @NotBlank
    private String productId;

    @NotBlank
    private String volume;

    private String funds;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private BigDecimal minSize;

    private BigDecimal maxSize;

    @NotBlank
    private BigDecimal spread;


    private String clientOid;


    @NotBlank
    private BigDecimal size;


    private BigDecimal price;

    private String last_trade_id;
    @NotBlank
    private String side;

    @NotBlank
    private String type;
    /**
     * [optional] GTC, GTT, IOC, or FOK (default is GTC)
     */
    private String TimeInForce;
    /**
     * [optional] GTC, GTT, IOC, or FOK (default is GTC)
     */

}
