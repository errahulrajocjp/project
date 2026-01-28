package onevz.soe.smbenrollment.responses.spectrumadapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Response object from Nautilus Bulk Address Qualification API.
 * 
 * IMPORTANT: Added missing fields based on check5GCoverage API expected response:
 * - fwaCbandDownloadSpeedList
 * - fwaCbandUploadSpeedList
 * 
 * Please verify these fields exist in actual Nautilus API response.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkAddressQualificationResponse {
    
    // Existing fields
    private String recordIdentifier;
    private String returnCode;
    private String returnMessage;
    private boolean fiveGHomeQualified;
    private boolean moveQualified;
    private boolean planChangeRequired;
    private boolean deviceChangeRequired;
    private String launchType;
    private String addressType;
    private String installType;
    private String market;
    private String preOrderInServiceDate;
    private boolean additionalInforrequired;
    private AdditionalAddressInfo additionalAddressInfo;
    private AddressInfo addressInfo;
    private Eligibilities eligibilities;
    private PriorQualification priorQualification;
    private String returnName;
    private String availableCapacity;
    private AvailableCapacityInfo availableCapacityInfo;
    private boolean cBandQualified;
    private boolean LTEQualified;
    private boolean wifiBackUpCbandQualified;
    private boolean wifiBackupLteQualified;
    private boolean VHILiteQualified;
    
    // ========================================
    // NEWLY ADDED FIELDS - Speed Lists
    // ========================================
    
    /**
     * FWA C-Band download speed options.
     * Example: ["150", "300"]
     */
    private List<String> fwaCbandDownloadSpeedList;
    
    /**
     * FWA C-Band upload speed options.
     * Example: ["15", "10"]
     */
    private List<String> fwaCbandUploadSpeedList;
    
    /**
     * List of qualified C-Band installation types.
     * Example: ["indoor", "outdoor"]
     */
    private List<String> qualCBandInstallTypes;
}
