I understand you need the exact format: `"Schedule_interval": "/"@daily/""`

This is a challenging format because it contains unescaped quotes inside a JSON string. Let's try this specific approach:

```java
// 3. Handle Schedule_interval formatting with unique placeholders
if (dagParameter.getScheduleInterval() != null) {
    String scheduleInterval = dagParameter.getScheduleInterval();
    
    // Use unique placeholders that won't appear in regular JSON
    if ("Daily".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "DAILY_PLACEHOLDER_123XYZ");
    } else if ("Weekly".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "WEEKLY_PLACEHOLDER_123XYZ");
    } else if ("Monthly".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "MONTHLY_PLACEHOLDER_123XYZ");
    } else {
        rootNode.put("Schedule_interval", scheduleInterval);
    }
} else {
    rootNode.put("Schedule_interval", "None");
}

// Later, after generating the whole JSON:

// Convert to string for replacements
try {
    String jsonString = mapper.writeValueAsString(rootNode);
    
    // Replace the placeholders with EXACT strings including the special quotes
    // Use the escaped format for Java strings but the replacement will be exact
    jsonString = jsonString.replace("\"DAILY_PLACEHOLDER_123XYZ\"", "\"\\\"/@daily/\\\"\"");
    jsonString = jsonString.replace("\"WEEKLY_PLACEHOLDER_123XYZ\"", "\"\\\"/@weekly/\\\"\"");
    jsonString = jsonString.replace("\"MONTHLY_PLACEHOLDER_123XYZ\"", "\"\\\"/@monthly/\\\"\"");
    
    // Convert back to JsonNode
    return mapper.readTree(jsonString);
} catch (Exception e) {
    logger.error("Error in JSON processing: " + e.getMessage());
    return rootNode; // Fallback to original
}
```

The key is in the replacement strings. Using `"\"/@daily/\""` should give us the exact format you want. Let's try it this way.

If this still doesn't work, we could consider a more drastic approach by modifying the final JSON string right before returning it from the API.

// Replace the placeholders with EXACT strings including the special quotes
jsonString = jsonString.replace("\"DAILY_PLACEHOLDER_123XYZ\"", "\"/\\\"@daily/\\\"\"");
jsonString = jsonString.replace("\"WEEKLY_PLACEHOLDER_123XYZ\"", "\"/\\\"@weekly/\\\"\"");
jsonString = jsonString.replace("\"MONTHLY_PLACEHOLDER_123XYZ\"", "\"/\\\"@monthly/\\\"\"");


