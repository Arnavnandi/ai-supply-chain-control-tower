package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    List<CustomerOrder> findByStatus(CustomerOrder.OrderStatus status);
    List<CustomerOrder> findByOrderByOrderDateDesc();
}
