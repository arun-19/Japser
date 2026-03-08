package com.pdf.jasper.service;

import com.pdf.jasper.entity.ReportTemplate;
import com.pdf.jasper.repository.ReportTemplateRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JasperReportService {

    @Value("${jasper.templates.dir}")
    private String templatesDir;

    @Autowired
    private ReportTemplateRepository reportTemplateRepository;

    @Autowired
    private DataSource dataSource;

    // =====================================================================
    // 1. Upload JRXML file
    // =====================================================================
    public ReportTemplate uploadTemplate(MultipartFile file, String reportName) throws IOException {
        // Create the uploads/templates directory if it doesn't exist
        Path dirPath = Paths.get(templatesDir).toAbsolutePath();
        Files.createDirectories(dirPath);

        // Save the file
        String originalFileName = file.getOriginalFilename();
        Path filePath = dirPath.resolve(originalFileName);
        file.transferTo(filePath.toFile());

        // Save metadata to MySQL
        ReportTemplate template = new ReportTemplate(reportName, originalFileName, filePath.toString());
        return reportTemplateRepository.save(template);
    }

    // =====================================================================
    // 2. List all templates
    // =====================================================================
    public List<ReportTemplate> getAllTemplates() {
        return reportTemplateRepository.findAll();
    }

    // =====================================================================
    // 3. Get single template by ID
    // =====================================================================
    public ReportTemplate getTemplate(Long id) {
        return reportTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Report template not found with id: " + id));
    }

    // =====================================================================
    // 3b. Get single template by Name
    // =====================================================================
    public ReportTemplate getTemplateByName(String name) {
        return reportTemplateRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new RuntimeException("Report template not found with name: " + name));
    }

    // =====================================================================
    // 4. Generate PDF — returns PDF bytes
    //    Parameters map can contain: "name", "date", or any custom key
    // =====================================================================
    public byte[] generatePdf(Long templateId, Map<String, Object> params) throws Exception {
        ReportTemplate template = getTemplate(templateId);
        return generatePdfFromTemplate(template, params);
    }

    public byte[] generatePdfByName(String name, Map<String, Object> params) throws Exception {
        ReportTemplate template = getTemplateByName(name);
        return generatePdfFromTemplate(template, params);
    }

    private byte[] generatePdfFromTemplate(ReportTemplate template, Map<String, Object> params) throws Exception {
        File jrxmlFile = new File(template.getFilePath());

        if (!jrxmlFile.exists()) {
            throw new RuntimeException("JRXML file not found on disk: " + template.getFilePath());
        }

        // Load design, inject custom import for SUM(), and compile
        JasperDesign design = JRXmlLoader.load(jrxmlFile.getAbsolutePath());
        design.addImport("static com.pdf.jasper.util.JasperCustomUtils.SUM");
        JasperReport jasperReport = JasperCompileManager.compileReport(design);

        // Get JRXML expected parameter types
        JRParameter[] jrParameters = jasperReport.getParameters();
        Map<String, Class<?>> paramTypes = new HashMap<>();
        if (jrParameters != null) {
            for (JRParameter p : jrParameters) {
                if (!p.isSystemDefined()) {
                    paramTypes.put(p.getName(), p.getValueClass());
                }
            }
        }

        // Build parameter map and auto-convert types
        Map<String, Object> reportParams = new HashMap<>();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                Class<?> expectedType = paramTypes.get(key);
                
                if (val instanceof String && expectedType != null && expectedType != String.class) {
                    try {
                        String s = ((String) val).trim();
                        String typeName = expectedType.getName();
                        
                        if (typeName.equals("java.lang.Integer") || typeName.equals("int")) {
                            val = Integer.parseInt(s);
                        } else if (typeName.equals("java.lang.Long") || typeName.equals("long")) {
                            val = Long.parseLong(s);
                        } else if (typeName.equals("java.lang.Double") || typeName.equals("double")) {
                            val = Double.parseDouble(s);
                        } else if (typeName.equals("java.lang.Float") || typeName.equals("float")) {
                            val = Float.parseFloat(s);
                        } else if (typeName.equals("java.lang.Boolean") || typeName.equals("boolean")) {
                            val = Boolean.parseBoolean(s);
                        }
                    } catch (Exception ignored) {
                        // If parsing fails, pass original String val
                    }
                }
                reportParams.put(key, val);
            }
        }

        // Fill the report using a MySQL JDBC connection
        JasperPrint jasperPrint;
        try (Connection connection = dataSource.getConnection()) {
            jasperPrint = JasperFillManager.fillReport(jasperReport, reportParams, connection);
        }

        // Export to PDF bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
        SimplePdfExporterConfiguration config = new SimplePdfExporterConfiguration();
        exporter.setConfiguration(config);
        exporter.exportReport();

        return outputStream.toByteArray();
    }

    // =====================================================================
    // 5. Get expected parameters for a template
    //    Returns a map of ParameterName -> ParameterClass (e.g. "fk" -> "java.lang.Integer")
    // =====================================================================
    public Map<String, String> getTemplateParameters(Long templateId) throws Exception {
        ReportTemplate template = getTemplate(templateId);
        return getTemplateParametersFromTemplate(template);
    }

    public Map<String, String> getTemplateParametersByName(String name) throws Exception {
        ReportTemplate template = getTemplateByName(name);
        return getTemplateParametersFromTemplate(template);
    }

    private Map<String, String> getTemplateParametersFromTemplate(ReportTemplate template) throws Exception {
        File jrxmlFile = new File(template.getFilePath());

        if (!jrxmlFile.exists()) {
            throw new RuntimeException("JRXML file not found on disk: " + template.getFilePath());
        }

        JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlFile.getAbsolutePath());
        JRParameter[] jrParameters = jasperReport.getParameters();
        
        Map<String, String> params = new HashMap<>();
        if (jrParameters != null) {
            for (JRParameter p : jrParameters) {
                // Ignore built-in Jasper system parameters
                if (!p.isSystemDefined()) {
                    params.put(p.getName(), p.getValueClassName());
                }
            }
        }
        return params;
    }

    // =====================================================================
    // 5. Update a template — rename and/or replace the JRXML file
    // =====================================================================
    public ReportTemplate updateTemplate(Long id, String newName, MultipartFile newFile) throws IOException {
        ReportTemplate template = getTemplate(id);

        // Update name if provided
        if (newName != null && !newName.isBlank()) {
            template.setName(newName);
        }

        // Replace JRXML file on disk if a new file was supplied
        if (newFile != null && !newFile.isEmpty()) {
            String originalFileName = newFile.getOriginalFilename();
            if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".jrxml")) {
                throw new RuntimeException("Only .jrxml files are accepted");
            }
            // Delete old file
            File oldFile = new File(template.getFilePath());
            if (oldFile.exists()) oldFile.delete();

            // Save new file
            Path dirPath = Paths.get(templatesDir).toAbsolutePath();
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(originalFileName);
            newFile.transferTo(filePath.toFile());

            template.setFileName(originalFileName);
            template.setFilePath(filePath.toString());
        }

        return reportTemplateRepository.save(template);
    }

    // =====================================================================
    // 6. Delete a template (and its file from disk)
    // =====================================================================
    public void deleteTemplate(Long id) throws IOException {
        ReportTemplate template = getTemplate(id);
        File file = new File(template.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        reportTemplateRepository.deleteById(id);
    }

}
