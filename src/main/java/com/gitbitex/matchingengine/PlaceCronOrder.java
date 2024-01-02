package com.gitbitex.matchingengine;
import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
@Getter
@Setter
public class PlaceCronOrder {
    private  String tradeEmitId;
    @NotBlank
    private String productId;

    private BigDecimal volume;
    private BigDecimal funds;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minSize;
    private BigDecimal maxSize;

    private BigDecimal spread;
    private String clientOid;

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
