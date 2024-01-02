package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
@Setter
@Getter
public class SaveProductRequest {
    @NotBlank
    private String baseCurrency;
    @NotBlank
    private String quoteCurrency;
}
