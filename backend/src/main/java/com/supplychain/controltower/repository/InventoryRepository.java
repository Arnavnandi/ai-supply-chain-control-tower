package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByCompanyId(Long companyId);
    Optional<Inventory> findByCompanyIdAndId(Long companyId, Long id);
    List<Inventory> findByCompanyIdAndProductId(Long companyId, Long productId);
    List<Inventory> findByCompanyIdAndWarehouseId(Long companyId, Long warehouseId);
    Optional<Inventory> findByCompanyIdAndProductIdAndWarehouseId(Long companyId, Long productId, Long warehouseId);

    @Query("SELECT i FROM Inventory i WHERE i.company.id = :companyId AND i.quantityAvailable <= i.reorderLevel")
    List<Inventory> findLowStockInventory(@Param("companyId") Long companyId);

    @Query("SELECT i FROM Inventory i WHERE i.company.id = :companyId AND i.quantityAvailable > (i.reorderLevel * 3)")
    List<Inventory> findOverstockedInventory(@Param("companyId") Long companyId);

    @Query("SELECT SUM(i.quantityAvailable * i.product.price) FROM Inventory i WHERE i.company.id = :companyId")
    Double calculateTotalInventoryValue(@Param("companyId") Long companyId);

    List<Inventory> findByProductId(Long productId);
    List<Inventory> findByWarehouseId(Long warehouseId);
    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
}

