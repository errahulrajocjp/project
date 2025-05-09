// 3. Handle Schedule_interval formatting
if (dagParameter.getScheduleInterval() != null) {
    String scheduleInterval = dagParameter.getScheduleInterval();
    
    // Convert Daily/Weekly/Monthly to "@daily", "@weekly", "@monthly" with quotes
    if ("Daily".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "\"@daily\"");
    } else if ("Weekly".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "\"@weekly\"");
    } else if ("Monthly".equalsIgnoreCase(scheduleInterval)) {
        rootNode.put("Schedule_interval", "\"@monthly\"");
    } else {
        rootNode.put("Schedule_interval", scheduleInterval);
    }
} else {
    rootNode.put("Schedule_interval", "None");
}
