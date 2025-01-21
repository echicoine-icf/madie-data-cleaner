This is a small utility to be used alongside cqf-tooling's RefreshIG process. 
MADiE exports currently do not validate well with hapi-fhir servers as different 
rules are ignored that ultimately block the resource from being uploaded to hapi-fhir
servers.

To start, compile the jar using mvn clean install. Then nagivate to the target folder and 
copy the jar "MADiEDataCleaner-jar-with-dependencies.jar"  to the IG directory, 
such as ecqm-content-qicore-2024.

To run the utility, open a command prompt in the same directory as the jar and run:
  java -jar MADiEDataCleaner-jar-with-dependencies.jar

This initial pass will clean up IDs and references to those IDs by fixing:
  - All numeric IDS
  - IDs with spaces in them
  - IDs that exceed 64 characters

Once the data has been "cleaned" you can run the RefreshIG process from cqf-tooling. Failed
resources will be logged to http log files (it's best to delete previous http log files generated
prior to using this script.) 

If referenced resources are missing and failed to upload to the hapi-fhir server during RefreshIG,
http log files should be generated detailing the ids, resource types, and url to which these 
resources need to reside.

At this point, re-run the utility with:
  java -jar MADiEDataCleaner-jar-with-dependencies.jar -checklogs

The -checklogs flag will now introduce a step in which the log files are parsed and attempts 
are made to send the missing resources as "barebones" entries to the server. Once these missing
references are on the hapi-fhir server, you can re-run RefreshIG and hopefully avoid the
missing reference errors. If further missing reference errors exist, simply repeat the process 
using -checklogs and new entries will be attempted against the server.
