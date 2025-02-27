package com.icf.ecqm.madie.data.cleaner;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    private static int totalFiles = 0;
    private static int processedFiles = 0;
    private static String phase = "";
    private static final StringBuilder log = new StringBuilder();
    private static final Map<String, ParsedLogEntry> dummyEntryMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        Path currentDir = Paths.get("").toAbsolutePath();

        boolean logFile = false;
        for (String arg : args) {
            if (arg.toLowerCase().contains("-checklogs")) {
                logFile = true;
                break;
            }
        }

        if (logFile) {
            processLogFile(currentDir);
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


                log.append(FHIRJsonUtil.updateResourceIdMap(jsonObject, resourceIdToFileMap, file, dummyEntryMap));

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
                    .replace("Practitioner/example", "Practitioner/practitioner-123456");
            JSONObject jsonObject = new JSONObject(content);

            // Update the 'id' fields in 'resource'
            FHIRJsonUtil.updateResourceId(jsonObject, resourceIdToFileMap);

            // Update references entries
            log.append(FHIRJsonUtil.updateReferences(jsonObject, fileQueue, processedFileSet, resourceIdToFileMap, jsonFile));

            // Save the modified jsonObject ref
            Files.writeString(jsonFile, jsonObject.toString(4));

            processedFileSet.add(jsonFile);
        } catch (Exception e) {
            System.err.println("Failed to process file: " + jsonFile);
            e.printStackTrace();
        }
    }

    private static void processLogFile(Path currentDir) {
        //fist, check for latest http log, attempt to add dummy entries to files claiming they are missing:
        try {
            Set<String> allLogs = readLogFilesIntoSet(currentDir.toString());
            String url = "";
            if (!allLogs.isEmpty()) {
                System.out.println("Reading all http logs to attempt to patch missing resources.");

                for (String line : allLogs) {
                    ParsedLogEntry entry = ParsedLogEntry.parseLogEntry(line);
                    if (entry != null) {
                        url = entry.getUrl();
                        if (!dummyEntryMap.containsKey(entry.getFileName())) {
                            dummyEntryMap.put(entry.getFileName(), entry);
                        }
                    }
                }
            } else {
                System.out.println("No log files found.");
            }
            if (!url.isEmpty()) {
                System.out.println("Entries for " + url + " processed with " + dummyEntryMap.size() + " missing resources identified.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> readLogFilesIntoSet(String directoryPath) throws IOException {
        File dir = new File(directoryPath);

        // Validate that the provided path is a directory
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("The provided path is not a directory.");
        }

        // Regex to match log filenames
        Pattern logFilePattern = Pattern.compile("http_request_fail_\\d{14}\\.log");

        // Set to store unique lines
        Set<String> uniqueLines = new HashSet<>();

        // Process each matching file in the directory
        File[] logFiles = Objects.requireNonNull(dir.listFiles((dir1, name) -> logFilePattern.matcher(name).matches()));

        for (File logFile : logFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    uniqueLines.add(line);
                }
            }
        }

        return uniqueLines;
    }

    private static void reportProgress() {
        double percentage = (double) processedFiles / totalFiles * 100;
        System.out.print("\r" + phase + ": " + String.format("%.2f%%", percentage) + " processed.");
    }

}