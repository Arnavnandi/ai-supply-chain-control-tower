package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {
    Optional<DocumentMetadata> findByFileName(String fileName);
}
