package com.gitbitex.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
@RestController
@RequestMapping("/init-controller")
public class InitController {
    private final CoinbaseTrader coinbaseTrader;

    @Autowired
    public InitController(CoinbaseTrader coinbaseTrader) {
        this.coinbaseTrader = coinbaseTrader;
    }

    @PostMapping(value = "/start-init/{timeInSec}")
    public ResponseEntity<String> startInit(@PathVariable long timeInSec) {

        coinbaseTrader.startInit(timeInSec);
        return ResponseEntity.ok("Initialization started");
    }

    @PostMapping("/stop-init")
    public ResponseEntity<String> stopInit() {
        coinbaseTrader.stopInit();
        return ResponseEntity.ok("Initialization stopped");
    }
}
