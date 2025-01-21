package com.icf.ecqm.madie.data.cleaner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParsedLogEntry {
    private final String url;
    private final String fileName;
    private final String resourceType;
    private final String resourceId;

    public ParsedLogEntry(String resourceType, String resourceId, String fileName, String url) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.fileName = fileName;
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public static ParsedLogEntry parseLogEntry(String logLine) {
        if (!logLine.contains("not found, specified in path")) {
            return null;
        }

        // Define the regular expressions
        String filenameRegex = "([^\\s:]+\\.json)";
        String resourceFullRegex = "Resource\\s+(\\w+/[\\w-]+)";
        String urlRegex = "http[s]?://[^\\s]+";

        // Extract filename
        Pattern filenamePattern = Pattern.compile(filenameRegex);
        Matcher filenameMatcher = filenamePattern.matcher(logLine);
        String filename = filenameMatcher.find() ? filenameMatcher.group(1) : null;

        // Extract full resource string
        Pattern resourceFullPattern = Pattern.compile(resourceFullRegex);
        Matcher resourceFullMatcher = resourceFullPattern.matcher(logLine);
        String resourceFull = resourceFullMatcher.find() ? resourceFullMatcher.group(1) : null;

        // Extract URL
        Pattern urlPattern = Pattern.compile(urlRegex);
        Matcher urlMatcher = urlPattern.matcher(logLine);
        String url = urlMatcher.find() ? urlMatcher.group() : null;
        // Clean the URL to remove any trailing colons
        if (url != null && url.endsWith(":")) {
            url = url.substring(0, url.length() - 1); // Remove the trailing colon
        }

        if (resourceFull == null || url == null || filename == null) {
            return null;
        }

        String resourceType = resourceFull.split("/")[0];
        String resourceId = resourceFull.split("/")[1];

        if (resourceType == null || resourceId == null) {
            return null;
        }

        return new ParsedLogEntry(resourceType, resourceId, filename, url);
    }
}
