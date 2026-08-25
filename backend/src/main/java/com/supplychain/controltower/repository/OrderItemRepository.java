package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByProductId(Long productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o WHERE oi.product.id = :productId ORDER BY o.orderDate ASC")
    List<OrderItem> findByProductIdWithOrderDate(@Param("productId") Long productId);
}

