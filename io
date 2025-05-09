We're very close! The issue is in the escape sequences. Let's try a different replacement approach:

```java
@Override
public JsonNode generateDpfJson(String dagId) {
    // ... existing code ...
    
    // Get the final JsonNode
    JsonNode jsonNode = buildDpfJson(dagParameter);
    
    // For the exact Schedule_interval format, we'll need to manipulate the raw JSON string
    try {
        // Convert to string
        String jsonString = mapper.writeValueAsString(jsonNode);
        
        // First handle common patterns with direct replacements 
        jsonString = jsonString.replace("\"Schedule_interval\":\"@daily\"", 
                                      "\"Schedule_interval\":\"/\\\"@daily/\\\"\"");
        jsonString = jsonString.replace("\"Schedule_interval\":\"@weekly\"", 
                                      "\"Schedule_interval\":\"/\\\"@weekly/\\\"\"");
        jsonString = jsonString.replace("\"Schedule_interval\":\"@monthly\"", 
                                      "\"Schedule_interval\":\"/\\\"@monthly/\\\"\"");
        
        // Convert back to JsonNode
        return mapper.readTree(jsonString);
    } catch (Exception e) {
        logger.error("Error in JSON post-processing: " + e.getMessage());
        return jsonNode; // Fallback to original
    }
}

private JsonNode buildDpfJson(DagParameter dagParameter) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode rootNode = mapper.createObjectNode();
    
    // ... Copy all the existing JSON building logic here ...
    
    // For Schedule_interval, just use simple values that we'll replace later
    if (dagParameter.getScheduleInterval() != null) {
        String scheduleInterval = dagParameter.getScheduleInterval();
        
        if ("Daily".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "@daily");
        } else if ("Weekly".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "@weekly");
        } else if ("Monthly".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "@monthly");
        } else {
            rootNode.put("Schedule_interval", scheduleInterval);
        }
    }
    
    // ... rest of the JSON building ...
    
    return rootNode;
}
```

The key change is in the replacement string:
- Instead of `\"\\\"/@daily/\\\"\"` we're using `\"/\\\"@daily/\\\"\"` 
- This format should produce the exact `"/"@daily/""` format you want

If this still doesn't work, we might need to try direct string manipulation with the final JSON right before returning it from the controller.
