package com.icf.ecqm.madie.data.cleaner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    enum resourceType {
        CONDITION,
        PRACTITIONER,
        ENCOUNTER,
        LOCATION,
        ORGANIZATION
    }

    private static int totalFiles = 0;
    private static int processedFiles = 0;

    private static String phase = "";

    private static final String[] entryResourceIDSubArrays = {"diagnosis.condition"};
    private static final String[] entryResourceIDArrays = {"payor", "evaluatedResource", "partOf"};
    private static final String[] entryResourceIDStrings = {"subject", "beneficiary", "requester", "encounter"};


    private static final StringBuilder log = new StringBuilder();


    private static final Map<String, ParsedLogEntry> dummyEntryMap = new HashMap<>();

    public static File getMostRecentLogFile(String directoryPath) {
        File dir = new File(directoryPath);

        // Validate that the provided path is a directory
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("The provided path is not a directory.");
        }

        // Find the most recent log file based on the timestamp in the filename
        return Arrays.stream(Objects.requireNonNull(dir.listFiles((dir1, name) -> name.startsWith("http_post_fail_") && name.endsWith(".log"))))
                .max(Comparator.comparingLong(Main::extractTimestampFromFilename))
                .orElse(null);
    }

    private static long extractTimestampFromFilename(File file) {
        String filename = file.getName();

        // Regex to extract the timestamp from the filename
        Pattern pattern = Pattern.compile("http_post_fail_(\\d{14})\\.log");
        Matcher matcher = pattern.matcher(filename);

        if (matcher.matches()) {
            // Parse and return the timestamp as a long
            return Long.parseLong(matcher.group(1));
        }

        return 0; // Default to 0 if no match is found
    }

    public static ParsedLogEntry parseLogEntry(String logLine) {
        if (!logLine.contains("not found, specified in path")){
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


    // Inner class to represent a parsed log entry
    public static class ParsedLogEntry {

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

    }

    public static void main(String[] args) throws IOException {


        Path currentDir = Paths.get("").toAbsolutePath();


        //fist, check for latest http log, attempt to add dummy entries to files claiming they are missing:

        try {
            File mostRecentFile = getMostRecentLogFile(currentDir.toString());

            if (mostRecentFile != null) {
                System.out.println("Most recent file: " + mostRecentFile.getName());
                List<String> lines = Files.readAllLines(mostRecentFile.toPath());

                for (String line : lines) {
                    ParsedLogEntry entry = parseLogEntry(line);

                    if (entry != null) {
                        if (!dummyEntryMap.containsKey(entry.fileName)) {
                            dummyEntryMap.put(entry.fileName, entry);
                        }
                    }
                }
            } else {
                System.out.println("No log files found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        List<Path> jsonFiles = Files.walk(currentDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());

        totalFiles = jsonFiles.size();
        Queue<Path> fileQueue = new LinkedList<>(jsonFiles);
        Set<Path> processedFileSet = new HashSet<>();
        Map<String, Path> resourceIdToFileMap = new HashMap<>();

        phase = "Mapping files";
        for (Path file : jsonFiles) {
            try {
                String content = Files.readString(file);
                JSONObject jsonObject = new JSONObject(content);
                updateResourceIdMap(jsonObject, resourceIdToFileMap, file);
                processedFiles++;
                reportProgress();
            } catch (Exception e) {
                System.err.println("Failed to map: " + file);
                e.printStackTrace();
            }
        }
        processedFiles = 0;

        phase = "Writing new IDs to references";
        while (!fileQueue.isEmpty()) {
            Path jsonFile = fileQueue.poll();
            if (!processedFileSet.contains(jsonFile)) {
                processFile(jsonFile, fileQueue, processedFileSet, resourceIdToFileMap);
                processedFiles++;
                reportProgress();
            }
        }

        if (log.length() > 0) {
            System.out.println(log);
            System.out.println("\n\r\n\rProcess complete.");

        }
    }

    private static void processFile(Path jsonFile, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap) {
        try {
            //parse json content, remove external references forcefully where any part of the string that looks like '"reference": "https://madie.cms.gov/':
            String content = Files.readString(jsonFile)
                    .replace("\"reference\": \"https://madie.cms.gov/", "\"reference\": \"")
                    .replace("\"reference\":\"https://madie.cms.gov/", "\"reference\":\"")
                    .replace("http://myGoodHealthcare.com/fhir/", "")
                    .replace("http://GoodHealthcare.com/fhir/", "")
                    .replace("Practitioner/example", "Practitioner/practitioner-123456")
                    .replace("Practitioner/practitioner-123456", "Practitioner/Practitioner-123456");
            JSONObject jsonObject = new JSONObject(content);

            // Update the 'id' fields in 'resource'
            updateResourceId(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap, jsonFile);

            // Update references in 'evaluatedResource'
            updateReferences(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap, jsonFile);

            // Save the modified JSON
            Files.writeString(jsonFile, jsonObject.toString(4));

            processedFileSet.add(jsonFile);
        } catch (Exception e) {
            System.err.println("Failed to process file: " + jsonFile);
            e.printStackTrace();
        }
    }

    private static void updateResourceId(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap, Path jsonFile) {
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource")) {
                    JSONObject resource = entry.getJSONObject("resource");

                    if (resource.has("id") && resource.has("resourceType")) {

                        String id = resource.getString("id");
                        String resourceType = resource.getString("resourceType");

                        if (isNumeric(id)) {
                            String newId = resourceType + "-" + id;
                            resource.put("id", newId);

                            // Update resource ID to file mapping
                            Path file = resourceIdToFileMap.get(id);
                            if (file != null) {
                                resourceIdToFileMap.remove(id);
                                resourceIdToFileMap.put(newId, file);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void updateReferences(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap, Path jsonFile) {

        StringBuilder updatedEntriesLogger = new StringBuilder();

        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);

                //evaluatedResource etc
                for (String entryResourceID : entryResourceIDArrays) {
                    if (entry.has("resource") && entry.getJSONObject("resource").has(entryResourceID)) {

                        JSONArray resourcesArray = entry.getJSONObject("resource").getJSONArray(entryResourceID);

                        for (int j = 0; j < resourcesArray.length(); j++) {
                            JSONObject entryIDResource = resourcesArray.getJSONObject(j);
                            if (entryIDResource.has("reference")) {
                                String reference = entryIDResource.getString("reference");
                                String[] parts = reference.split("/");
                                if (parts.length == 2 && isNumeric(parts[1])) {

                                    //strip external reference, align the id with hapi-suitable rules:
                                    String newReference = parts[0] + "/" + parts[0] + "-" + parts[1];

                                    entryIDResource.put("reference", newReference);

                                    // Enqueue file for re-processing if needed
                                    Path referencedFile = resourceIdToFileMap.get(parts[1]);
                                    if (updatedEntriesLogger.length() == 0) {
                                        updatedEntriesLogger.append("\n\r\n\r").append(jsonFile.getFileName()).append(":");
                                    }
                                    updatedEntriesLogger.append("\n\rUpdated ").append(entryResourceID).append(" ID: ").append(reference).append(" to ").append(newReference);

                                    if (referencedFile != null && !processedFileSet.contains(referencedFile)) {
                                        fileQueue.add(referencedFile);
                                    }
                                }
                            }
                        }
                    }
                }

                //diagnosis.condition etc
                for (String entrySub : entryResourceIDSubArrays) {
                    String[] theseParts = entrySub.split("\\.");
                    String arrayName = theseParts[0];
                    String subEntry = theseParts[1];

                    if (entry.has("resource") && entry.getJSONObject("resource").has(arrayName)) {

                        JSONArray resourceArray = entry.getJSONObject("resource").getJSONArray(arrayName);

                        for (int j = 0; j < resourceArray.length(); j++) {
                            JSONObject entryIDResource = resourceArray.getJSONObject(j);

                            if (entryIDResource.has(arrayName)) {

                                JSONArray subArrayResource = entryIDResource.getJSONArray(arrayName);

                                for (int k = 0; k < subArrayResource.length(); k++) {

                                    JSONObject subArrayEntry = subArrayResource.getJSONObject(k);
                                    if (subArrayEntry.has(subEntry)) {
                                        String reference = subArrayEntry.getString(subEntry);
                                        String[] parts = reference.split("/");
                                        if (parts.length == 2 && isNumeric(parts[1])) {

                                            //strip external reference, align the id with hapi-suitable rules:
                                            String newReference = parts[0] + "/" + parts[0] + "-" + parts[1];

                                            subArrayEntry.put(subEntry, newReference);

                                            Path referencedFile = resourceIdToFileMap.get(parts[1]);

                                            if (updatedEntriesLogger.length() == 0) {
                                                updatedEntriesLogger.append("\n\r\n\r").append(jsonFile.getFileName()).append(":");
                                            }
                                            updatedEntriesLogger.append("\n\rUpdated ").append(entrySub).append(" ID: ").append(reference).append(" to ").append(newReference);

                                            if (referencedFile != null && !processedFileSet.contains(referencedFile)) {
                                                fileQueue.add(referencedFile);
                                            }
                                        }
                                    }


                                }

                            }
                        }
                    }
                }

                //subject etc
                for (String entryResourceID : entryResourceIDStrings) {
                    if (entry.has("resource") && entry.getJSONObject("resource").has(entryResourceID)) {

                        JSONObject resourceObject = entry.getJSONObject("resource").getJSONObject(entryResourceID);

                        if (resourceObject.has("reference")) {
                            String reference = resourceObject.getString("reference");
                            String[] parts = reference.split("/");
                            if (parts.length == 2 && isNumeric(parts[1])) {
                                //strip external reference, align the id with hapi-suitable rules:
                                String newReference = parts[0] + "/" + parts[0] + "-" + parts[1];
                                resourceObject.put("reference", newReference);

                                // Enqueue file for re-processing if needed
                                Path referencedFile = resourceIdToFileMap.get(parts[1]);

                                if (updatedEntriesLogger.length() == 0) {
                                    updatedEntriesLogger.append("\n\r\n\r").append(jsonFile.getFileName()).append(":");
                                }
                                updatedEntriesLogger.append("\n\rUpdated ").append(entryResourceID).append(" ID: ").append(reference).append(" to ").append(newReference);

                                if (referencedFile != null && !processedFileSet.contains(referencedFile)) {
                                    fileQueue.add(referencedFile);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (updatedEntriesLogger.length() > 0) {
            log.append(updatedEntriesLogger);
        }
    }

    private static void updateResourceIdMap(JSONObject jsonObject, Map<String, Path> resourceIdToFileMap, Path file) throws IOException {
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            List<String> resourceIds = new ArrayList<>();

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource")) {
                    JSONObject resource = entry.getJSONObject("resource");
                    if (resource.has("id")) {
                        String id = resource.getString("id");

                        String recordedId = id;
                        if (isNumeric(id) && resource.has("resourceType")){
                            recordedId = resource.getString("resourceType") + "-" + id;
                        }

                        //avoid duplicates injected via dummy entry:
                        resourceIds.add(recordedId);
                        resourceIdToFileMap.put(id, file);
                    }
                }
            }

            //place "dummy resource" if previous httplog indicates it's missing:
            String fileNameKey = file.getFileName().toString();
            if (dummyEntryMap.containsKey(fileNameKey)) {
                ParsedLogEntry ple = dummyEntryMap.get(fileNameKey);

                String resourceID = ple.resourceId;
                if (resourceIds.contains(resourceID)){
                    return;
                }
                String resourceType = ple.resourceType;

                JSONObject resource = new JSONObject();
                JSONObject newResource = null;

                if (resourceType.equalsIgnoreCase(("Condition"))) {
                    newResource = getConditionObject(resourceID);
                } else if (resourceType.equalsIgnoreCase(("Practitioner"))) {
                    newResource = getPractitionerObject(resourceID);
                } else if (resourceType.equalsIgnoreCase(("Encounter"))) {
                    newResource = getEncounterObject(resourceID);
                } else if (resourceType.equalsIgnoreCase(("Location"))) {
                    newResource = getLocationObject(resourceID);
                } else if (resourceType.equalsIgnoreCase(("Organization"))) {
                    newResource = getOrganizationObject(resourceID);
                }

                if (newResource != null) {
                    resource.put("resource", newResource);
                    resource.put("fullUrl", "https://madie.cms.gov/" + resourceType + "/" + resourceID);
                    resource.put("request", new JSONObject()
                            .put("method", "PUT")
                            .put("url", resourceType + "/" + resourceID));


                    entries.put(resource);
                    jsonObject.put("entry", sortResources(entries));


                    String type = null;
                    String jsonResourceType = null;
                    if (jsonObject.has("type")){
                        type = jsonObject.getString("type");
                    }
                    if (jsonObject.has("resourceType")){
                        jsonResourceType = jsonObject.getString("resourceType");
                    }

                    String url = null;

                    if (type != null && type.equalsIgnoreCase("transaction")){
                        url = ple.url;
                    }else{
                        if (ple.url.endsWith("/")){
                            url = ple.url + jsonResourceType;
                        }else{
                            url = ple.url + "/" + jsonResourceType;
                        }
                    }

                    //POST this modified file to the url it failed with prior:
                    if (url == null) return;

                    // Create an HttpClient
                    HttpClient httpClient = HttpClient.newHttpClient();

                    try {
                        // Build the HttpRequest
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Content-Type", "application/fhir+json")
                                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                                .build();

                        // Send the request and get the response
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        // log the response
                        log.append("\n\r\n\rUploaded modified resource with dummy entries to: ").append(url).append("\n\r\tResponse:").append(response.body());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Files.writeString(file, jsonObject.toString(4));

                }

            }
        }
    }

    public static JSONArray sortResources(JSONArray entry) {
        // Define resource types in dependency order
        List<String> dependencyOrder = Arrays.asList(
                "Organization",
                "Practitioner",
                "Location",
                "Patient",
                "Encounter",
                "Condition",
                "Observation",
                "DiagnosticReport",
                "Procedure",
                "CarePlan"
        );

        // Create a map to hold resources grouped by type
        Map<String, List<JSONObject>> resourceMap = new HashMap<>();
        for (String type : dependencyOrder) {
            resourceMap.put(type, new ArrayList<>());
        }

        // Group resources by type
        for (int i = 0; i < entry.length(); i++) {
            JSONObject resourceEntry = entry.getJSONObject(i);
            JSONObject resource = resourceEntry.getJSONObject("resource");
            String resourceType = resource.getString("resourceType");
            resourceMap.getOrDefault(resourceType, new ArrayList<>()).add(resourceEntry);
        }

        // Construct the sorted JSONArray
        JSONArray sortedEntry = new JSONArray();
        for (String type : dependencyOrder) {
            List<JSONObject> resources = resourceMap.get(type);
            if (resources != null) {
                for (JSONObject resource : resources) {
                    sortedEntry.put(resource);
                }
            }
        }

        // Add any remaining resources not explicitly listed in dependencyOrder
        for (int i = 0; i < entry.length(); i++) {
            JSONObject resourceEntry = entry.getJSONObject(i);
            JSONObject resource = resourceEntry.getJSONObject("resource");
            String resourceType = resource.getString("resourceType");
            if (!dependencyOrder.contains(resourceType)) {
                sortedEntry.put(resourceEntry);
            }
        }

        return sortedEntry;
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private static void reportProgress() {
        double percentage = (double) processedFiles / totalFiles * 100;
        System.out.print("\r" + phase + ": " + String.format("%.2f%%", percentage) + " processed.");
    }


    private static JSONObject getConditionObject(String id) {
        return new JSONObject()
                .put("resourceType", "Condition")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-condition")))
                .put("clinicalStatus", new JSONObject()
                        .put("coding", new JSONArray()
                                .put(new JSONObject()
                                        .put("system", "http://terminology.hl7.org/CodeSystem/condition-clinical")
                                        .put("code", "active")
                                        .put("display", "active")
                                        .put("userSelected", true)))
                )
                .put("category", new JSONArray()
                        .put(new JSONObject()
                                .put("coding", new JSONArray()
                                        .put(new JSONObject()
                                                .put("system", "http://terminology.hl7.org/CodeSystem/condition-clinical")
                                                .put("code", "active")
                                                .put("display", "active")
                                                .put("userSelected", true))))
                )
                .put("code", new JSONObject()
                        .put("coding", new JSONArray()
                                .put(new JSONObject()
                                        .put("system", "http://snomed.info/sct")
                                        .put("version", "http://snomed.info/sct/731000124108/version/202303")
                                        .put("code", "10349009")
                                        .put("display", "Multi-infarct dementia with delirium (disorder)")
                                        .put("userSelected", true)))
                )
                .put("subject", new JSONObject()
                        .put("reference", "Patient/97b034d3-87b0-4e89-b672-58389f8ebcb3"))
                .put("onsetDateTime", "2025-08-06T08:00:00.000+00:00")
                .put("recordedDate", "2025-08-09T08:00:00.000+00:00");
    }

    private static JSONObject getPractitionerObject(String id) {
        return new JSONObject()
                .put("resourceType", "Practitioner")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-practitioner")))
                .put("identifier", new JSONArray()
                        .put(new JSONObject()
                                .put("use", "temp")
                                .put("system", "urn:oid:2.16.840.1.113883.4.336")
                                .put("value", "Practitioner-23")))
                .put("active", true)
                .put("name", new JSONArray()
                        .put(new JSONObject()
                                .put("family", "Careful")
                                .put("given", new JSONArray()
                                        .put("Adam"))
                                .put("prefix", new JSONArray()
                                        .put("Dr")))
                )
                .put("address", new JSONArray()
                        .put(new JSONObject()
                                .put("use", "home")
                                .put("line", new JSONArray()
                                        .put("534 Erewhon St"))
                                .put("city", "PleasantVille")
                                .put("state", "UT")
                                .put("postalCode", "84414"))
                )
                .put("qualification", new JSONArray()
                        .put(new JSONObject()
                                .put("identifier", new JSONArray()
                                        .put(new JSONObject()
                                                .put("system", "http://example.org/UniversityIdentifier")
                                                .put("value", "12345")))
                                .put("code", new JSONObject()
                                        .put("coding", new JSONArray()
                                                .put(new JSONObject()
                                                        .put("system", "http://terminology.hl7.org/CodeSystem/v2-0360|2.7")
                                                        .put("code", "BS")
                                                        .put("display", "Bachelor of Science")))
                                        .put("text", "Bachelor of Science"))
                                .put("period", new JSONObject()
                                        .put("start", "1995"))
                                .put("issuer", new JSONObject()
                                        .put("display", "Example University"))
                        )
                );
    }


    private static JSONObject getEncounterObject(String id) {
        return new JSONObject()
                .put("resourceType", "Encounter")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-encounter")))
                .put("status", "finished")
                .put("class", new JSONObject()
                        .put("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode")
                        .put("code", "AMB")
                        .put("display", "ambulatory"))
                .put("type", new JSONArray()
                        .put(new JSONObject()
                                .put("coding", new JSONArray()
                                        .put(new JSONObject()
                                                .put("system", "http://snomed.info/sct")
                                                .put("code", "185317003")
                                                .put("display", "Telephone encounter (procedure)")))))
                .put("subject", new JSONObject()
                        .put("reference", "Patient/8d04edbd-85b2-43b4-b62a-aa14a976bf1b"))
                .put("period", new JSONObject()
                        .put("start", "2025-01-01T00:00:00.000Z")
                        .put("end", "2025-01-01T01:15:00.000Z"));
    }


    private static JSONObject getLocationObject(String id) {
        return new JSONObject()
                .put("resourceType", "Location")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-location")))
                .put("identifier", new JSONArray()
                        .put(new JSONObject()
                                .put("use", "usual")
                                .put("system", "http://exampleoflocation.com")
                                .put("value", "B1-S.F2")))
                .put("name", "name")
                .put("type", new JSONArray()
                        .put(new JSONObject()
                                .put("coding", new JSONArray()
                                        .put(new JSONObject()
                                                .put("system", "https://www.cdc.gov/nhsn/cdaportal/terminology/codesystem/hsloc.html")
                                                .put("version", "2022")
                                                .put("code", "1108-0")
                                                .put("display", "Emergency Department")
                                                .put("userSelected", true)))));
    }


    private static JSONObject getOrganizationObject(String id) {
        return new JSONObject()
                .put("resourceType", "Organization")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-organization")))
                .put("name", "name")
                .put("active", true)
                .put("identifier", new JSONArray()
                        .put(new JSONObject()
                                .put("system", "urn:oid:2.16.840.1.113883.4.4")
                                .put("use", "temp")
                                .put("value", "21-3259825")))
                .put("address", new JSONArray()
                        .put(new JSONObject()
                                .put("country", "USA")
                                .put("city", "Dallas")
                                .put("use", "billing")
                                .put("line", new JSONArray()
                                        .put("P.O. Box 660044"))
                                .put("postalCode", "75266-0044")
                                .put("state", "TX")
                                .put("type", "postal")))
                .put("telecom", new JSONArray()
                        .put(new JSONObject()
                                .put("system", "phone")
                                .put("value", "(+1) 972-766-6900")))
                .put("type", new JSONArray()
                        .put(new JSONObject()
                                .put("coding", new JSONArray()
                                        .put(new JSONObject()
                                                .put("system", "http://terminology.hl7.org/CodeSystem/organization-type")
                                                .put("code", "pay")
                                                .put("display", "Payer")))));
    }


}