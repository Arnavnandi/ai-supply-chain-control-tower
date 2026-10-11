package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByCompanyIdAndOrderId(Long companyId, Long orderId);
    List<OrderItem> findByCompanyIdAndProductId(Long companyId, Long productId);
    List<OrderItem> findByCompanyIdAndOrderIdAndProductId(Long companyId, Long orderId, Long productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o WHERE oi.company.id = :companyId AND oi.product.id = :productId ORDER BY o.orderDate ASC")
    List<OrderItem> findByProductIdWithOrderDate(@Param("companyId") Long companyId, @Param("productId") Long productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o WHERE oi.company.id = :companyId AND oi.product.id = :productId ORDER BY o.orderDate ASC")
    List<OrderItem> findByCompanyIdAndProductIdWithOrderDate(@Param("companyId") Long companyId, @Param("productId") Long productId);

    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByProductId(Long productId);
    List<OrderItem> findByOrderIdAndProductId(Long orderId, Long productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o WHERE oi.product.id = :productId ORDER BY o.orderDate ASC")
    List<OrderItem> findByProductIdWithOrderDate(@Param("productId") Long productId);
}

