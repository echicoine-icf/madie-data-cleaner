package com.icf.ecqm.madie.data.cleaner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;

public class FHIRJsonUtil {
    private static final List<String> dummyEntryResourceIdTracker = new ArrayList<>();

    /*
       "diagnosis": [
       {
           "condition": {
               "reference": "Condition/65f0d4b9-5788-47cf-a9ed-9e6a37aeb8c2"
           }
       }
   ],*/
    protected static final String[] referenceBySubArrays = {"diagnosis.condition"};


    /*
        "partOf": [
        {
            "reference": "Procedure/denex-pass-CMS646v0QICore4-3"
        }
    ],*/
    protected static final String[] referenceByArray = {"payor", "evaluatedResource", "partOf"};

    /*
    "subject": {
        "reference": "Patient/bb32779d-4c41-4113-85af-e534298c4579"
    },*/
    protected static final String[] referenceByString = {"subject", "beneficiary", "requester", "encounter", "medicationReference"};


    protected static void updateResourceId(JSONObject jsonObject, Map<String, Path> resourceIdToFileMap) {
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
                            String newId = truncateFhirId(resourceType + "-" + id);
                            resource.put("id", newId);

                            // Update resource ID to file mapping
                            Path file = resourceIdToFileMap.get(id);
                            if (file != null) {
                                resourceIdToFileMap.remove(id);
                                resourceIdToFileMap.put(newId, file);
                            }
                        } else if (id.length() > 64 || id.contains(" ")) {
                            String newId = truncateFhirId(id);
                            jsonObject.put("id", newId);

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
        } else {
            if (jsonObject.has("id") && jsonObject.has("resourceType")) {

                String id = jsonObject.getString("id");
                String resourceType = jsonObject.getString("resourceType");

                if (isNumeric(id)) {
                    String newId = truncateFhirId(resourceType + "-" + id);
                    jsonObject.put("id", newId);

                    // Update resource ID to file mapping
                    Path file = resourceIdToFileMap.get(id);
                    if (file != null) {
                        resourceIdToFileMap.remove(id);
                        resourceIdToFileMap.put(newId, file);
                    }
                } else if (id.length() > 64 || id.contains(" ")) {
                    String newId = truncateFhirId(id);
                    jsonObject.put("id", newId);

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


    protected static StringBuilder updateReferences(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap, Path jsonFile) {

        StringBuilder updatedEntriesLogger = new StringBuilder();
        //establish the jsonObject we need to process, then process that block and return it.
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource")) {
                    processJsonResource(entry.getJSONObject("resource"), fileQueue, processedFileSet, resourceIdToFileMap, jsonFile, updatedEntriesLogger);
                }
            }
        } else if (jsonObject.has("id")) {
            processJsonResource(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap, jsonFile, updatedEntriesLogger);
        }

        return updatedEntriesLogger;
    }

