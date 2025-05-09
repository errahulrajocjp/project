I see that you're using an ApiResponse wrapper class. Let's update the controller method to use ApiResponse while still handling the special Schedule_interval format:

```java
@GetMapping("/{dagId}/json")
public ResponseEntity<ApiResponse<JsonNode>> getDagAsJson(@PathVariable String dagId) {
    logger.info("Received request to get DAG as JSON for: " + dagId);
    
    try {
        // Generate DPF JSON with placeholders
        JsonNode dpfJson = dagService.generateDpfJson(dagId);
        
        // Convert to string for replacement
        String jsonString = dpfJson.toString();
        
        // Apply the special replacements
        jsonString = jsonString.replace("\"##DAILY_PLACEHOLDER##\"", "\"/@daily/\"");
        jsonString = jsonString.replace("\"##WEEKLY_PLACEHOLDER##\"", "\"/@weekly/\"");
        jsonString = jsonString.replace("\"##MONTHLY_PLACEHOLDER##\"", "\"/@monthly/\"");
        
        // Convert back to JsonNode
        ObjectMapper mapper = new ObjectMapper();
        JsonNode modifiedJson = mapper.readTree(jsonString);
        
        // Return success response with the modified JsonNode
        return ResponseEntity.ok(ApiResponse.success(modifiedJson));
        
    } catch (ResourceNotFoundException e) {
        logger.warn("DAG not found: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    } catch (Exception e) {
        logger.error("Error generating DPF JSON: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred: " + e.getMessage()));
    }
}
```

And in the service method:

```java
@Override
public JsonNode generateDpfJson(String dagId) {
    // ... existing code ...
    
    // 3. Handle Schedule_interval formatting with placeholders
    if (dagParameter.getScheduleInterval() != null) {
        String scheduleInterval = dagParameter.getScheduleInterval();
        
        // Use unique placeholders for replacement
        if ("Daily".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "##DAILY_PLACEHOLDER##");
        } else if ("Weekly".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "##WEEKLY_PLACEHOLDER##");
        } else if ("Monthly".equalsIgnoreCase(scheduleInterval)) {
            rootNode.put("Schedule_interval", "##MONTHLY_PLACEHOLDER##");
        } else {
            rootNode.put("Schedule_interval", scheduleInterval);
        }
    } else {
        rootNode.put("Schedule_interval", "None");
    }
    
    // ... rest of the code ...
    
    return rootNode;
}
```

This approach:
1. Keeps using the ApiResponse wrapper as seen in your codebase
2. Still handles the special format for Schedule_interval
3. Properly handles errors using the existing patterns
4. Maintains the content type as JSON
