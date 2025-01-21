package com.icf.ecqm.madie.data.cleaner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;

public class ResourceFactory {

    protected static JSONObject getConditionObject(String id) {
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
//                .put("subject", new JSONObject()
//                        .put("reference", "Patient/97b034d3-87b0-4e89-b672-58389f8ebcb3"))
                .put("onsetDateTime", "2025-08-06T08:00:00.000+00:00")
                .put("recordedDate", "2025-08-09T08:00:00.000+00:00");
    }

    protected static JSONObject getPractitionerObject(String id) {
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


    protected static JSONObject getEncounterObject(String id) {
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
//                .put("subject", new JSONObject()
//                        .put("reference", "Patient/8d04edbd-85b2-43b4-b62a-aa14a976bf1b"))
                .put("period", new JSONObject()
                        .put("start", "2025-01-01T00:00:00.000Z")
                        .put("end", "2025-01-01T01:15:00.000Z"));
    }


    protected static JSONObject getLocationObject(String id) {
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


    protected static JSONObject getOrganizationObject(String id) {
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


    //Patient dummy resource
    protected static JSONObject getPatientObject(String id) {
        return new JSONObject()
                .put("resourceType", "Patient")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient")
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient")))
                .put("identifier", new JSONArray()
                        .put(new JSONObject()
                                .put("system", "http://hospital.smarthealthit.org")
                                .put("value", "999999995")))
                .put("extension", new JSONArray()
                        .put(new JSONObject()
                                .put("url", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race")
                                .put("extension", new JSONArray()
                                        .put(new JSONObject()
                                                .put("url", "ombCategory")
                                                .put("valueCoding", new JSONObject()
                                                        .put("system", "urn:oid:2.16.840.1.113883.6.238")
                                                        .put("code", "2028-9")
                                                        .put("display", "Asian")))
                                        .put(new JSONObject()
                                                .put("url", "text")
                                                .put("valueString", "Asian"))))
                        .put(new JSONObject()
                                .put("url", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity")
                                .put("extension", new JSONArray()
                                        .put(new JSONObject()
                                                .put("url", "ombCategory")
                                                .put("valueCoding", new JSONObject()
                                                        .put("system", "urn:oid:2.16.840.1.113883.6.238")
                                                        .put("code", "2135-2")
                                                        .put("display", "Hispanic or Latino")))
                                        .put(new JSONObject()
                                                .put("url", "text")
                                                .put("valueString", "Hispanic or Latino")))))
                .put("gender", "female")
                .put("name", new JSONArray()
                        .put(new JSONObject()
                                .put("family", generateRandomString())
                                .put("given", new JSONArray()
                                        .put(generateRandomString()))))
                .put("birthDate", "1987-06-12");
    }

    protected static JSONObject getProcedureObject(String id) {
        return new JSONObject()
                .put("resourceType", "Procedure")
                .put("id", id)
                .put("status", "completed")
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-procedure")))
                .put("code", new JSONObject()
                        .put("coding", new JSONArray()
                                .put(new JSONObject()
                                        .put("system", "http://snomed.info/sct")
                                        .put("code", "307521008"))))
                .put("performedPeriod", new JSONObject()
                        .put("start", "2026-07-10T09:00:00+00:00")) // Static value
                .put("extension", new JSONArray()
                        .put(new JSONObject()
                                .put("valueDateTime", "2026-11-19T09:35:00-04:00") // Static value
                                .put("url", "http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-recorded")));
    }
    protected static JSONObject getMedicationObject(String id) {
        return new JSONObject()
                .put("resourceType", "Medication")
                .put("id", id)
                .put("meta", new JSONObject()
                        .put("profile", new JSONArray()
                                .put("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-medication")))
                .put("code", new JSONObject()
                        .put("coding", new JSONArray()
                                .put(new JSONObject()
                                        .put("system", "http://www.nlm.nih.gov/research/umls/rxnorm")
                                        .put("code", "1000001")
                                        .put("display", "amlodipine 5 MG / hydrochlorothiazide 25 MG / olmesartan medoxomil 40 MG Oral Tablet")
                                        .put("version", "04012024"))));
    }

    protected static String generateRandomString() {
        StringBuilder result = new StringBuilder(30);
        for (int i = 0; i < 30; i++) {
            int randomIndex = new SecureRandom().nextInt("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".length());
            result.append("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".charAt(randomIndex));
        }
        return result.toString();
    }
}
