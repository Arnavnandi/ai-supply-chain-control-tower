package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByCompanyId(Long companyId);
    Optional<CustomerOrder> findByCompanyIdAndId(Long companyId, Long id);
    Optional<CustomerOrder> findByCompanyIdAndOrderNumber(Long companyId, String orderNumber);
    List<CustomerOrder> findByCompanyIdAndStatus(Long companyId, CustomerOrder.OrderStatus status);
    long countByCompanyIdAndStatus(Long companyId, CustomerOrder.OrderStatus status);
    List<CustomerOrder> findByCompanyIdOrderByOrderDateDesc(Long companyId);

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    List<CustomerOrder> findByStatus(CustomerOrder.OrderStatus status);
    List<CustomerOrder> findByOrderByOrderDateDesc();
}