    protected static void processJsonResource(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap, Path jsonFile, StringBuilder updatedEntriesLogger) {
        /*
        "partOf": [
        {
            "reference": "Procedure/denex-pass-CMS646v0QICore4-3"
        }
        ],*/
        for (String entryResourceID : FHIRJsonUtil.referenceByArray) {
            if (jsonObject.has(entryResourceID)) {
                JSONArray resourcesArray = jsonObject.getJSONArray(entryResourceID);
                for (int j = 0; j < resourcesArray.length(); j++) {
                    JSONObject entryIDResource = resourcesArray.getJSONObject(j);
                    if (entryIDResource.has("reference")) {
                        String reference = entryIDResource.getString("reference");
                        String[] parts = reference.split("/");
                        if (parts.length == 2) {

                            String resourceID = parts[1];
                            if (isNumeric(parts[1])) {
                                resourceID = truncateFhirId(parts[0] + "-" + parts[1]);
                            } else if (parts[1].length() > 64 || parts[1].contains(" ")) {
                                resourceID = truncateFhirId(parts[1]);
                            }
                            // Strip external reference, align the ID with HAPI-suitable rules:
                            String newReference = parts[0] + "/" + resourceID;

                            if (newReference.equals(reference)) continue;

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

       /*
        "diagnosis": [
            {
                "condition": {
                    "reference": "Condition/65f0d4b9-5788-47cf-a9ed-9e6a37aeb8c2"
                }
            }
        ],*/
        for (String entrySub : FHIRJsonUtil.referenceBySubArrays) {
            String[] theseParts = entrySub.split("\\.");
            //diagnosis
            String arrayName = theseParts[0];

            //condition
            String subEntry = theseParts[1];

            //if the json block has diagnosis, process as Array
            if (jsonObject.has(arrayName)) {
                JSONArray resourceArray = jsonObject.getJSONArray(arrayName);

                //for each entry in the array, look for the subEntry name
                for (int j = 0; j < resourceArray.length(); j++) {
                    JSONObject entryIDResource = resourceArray.getJSONObject(j);

                    if (entryIDResource.has(subEntry)) {
                        JSONObject subEntryObject = entryIDResource.getJSONObject(subEntry);

                        if (subEntryObject != null && subEntryObject.has("reference")) {
                            String reference = subEntryObject.getString("reference");
                            String[] parts = reference.split("/");
                            if (parts.length == 2) {

                                String resourceID = parts[1];
                                if (isNumeric(parts[1])) {
                                    resourceID = truncateFhirId(parts[0] + "-" + parts[1]);
                                } else if (parts[1].length() > 64 || parts[1].contains(" ")) {
                                    resourceID = truncateFhirId(parts[1]);
                                }
                                // Strip external reference, align the ID with HAPI-suitable rules:
                                String newReference = parts[0] + "/" + resourceID;

                                if (newReference.equals(reference)) continue;

                                subEntryObject.put("reference", newReference); // Update reference in the nested object

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

        /*
        "subject": {
            "reference": "Patient/bb32779d-4c41-4113-85af-e534298c4579"
        },*/
        for (String entryResourceID : FHIRJsonUtil.referenceByString) {
            if (jsonObject.has(entryResourceID)) {
                JSONObject resourceObject = jsonObject.getJSONObject(entryResourceID);

                if (resourceObject.has("reference")) {
                    String reference = resourceObject.getString("reference");
                    String[] parts = reference.split("/");
                    if (parts.length == 2) {

                        String resourceID = parts[1];
                        if (isNumeric(parts[1])) {
                            resourceID = truncateFhirId(parts[0] + "-" + parts[1]);
                        } else if (parts[1].length() > 64 || parts[1].contains(" ")) {
                            resourceID = truncateFhirId(parts[1]);
                        }
                        //strip external reference, align the id with hapi-suitable rules:
                        String newReference = parts[0] + "/" + resourceID;

                        if (newReference.equals(reference)) continue;

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

    protected static StringBuilder updateResourceIdMap(JSONObject jsonObject, Map<String, Path> resourceIdToFileMap, Path file, Map<String, ParsedLogEntry> dummyEntryMap) {
        StringBuilder log = new StringBuilder();
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");


            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource")) {
                    JSONObject resource = entry.getJSONObject("resource");
                    if (resource.has("id")) {
                        String id = resource.getString("id");

                        String recordedId = id;
                        if (isNumeric(id) && resource.has("resourceType")) {
                            recordedId = resource.getString("resourceType") + "-" + id;
                        } else if (id.length() > 64 || id.contains(" ")) {
                            recordedId = truncateFhirId(id);
                        }

                        //avoid duplicates injected via dummy entry:
                        dummyEntryResourceIdTracker.add(recordedId);
                        resourceIdToFileMap.put(id, file);
                    }
                }
            }
            //not a bundle but rather single file resource:
        } else if (jsonObject.has("id")) {
            String id = jsonObject.getString("id");

            String recordedId = id;
            if (isNumeric(id) && jsonObject.has("resourceType")) {
                recordedId = jsonObject.getString("resourceType") + "-" + id;
            } else if (id.length() > 64 || id.contains(" ")) {
                recordedId = truncateFhirId(id);
            }

            //avoid duplicates injected via dummy entry:
            dummyEntryResourceIdTracker.add(recordedId);
            resourceIdToFileMap.put(id, file);

        }

        //place "dummy resource" if previous httplog indicates it's missing:
        String fileNameKey = file.getFileName().toString();
        //this file was logged as having a missing resource, find which one, post it to the server:
        if (dummyEntryMap.containsKey(fileNameKey)) {
            ParsedLogEntry ple = dummyEntryMap.get(fileNameKey);

            //if the id is all numeric, append type and - to it.
            String resourceID = isNumeric(ple.getResourceId()) ?
                    ple.getResourceType() + "-" + ple.getResourceId()
                    :
                    ple.getResourceId();

            resourceID = truncateFhirId(resourceID);

            if (dummyEntryResourceIdTracker.contains(resourceID)) {
                return log;
            }

            JSONObject newResource = null;

            if (ple.getResourceType().equalsIgnoreCase(("Condition"))) {
                newResource = ResourceFactory.getConditionObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Practitioner"))) {
                newResource = ResourceFactory.getPractitionerObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Encounter"))) {
                newResource = ResourceFactory.getEncounterObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Location"))) {
                newResource = ResourceFactory.getLocationObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Organization"))) {
                newResource = ResourceFactory.getOrganizationObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Patient"))) {
                newResource = ResourceFactory.getPatientObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Procedure"))) {
                newResource = ResourceFactory.getProcedureObject(resourceID);
            } else if (ple.getResourceType().equalsIgnoreCase(("Medication"))) {
                newResource = ResourceFactory.getMedicationObject(resourceID);
            }

            String resourceURL = (ple.getUrl().endsWith("/")
                    ? ple.getUrl() + ple.getResourceType() :
                    ple.getUrl() + "/" + ple.getResourceType()) + "/" + resourceID;

            if (newResource != null) {
                //place resource on server
                HttpClient httpClient = HttpClient.newHttpClient();
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(resourceURL))
                            .header("Content-Type", "application/fhir+json")
                            .PUT(HttpRequest.BodyPublishers.ofString(newResource.toString()))
                            .build();

                    // Send the request and get the response
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    // log the response
                    log.append("\n\r\n\rUploaded 'dummy entry' ")
                            .append(ple.getResourceType()).append(": ")
                            .append(ple.getUrl()).append("\n\r\tResponse:")
                            .append("\n\r").append(response.statusCode())
                            .append("\n\r").append(response.body());
                    dummyEntryResourceIdTracker.add(resourceID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return log;
    }

    private static String truncateFhirId(String input) {
        return (input.length() > 64 ? input.substring(0, 64) : input).replace(" ", "");
    }


    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }
}
