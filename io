// Convert to string
String jsonString = rootNode.toString();

// Use properly escaped strings for replacement
jsonString = jsonString.replace("\"##DAILY_PLACEHOLDER##\"", "\"\\/\\\"@daily\\/\\\"\"");
jsonString = jsonString.replace("\"##WEEKLY_PLACEHOLDER##\"", "\"\\/\\\"@weekly\\/\\\"\"");
jsonString = jsonString.replace("\"##MONTHLY_PLACEHOLDER##\"", "\"\\/\\\"@monthly\\/\\\"\"");
