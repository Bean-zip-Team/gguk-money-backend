package com.ggukmoney.beanzip.domain.legal.repository;

import com.ggukmoney.beanzip.domain.legal.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    Optional<LegalDocument> findByPublicId(UUID publicId);
}
