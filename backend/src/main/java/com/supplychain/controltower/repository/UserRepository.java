package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    List<User> findByCompanyId(Long companyId);
    Optional<User> findByCompanyIdAndId(Long companyId, Long id);
}
