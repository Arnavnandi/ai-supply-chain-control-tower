package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
    Optional<Product> findByCompanyIdAndId(Long companyId, Long id);
    Optional<Product> findByCompanyIdAndSku(Long companyId, String sku);
    List<Product> findByCompanyIdAndCategoryId(Long companyId, Long categoryId);

    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Product> searchProducts(@Param("companyId") Long companyId, @Param("query") String query);

    Optional<Product> findBySku(String sku);
    List<Product> findByCategoryId(Long categoryId);
}

