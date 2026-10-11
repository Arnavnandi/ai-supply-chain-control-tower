package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByCompanyId(Long companyId);
    Optional<Supplier> findByCompanyIdAndId(Long companyId, Long id);
    Optional<Supplier> findByCompanyIdAndCode(Long companyId, String code);
    List<Supplier> findByCompanyIdOrderByReliabilityScoreDesc(Long companyId);

    Optional<Supplier> findByCode(String code);
    List<Supplier> findByOrderByReliabilityScoreDesc();
}
