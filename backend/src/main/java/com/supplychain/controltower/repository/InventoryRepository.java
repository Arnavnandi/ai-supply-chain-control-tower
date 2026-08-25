package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByProductId(Long productId);
    List<Inventory> findByWarehouseId(Long warehouseId);
    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("SELECT i FROM Inventory i WHERE i.quantityAvailable <= i.reorderLevel")
    List<Inventory> findLowStockInventory();

    @Query("SELECT i FROM Inventory i WHERE i.quantityAvailable > (i.reorderLevel * 3)")
    List<Inventory> findOverstockedInventory();

    @Query("SELECT SUM(i.quantityAvailable * i.product.price) FROM Inventory i")
    Double calculateTotalInventoryValue();
}
