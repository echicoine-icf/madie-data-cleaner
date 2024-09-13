package com.icf.ecqm.madie.data.cleaner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static int totalFiles = 0;
    private static int processedFiles = 0;

    private static String phase = "";

    public static void main(String[] args) throws IOException {


        Path currentDir = Paths.get("").toAbsolutePath();

        List<Path> jsonFiles = Files.walk(currentDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());

        totalFiles = jsonFiles.size();
        Queue<Path> fileQueue = new LinkedList<>(jsonFiles);
        Set<Path> processedFileSet = new HashSet<>();
        Map<String, Path> resourceIdToFileMap = new HashMap<>();

        phase = "Mapping files...";
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

        phase = "Writing new IDs to references...";
        while (!fileQueue.isEmpty()) {
            Path jsonFile = fileQueue.poll();
            if (!processedFileSet.contains(jsonFile)) {
                processFile(jsonFile, fileQueue, processedFileSet, resourceIdToFileMap);
                processedFiles++;
                reportProgress();
            }
        }
    }

    private static void processFile(Path jsonFile, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap) {
        try {
            String content = Files.readString(jsonFile);
            JSONObject jsonObject = new JSONObject(content);

            // Update the 'id' fields in 'resource'
            updateResourceId(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap);

            // Update references in 'evaluatedResource'
            updateReferences(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap);

            // Save the modified JSON
            Files.writeString(jsonFile, jsonObject.toString(4));
            System.out.println("Updated file: " + jsonFile);
            processedFileSet.add(jsonFile);
        } catch (Exception e) {
            System.err.println("Failed to process file: " + jsonFile);
            e.printStackTrace();
        }
    }

    private static void updateResourceId(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap) {
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

    private static void updateReferences(JSONObject jsonObject, Queue<Path> fileQueue, Set<Path> processedFileSet, Map<String, Path> resourceIdToFileMap) {
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource") && entry.getJSONObject("resource").has("evaluatedResource")) {
                    JSONArray evaluatedResources = entry.getJSONObject("resource").getJSONArray("evaluatedResource");

                    for (int j = 0; j < evaluatedResources.length(); j++) {
                        JSONObject evaluatedResource = evaluatedResources.getJSONObject(j);
                        if (evaluatedResource.has("reference")) {
                            String reference = evaluatedResource.getString("reference");
                            String[] parts = reference.split("/");
                            if (parts.length == 2 && isNumeric(parts[1])) {
                                String newReference = parts[0] + "/" + parts[0] + "-" + parts[1];
                                evaluatedResource.put("reference", newReference);

                                // Enqueue file for re-processing if needed
                                Path referencedFile = resourceIdToFileMap.get(parts[1]);
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

    private static void updateResourceIdMap(JSONObject jsonObject, Map<String, Path> resourceIdToFileMap, Path file) {
        if (jsonObject.has("entry")) {
            JSONArray entries = jsonObject.getJSONArray("entry");

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                if (entry.has("resource")) {
                    JSONObject resource = entry.getJSONObject("resource");
                    if (resource.has("id")) {
                        String id = resource.getString("id");
                        resourceIdToFileMap.put(id, file);
                    }
                }
            }
        }
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private static void reportProgress() {
        double percentage = (double) processedFiles / totalFiles * 100;
        System.out.print("\r" + phase + ": " + String.format("%.2f%%", percentage) + " processed.");
    }

}