package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.service.ForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Slf4j
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping("/{productId}")
    public ResponseEntity<DemandForecastingEngine.ForecastResult> getForecastForProduct(@PathVariable Long productId) {
        log.info("[DEMAND FORECAST REQUEST] Calculating forecast for productId={}", productId);
        return ResponseEntity.ok(forecastService.getDemandForecast(productId));
    }
}

