package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {
    List<DocumentMetadata> findByCompanyId(Long companyId);
    Optional<DocumentMetadata> findByCompanyIdAndId(Long companyId, Long id);
    Optional<DocumentMetadata> findByCompanyIdAndFileName(Long companyId, String fileName);

    Optional<DocumentMetadata> findByFileName(String fileName);
}
