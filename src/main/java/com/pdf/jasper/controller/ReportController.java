package com.pdf.jasper.controller;

import com.pdf.jasper.entity.ReportTemplate;
import com.pdf.jasper.service.JasperReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/reports", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "*")  // Allow React Native to call this API
public class ReportController {

    @Autowired
    private JasperReportService jasperReportService;

    // =========================================================
    // POST /api/reports/upload
    // Upload a .jrxml template file
    // Form-data: file (MultipartFile), name (String)
    // =========================================================
    @PostMapping("/upload")
    public ResponseEntity<?> uploadTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.toLowerCase().endsWith(".jrxml")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only .jrxml files are accepted"));
            }
            ReportTemplate saved = jasperReportService.uploadTemplate(file, name);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // GET /api/reports/health
    // Health check — open in browser to confirm API is running
    // =========================================================
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Jasper Reports API is running",
                "endpoints", Map.of(
                        "upload",   "POST /api/reports/upload  (multipart: file + name)",
                        "list",     "GET  /api/reports",
                        "view",     "GET  /api/reports/{id}/view?name=John",
                        "download", "GET  /api/reports/{id}/download?name=John",
                        "generate", "POST /api/reports/{id}/generate  (JSON body)"
                )
        ));
    }

    // =========================================================
    // GET /api/reports/upload  ← browser-friendly info page
    // (browsers always send GET; this explains how to POST)
    // =========================================================
    @GetMapping("/upload")
    public ResponseEntity<?> uploadInfo() {
        return ResponseEntity.ok(Map.of(
                "info", "This endpoint only accepts POST requests.",
                "usage", "POST /api/reports/upload",
                "contentType", "multipart/form-data",
                "params", Map.of(
                        "file", "The .jrxml template file",
                        "name", "A human-readable name for the report (e.g. Invoice Report)"
                ),
                "example_curl",
                "curl -X POST http://localhost:8080/api/reports/upload -F \"file=@sample_template.jrxml\" -F \"name=MyReport\""
        ));
    }

    // =========================================================
    // GET /api/reports
    // List all uploaded report templates
    // =========================================================
    @GetMapping
    public ResponseEntity<List<ReportTemplate>> getAllTemplates() {
        return ResponseEntity.ok(jasperReportService.getAllTemplates());
    }

    // =========================================================
    // GET /api/reports/{id}
    // Get a single template by ID
    // =========================================================
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<?> getTemplate(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(jasperReportService.getTemplate(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // POST /api/reports/{id}/generate
    // Generate PDF with parameters (JSON body)
    // Body example: { "name": "John", "company": "ACME" }
    // Returns PDF bytes
    // =========================================================
    @PostMapping(value = "/{id:[0-9]+}/generate", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> generatePdf(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        try {
            if (params == null) params = new HashMap<>();
            byte[] pdfBytes = jasperReportService.generatePdf(id, params);
            HttpHeaders headers = buildPdfHeaders("report_" + id + ".pdf", false);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // GET /api/reports/{id}/parameters
    // Get required parameters for a template
    // =========================================================
    @GetMapping("/{id:[0-9]+}/parameters")
    public ResponseEntity<?> getTemplateParameters(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(jasperReportService.getTemplateParameters(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    // =========================================================
    // GET /api/reports/{id}/download?name=John&company=ACME
    // Download PDF as attachment (saves file on device)
    // =========================================================
    @GetMapping(value = "/{id:[0-9]+}/download", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable("id") Long id,
            @RequestParam Map<String, Object> params) {
        try {
            byte[] pdfBytes = jasperReportService.generatePdf(id, params);
            HttpHeaders headers = buildPdfHeaders("report_" + id + ".pdf", true);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // GET /api/reports/{id}/view?name=John&company=ACME
    // View PDF inline (for React Native PDF viewer)
    // =========================================================
    @GetMapping(value = "/{id:[0-9]+}/view", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> viewPdf(
            @PathVariable("id") Long id,
            @RequestParam Map<String, Object> params) {
        try {
            byte[] pdfBytes = jasperReportService.generatePdf(id, params);
            HttpHeaders headers = buildPdfHeaders("report_" + id + ".pdf", false); // inline
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // PUT /api/reports/{id}
    // Update template name and/or replace the JRXML file
    // Form-data: name (optional), file (optional .jrxml)
    // =========================================================
    @PutMapping(value = "/{id:[0-9]+}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateTemplate(
            @PathVariable("id") Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if ((name == null || name.isBlank()) && (file == null || file.isEmpty())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Provide at least a new name or a new .jrxml file"));
            }
            ReportTemplate updated = jasperReportService.updateTemplate(id, name, file);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // DELETE /api/reports/{id}
    // Delete a template and its file from disk
    // =========================================================
    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<?> deleteTemplate(@PathVariable("id") Long id) {
        try {
            jasperReportService.deleteTemplate(id);
            return ResponseEntity.ok(Map.of("message", "Template deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    // =========================================================
    // POST /api/reports/{name}/generate
    // Generate PDF with parameters (JSON body)
    // =========================================================
    @PostMapping(value = "/{name:[a-zA-Z0-9_\\-]*[a-zA-Z_\\-]+[a-zA-Z0-9_\\-]*}/generate", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> generatePdfByName(
            @PathVariable("name") String name,
            @RequestBody(required = false) Map<String, Object> params) {
        try {
            if (params == null) params = new HashMap<>();
            byte[] pdfBytes = jasperReportService.generatePdfByName(name, params);
            HttpHeaders headers = buildPdfHeaders("report_" + name + ".pdf", false);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // GET /api/reports/{name}/parameters
    // Get required parameters for a template by name
    // =========================================================
    @GetMapping("/{name:[a-zA-Z0-9_\\-]*[a-zA-Z_\\-]+[a-zA-Z0-9_\\-]*}/parameters")
    public ResponseEntity<?> getTemplateParametersByName(@PathVariable("name") String name) {
        try {
            return ResponseEntity.ok(jasperReportService.getTemplateParametersByName(name));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal server error"));
        }
    }

    // =========================================================
    // GET /api/reports/{name}/download?param=1
    // Download PDF as attachment by name
    // =========================================================
    @GetMapping(value = "/{name:[a-zA-Z0-9_\\-]*[a-zA-Z_\\-]+[a-zA-Z0-9_\\-]*}/download", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadPdfByName(
            @PathVariable("name") String name,
            @RequestParam Map<String, Object> params) {
        try {
            byte[] pdfBytes = jasperReportService.generatePdfByName(name, params);
            HttpHeaders headers = buildPdfHeaders("report_" + name + ".pdf", true);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // GET /api/reports/{name}/view?param=1
    // View PDF inline by name
    // =========================================================
    @GetMapping(value = "/{name:[a-zA-Z0-9_\\-]*[a-zA-Z_\\-]+[a-zA-Z0-9_\\-]*}/view", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> viewPdfByName(
            @PathVariable("name") String name,
            @RequestParam Map<String, Object> params) {
        try {
            byte[] pdfBytes = jasperReportService.generatePdfByName(name, params);
            HttpHeaders headers = buildPdfHeaders("report_" + name + ".pdf", false); // inline
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((e.getMessage() != null ? e.getMessage() : "Unknown error").getBytes());
        }
    }

    // =========================================================
    // Helper: Build PDF response headers
    // disposition=true  → attachment (download)
    // disposition=false → inline (view in PDF viewer)
    // =========================================================
    private HttpHeaders buildPdfHeaders(String filename, boolean download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(filename).build()
                : ContentDisposition.inline().filename(filename).build();
        headers.setContentDisposition(disposition);
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Expose-Headers", "Content-Disposition");
        return headers;
    }
}
