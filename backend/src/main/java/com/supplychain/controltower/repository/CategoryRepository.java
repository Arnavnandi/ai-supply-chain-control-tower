package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByCompanyId(Long companyId);
    Optional<Category> findByCompanyIdAndId(Long companyId, Long id);
    Optional<Category> findByCompanyIdAndName(Long companyId, String name);
    Optional<Category> findByName(String name);
}
