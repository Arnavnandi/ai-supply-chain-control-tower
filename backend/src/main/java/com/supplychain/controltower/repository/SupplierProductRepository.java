package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {
    List<SupplierProduct> findByCompanyId(Long companyId);
    List<SupplierProduct> findByCompanyIdAndProductId(Long companyId, Long productId);
    List<SupplierProduct> findByCompanyIdAndSupplierId(Long companyId, Long supplierId);
    List<SupplierProduct> findByCompanyIdAndSupplierIdAndProductId(Long companyId, Long supplierId, Long productId);

    List<SupplierProduct> findByProductId(Long productId);
    List<SupplierProduct> findBySupplierId(Long supplierId);
    List<SupplierProduct> findBySupplierIdAndProductId(Long supplierId, Long productId);
}
