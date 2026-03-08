package com.pdf.jasper.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_templates")
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;           // Human-readable report name (e.g. "Invoice Report")

    @Column(name = "file_name", nullable = false)
    private String fileName;       // Original .jrxml filename

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;       // Absolute path on disk

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ===== Constructors =====
    public ReportTemplate() {}

    public ReportTemplate(String name, String fileName, String filePath) {
        this.name = name;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
