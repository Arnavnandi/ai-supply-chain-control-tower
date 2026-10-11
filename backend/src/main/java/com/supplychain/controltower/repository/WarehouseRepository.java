package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByCompanyId(Long companyId);
    Optional<Warehouse> findByCompanyIdAndId(Long companyId, Long id);
    Optional<Warehouse> findByCompanyIdAndCode(Long companyId, String code);
    List<Warehouse> findByCompanyIdOrderByUtilizationPercentageDesc(Long companyId);

    Optional<Warehouse> findByCode(String code);
    List<Warehouse> findByOrderByUtilizationPercentageDesc();
}
