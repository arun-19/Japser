package com.pdf.jasper.util;

public class JasperCustomUtils {
    /**
     * Custom SUM method exposed to JasperReports
     * Handles variable numbers of Double arguments natively.
     */
    public static Double SUM(Double... values) {
        if (values == null) return 0.0;
        
        double sum = 0.0;
        for (Double v : values) {
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }
    
    /**
     * Fallback for Number types
     */
    public static Double SUM(Number... values) {
        if (values == null) return 0.0;
        
        double sum = 0.0;
        for (Number v : values) {
            if (v != null) {
                sum += v.doubleValue();
            }
        }
        return sum;
    }
}
