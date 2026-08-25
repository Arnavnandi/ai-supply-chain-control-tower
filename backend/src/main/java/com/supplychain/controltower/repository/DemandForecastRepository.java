package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {
    List<DemandForecast> findByProductId(Long productId);
    List<DemandForecast> findByProductIdOrderByForecastDateAsc(Long productId);
}
