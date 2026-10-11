package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
    Optional<Recommendation> findByCompanyIdAndId(Long companyId, Long id);
    List<Recommendation> findByCompanyIdAndStatus(Long companyId, Recommendation.ApprovalStatus status);
    long countByCompanyIdAndStatus(Long companyId, Recommendation.ApprovalStatus status);
    List<Recommendation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Recommendation> findByStatus(Recommendation.ApprovalStatus status);
    long countByStatus(Recommendation.ApprovalStatus status);
    List<Recommendation> findByOrderByCreatedAtDesc();
}
