/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation 
 *
 * See the NOTICE file(s) distributed with this work for additional 
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for runtime placeholder substitution in HTTP templates.
 * Handles {placeholder} syntax for dynamic values like jobId, fileName, etc.
 * <p>
 * Note: ${VAR} environment variables are resolved by Spring when loading
 * application.yml, so they're already substituted before reaching this class.
 */
@Component
public class HttpTemplateEngine {
    
    // Pattern to match {placeholder} placeholders (for jobId, fileName, etc.)
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    /**
     * Process a template by substituting placeholders.
     * <p>
     * Example: "https://api.example.com/jobs/{jobId}"
     * becomes: "https://api.example.com/jobs/12345"
     */
    public String process(String template, Map<String, String> placeholders) {
        if (template == null) {
            return null;
        }
        
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String placeholderValue = placeholders.get(placeholderName);
            
            if (placeholderValue == null) {
                throw new IllegalArgumentException("Placeholder not found: " + placeholderName);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(placeholderValue));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process a template with no placeholders (returns as-is).
     */
    public String process(String template) {
        return template;
    }
    
    /**
     * Process all values in a map (for headers, query params, etc.).
     */
    public Map<String, String> processMap(Map<String, String> map, Map<String, String> placeholders) {
        if (map == null) {
            return new HashMap<>();
        }
        
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result.put(entry.getKey(), process(entry.getValue(), placeholders));
        }
        
        return result;
    }
}

