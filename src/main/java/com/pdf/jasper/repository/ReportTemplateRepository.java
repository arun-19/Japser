package com.pdf.jasper.repository;

import com.pdf.jasper.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    // Support looking up a template dynamically by its name
    java.util.Optional<ReportTemplate> findByNameIgnoreCase(String name);
}
