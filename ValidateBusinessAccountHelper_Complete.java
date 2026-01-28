package onevz.soe.smbenrollment.helper;

import onevz.soe.smbenrollment.model.CustomerAddress;
import onevz.soe.smbenrollment.requests.spectrumadapter.FiveGCoverageCheckRequest;
import onevz.soe.smbenrollment.requests.spectrumadapter.NautilusQualificationRequest;
import onevz.soe.smbenrollment.requests.spectrumadapter.SplitAddressRequest;
import onevz.soe.smbenrollment.responses.SmbResponseWrapper;
import onevz.soe.smbenrollment.responses.SoeDataWrapper;
import onevz.soe.smbenrollment.responses.spectrumadapter.AddressInfo;
import onevz.soe.smbenrollment.responses.spectrumadapter.AvailableCapacityInfo;
import onevz.soe.smbenrollment.responses.spectrumadapter.BulkAddressQualificationResponse;
import onevz.soe.smbenrollment.responses.spectrumadapter.BundleInfo;
import onevz.soe.smbenrollment.responses.spectrumadapter.Eligibilities;
import onevz.soe.smbenrollment.responses.spectrumadapter.FiveGCoverageCheckResponse;
import onevz.soe.smbenrollment.responses.spectrumadapter.NautilusQualificationResponse;
import onevz.soe.smbenrollment.responses.spectrumadapter.SplitAddressResponse;
import onevz.soe.smbenrollment.constants.SmbConstants;
import onevz.soe.smbenrollment.utils.ValidationUtils;
import onevz.soe.util.CollectionUtilities;
import onevz.soe.util.StringUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ValidateBusinessAccountHelper {

    private static final Logger logger = LoggerFactory.getLogger(ValidateBusinessAccountHelper.class);
    private static final String SUCCESS_RETURN_CODE = "0";
    private static final String QUALIFIED_STATUS_MSG = "This address qualifies for 5G service.";
    private static final String NOT_QUALIFIED_STATUS_MSG = "This address does not qualify for 5G service.";

    private final FiveGCoverageCheckHelper fiveGCoverageCheckHelper;
    private final CxpSpectrumAdapterService spectrumAdapterService;

    public ValidateBusinessAccountHelper(FiveGCoverageCheckHelper fiveGCoverageCheckHelper,
                                         CxpSpectrumAdapterService spectrumAdapterService) {
        this.fiveGCoverageCheckHelper = fiveGCoverageCheckHelper;
        this.spectrumAdapterService = spectrumAdapterService;
    }

    /**
     * Main entry point for 5G coverage check.
     * Flow: Validate addresses -> Call Split Address service -> Call Nautilus service -> Map response
     */
    public Mono<SoeDataWrapper<SmbResponseWrapper<FiveGCoverageCheckResponse>>> check5GCoverageForAddresses(
            FiveGCoverageCheckRequest fiveGCoverageCheckRequest) {

        // Early validation - return error immediately for invalid addresses
        if (hasInvalidCustomerAddress(fiveGCoverageCheckRequest.getCustomerAddress())) {
            logger.warn("Customer addresses are invalid. Returning error response.");
            return buildErrorResponse(new IllegalArgumentException("Invalid customer address: addressLine1 and zipCode are required"));
        }

        return Flux.fromIterable(fiveGCoverageCheckRequest.getCustomerAddress())
                .flatMap(this::processSplitAddressForCustomer)
                .collectList()
                .flatMap(this::processValidateCustomerAccountForFiveGCoverage)
                .map(this::buildSuccessResponse)
                .onErrorResume(this::buildErrorResponse);
    }

    /**
     * Calls Split Address service for a single customer address.
     * Returns enriched CustomerAddress with validated/parsed address components.
     */
    private Mono<CustomerAddress> processSplitAddressForCustomer(CustomerAddress customerAddress) {
        SplitAddressRequest splitAddressRequest = fiveGCoverageCheckHelper.buildSplitCustomerAddressRequest(customerAddress);

        return spectrumAdapterService.splitAddress(splitAddressRequest)
                .map(addressResponse -> {
                    if (isValidSplitAddressResponse(addressResponse)) {
                        onevz.soe.smbenrollment.responses.spectrumadapter.Address address =
                                extractSplitAddress(addressResponse);

                        if (address != null) {
                            CustomerAddress updatedAddress = createUpdatedAddressFromSplit(customerAddress, address);
                            updatedAddress.setQualified(true);
                            updatedAddress.setStatusMsg("Address validated successfully");
                            return updatedAddress;
                        }
                    }

                    CustomerAddress failedAddress = createCustomerAddressCopy(customerAddress);
                    failedAddress.setQualified(false);
                    failedAddress.setStatusMsg("Address validation failed: Invalid split address response");
                    return failedAddress;
                })
                .doOnError(e -> logger.error("Error occurred from split address service for address: {}. Error: {}",
                        customerAddress.getAddressLine1(), e.getMessage(), e))
                .onErrorResume(error -> {
                    logger.error("Handling error gracefully for address: {}", customerAddress.getAddressLine1());
                    CustomerAddress errorAddress = createCustomerAddressCopy(customerAddress);
                    errorAddress.setQualified(false);
                    errorAddress.setStatusMsg("Address validation failed: " + error.getMessage());
                    return Mono.just(errorAddress);
                });
    }

    // ==================================================================================
    // NAUTILUS QUALIFICATION SERVICE - processValidateCustomerAccountForFiveGCoverage
    // ==================================================================================

    /**
     * Processes validated customer addresses through Nautilus Qualification service.
     * 
     * @param customerAddresses List of addresses validated by Split Address service
     * @return Mono containing FiveGCoverageCheckResponse with enriched qualification data
     */
    private Mono<FiveGCoverageCheckResponse> processValidateCustomerAccountForFiveGCoverage(
            List<CustomerAddress> customerAddresses) {

        // Handle empty input gracefully
        if (CollectionUtilities.isEmptyOrNull(customerAddresses)) {
            logger.warn("No customer addresses to process for Nautilus qualification");
            return Mono.just(createEmptyResponse());
        }

        logger.info("Building Nautilus qualification request for {} addresses", customerAddresses.size());

        // Build Nautilus request - recordIdentifier is set as index (0, 1, 2, ...)
        NautilusQualificationRequest nautilusRequest = 
                fiveGCoverageCheckHelper.buildNautilusQualificationRequest(customerAddresses);

        return spectrumAdapterService.checkAddressQualification(nautilusRequest)
                .flatMap(nautilusResponse -> 
                        mapNautilusResponseToFiveGCoverageResponse(customerAddresses, nautilusResponse))
                .doOnSuccess(response -> 
                        logger.info("Successfully processed Nautilus qualification for {} addresses", 
                                response.getCustomerAddress() != null ? response.getCustomerAddress().size() : 0))
                .doOnError(e -> 
                        logger.error("Error calling Nautilus service: {}", e.getMessage(), e))
                .onErrorResume(error -> 
                        handleNautilusServiceError(customerAddresses, error));
    }

    /**
     * Maps Nautilus qualification response to FiveGCoverageCheckResponse.
     * Correlates responses using recordIdentifier (index-based matching).
     */
    private Mono<FiveGCoverageCheckResponse> mapNautilusResponseToFiveGCoverageResponse(
            List<CustomerAddress> customerAddresses,
            NautilusQualificationResponse nautilusResponse) {

        // Validate Nautilus response structure
        if (!isValidNautilusResponse(nautilusResponse)) {
            logger.warn("Nautilus response is null or empty, returning addresses with failure status");
            return Mono.just(createFailureResponseForAllAddresses(customerAddresses, 
                    "No qualification data received from Nautilus service"));
        }

        List<BulkAddressQualificationResponse> qualificationResponses =
                nautilusResponse.getData().getBulkAddressQualificationResponse();

        // Build lookup map: recordIdentifier -> BulkAddressQualificationResponse
        // This enables O(1) lookup when mapping back to customer addresses
        Map<String, BulkAddressQualificationResponse> qualificationMap = buildQualificationMap(qualificationResponses);

        // Map each customer address with its corresponding qualification response
        // IMPORTANT: recordIdentifier is 1-based ("1", "2", "3"...) per Nautilus API spec
        List<CustomerAddress> enrichedAddresses = new ArrayList<>(customerAddresses.size());

        for (int index = 0; index < customerAddresses.size(); index++) {
            CustomerAddress originalAddress = customerAddresses.get(index);
            
            // recordIdentifier is 1-based: index 0 -> "1", index 1 -> "2", etc.
            String recordIdentifier = String.valueOf(index + 1);

            BulkAddressQualificationResponse qualification = qualificationMap.get(recordIdentifier);

            CustomerAddress enrichedAddress = mapQualificationToCustomerAddress(originalAddress, qualification, index);
            enrichedAddresses.add(enrichedAddress);
        }

        FiveGCoverageCheckResponse response = new FiveGCoverageCheckResponse();
        response.setCustomerAddress(enrichedAddresses);
        response.setStatus(SmbConstants.SUCCESS_STATUS);  // status: 1 for success
        response.setErrors(Collections.emptyList());       // No errors on success
        response.setBypassAddressValidation(false);        // Default false

        logger.info("Successfully mapped {} addresses with Nautilus qualification data", enrichedAddresses.size());
        return Mono.just(response);
    }

    /**
     * Maps a single BulkAddressQualificationResponse to CustomerAddress.
     * Creates a NEW CustomerAddress object to maintain immutability.
     */
    private CustomerAddress mapQualificationToCustomerAddress(
            CustomerAddress originalAddress,
            BulkAddressQualificationResponse qualification,
            int addressIndex) {

        // Create new CustomerAddress to avoid mutation of original
        CustomerAddress enrichedAddress = createCustomerAddressCopy(originalAddress);

        // Handle case where no qualification response found for this address
        if (qualification == null) {
            logger.warn("No qualification response found for address at index {}: {}",
                    addressIndex, originalAddress.getAddressLine1());
            enrichedAddress.setQualified(false);
            enrichedAddress.setStatusMsg("No qualification data found for this address");
            enrichedAddress.setEventCorrelationId(generateEventCorrelationId(originalAddress, addressIndex));
            return enrichedAddress;
        }

        // Determine if address is eligible based on returnCode
        boolean isServiceCallSuccessful = SUCCESS_RETURN_CODE.equals(qualification.getReturnCode());

        // Set primary qualification status
        enrichedAddress.setQualified(qualification.isFiveGHomeQualified());

        // Build and set status message
        enrichedAddress.setStatusMsg(buildStatusMessage(qualification, isServiceCallSuccessful));

        // Generate and set event correlation ID
        enrichedAddress.setEventCorrelationId(generateEventCorrelationId(originalAddress, addressIndex));

        // Map address identification info (addressId, subLocationId, fuzeSiteId, sector)
        mapAddressIdentificationInfo(enrichedAddress, qualification.getAddressInfo());

        // Map capacity information (availableCapacityCBand, availableCapacity4GHome)
        mapCapacityInfo(enrichedAddress, qualification.getAvailableCapacityInfo());

        // Map qualification flags
        enrichedAddress.setQualifiedCBand(qualification.isCBandQualified());
        enrichedAddress.setQualified4GHome(qualification.isLTEQualified());

        // Map eligibilities (bundleList, maxSpeed)
        mapEligibilities(enrichedAddress, qualification.getEligibilities());

        // Map install types if available
        mapInstallTypes(enrichedAddress, qualification);

        // Map FWA speed lists (NEW - required for expected response)
        mapSpeedLists(enrichedAddress, qualification);

        // Set additional flags with safe defaults
        enrichedAddress.setCbandBYODLine(false);
        enrichedAddress.setFloorPlanAvl(false);

        return enrichedAddress;
    }

    /**
     * Maps FWA speed lists from qualification response to CustomerAddress.
     * These fields are required in the final check5GCoverage response.
     */
    private void mapSpeedLists(CustomerAddress enrichedAddress, BulkAddressQualificationResponse qualification) {
        // Map download speed list
        if (!CollectionUtilities.isEmptyOrNull(qualification.getFwaCbandDownloadSpeedList())) {
            enrichedAddress.setFwaCbandDownloadSpeedList(
                    new ArrayList<>(qualification.getFwaCbandDownloadSpeedList()));
        }

        // Map upload speed list
        if (!CollectionUtilities.isEmptyOrNull(qualification.getFwaCbandUploadSpeedList())) {
            enrichedAddress.setFwaCbandUploadSpeedList(
                    new ArrayList<>(qualification.getFwaCbandUploadSpeedList()));
        }
    }

    // ==================================================================================
    // HELPER METHODS FOR NAUTILUS RESPONSE MAPPING
    // ==================================================================================

    /**
     * Validates Nautilus response structure before processing.
     */
    private boolean isValidNautilusResponse(NautilusQualificationResponse nautilusResponse) {
        return nautilusResponse != null
                && nautilusResponse.getData() != null
                && !CollectionUtilities.isEmptyOrNull(nautilusResponse.getData().getBulkAddressQualificationResponse());
    }

    /**
     * Builds a map of recordIdentifier -> BulkAddressQualificationResponse for O(1) lookup.
     */
    private Map<String, BulkAddressQualificationResponse> buildQualificationMap(
            List<BulkAddressQualificationResponse> qualificationResponses) {

        return qualificationResponses.stream()
                .filter(q -> q != null && q.getRecordIdentifier() != null)
                .collect(Collectors.toMap(
                        BulkAddressQualificationResponse::getRecordIdentifier,
                        Function.identity(),
                        (existing, replacement) -> {
                            logger.warn("Duplicate recordIdentifier found: {}, keeping first occurrence",
                                    existing.getRecordIdentifier());
                            return existing;
                        }
                ));
    }

    /**
     * Builds status message based on qualification response.
     */
    private String buildStatusMessage(BulkAddressQualificationResponse qualification, boolean isServiceCallSuccessful) {
        if (isServiceCallSuccessful && qualification.isFiveGHomeQualified()) {
            return QUALIFIED_STATUS_MSG;
        }

        // Use returnMessage from Nautilus if available
        if (StringUtilities.isNotEmptyOrNull(qualification.getReturnMessage())) {
            return qualification.getReturnMessage();
        }

        return NOT_QUALIFIED_STATUS_MSG;
    }

    /**
     * Generates event correlation ID for tracking.
     * Format: {zipCode}_{timestamp}_{suffix}
     */
    private String generateEventCorrelationId(CustomerAddress address, int index) {
        String zipCode = StringUtilities.isNotEmptyOrNull(address.getZipCode()) ? address.getZipCode() : "00000";
        long timestamp = System.currentTimeMillis();
        // Generate a random suffix similar to the API example pattern
        String suffix = generateRandomSuffix();
        return String.format("%s_%d_%s", zipCode, timestamp, suffix);
    }

    /**
     * Generates a random alphanumeric suffix for event correlation ID.
     */
    private String generateRandomSuffix() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder suffix = new StringBuilder(2);
        for (int i = 0; i < 2; i++) {
            int index = (int) (Math.random() * chars.length());
            suffix.append(chars.charAt(index));
        }
        return suffix.toString();
    }

    /**
     * Maps address identification info from AddressInfo to CustomerAddress.
     */
    private void mapAddressIdentificationInfo(CustomerAddress enrichedAddress, AddressInfo addressInfo) {
        if (addressInfo == null) {
            logger.debug("AddressInfo is null, skipping address identification mapping");
            enrichedAddress.setFuzeSiteId(0);
            enrichedAddress.setSector(0);
            return;
        }

        // Map addressId
        enrichedAddress.setAddressId(addressInfo.getAddressId());

        // Map subLocationId - prefer locationId, fallback to baseLocationId
        String subLocationId = StringUtilities.isNotEmptyOrNull(addressInfo.getLocationId())
                ? addressInfo.getLocationId()
                : addressInfo.getBaseLocationId();
        enrichedAddress.setSubLocationId(subLocationId);

        // Parse and map fuzeSiteId (String -> Integer)
        enrichedAddress.setFuzeSiteId(parseIntegerSafely(addressInfo.getFuzeSiteId(), "fuzeSiteId", 0));

        // Parse and map sector (String -> Integer)
        enrichedAddress.setSector(parseIntegerSafely(addressInfo.getSector(), "sector", 0));
    }

    /**
     * Maps capacity information from AvailableCapacityInfo to CustomerAddress.
     */
    private void mapCapacityInfo(CustomerAddress enrichedAddress, AvailableCapacityInfo capacityInfo) {
        if (capacityInfo == null) {
            logger.debug("AvailableCapacityInfo is null, setting default capacity values");
            enrichedAddress.setAvailableCapacityCBand(0);
            enrichedAddress.setAvailableCapacity4GHome(0);
            return;
        }

        // Parse cbandCapacity (String like "15.0" -> Integer 15)
        enrichedAddress.setAvailableCapacityCBand(
                parseDoubleToIntSafely(capacityInfo.getCbandCapacity(), "cbandCapacity", 0));

        // Parse lteCapacity -> availableCapacity4GHome
        enrichedAddress.setAvailableCapacity4GHome(
                parseDoubleToIntSafely(capacityInfo.getLteCapacity(), "lteCapacity", 0));
    }

    /**
     * Maps eligibilities (bundle list, speed tier) from Eligibilities to CustomerAddress.
     */
    private void mapEligibilities(CustomerAddress enrichedAddress, Eligibilities eligibilities) {
        if (eligibilities == null) {
            logger.debug("Eligibilities is null, skipping eligibility mapping");
            return;
        }

        // Map fiveGHomeBundle -> bundleList
        if (!CollectionUtilities.isEmptyOrNull(eligibilities.getFiveGHomeBundle())) {
            List<String> bundleNames = eligibilities.getFiveGHomeBundle().stream()
                    .filter(bundle -> bundle != null && StringUtilities.isNotEmptyOrNull(bundle.getBundleName()))
                    .map(BundleInfo::getBundleName)
                    .collect(Collectors.toList());

            if (!bundleNames.isEmpty()) {
                enrichedAddress.setBundleList(bundleNames);
            }
        }

        // Map availableSpeedTier -> maxSpeed
        if (StringUtilities.isNotEmptyOrNull(eligibilities.getAvailableSpeedTier())) {
            enrichedAddress.setMaxSpeed(eligibilities.getAvailableSpeedTier());
        }
    }

    /**
     * Maps install types from qualification response to CustomerAddress.
     * Prefers qualCBandInstallTypes list if available, falls back to single installType.
     */
    private void mapInstallTypes(CustomerAddress enrichedAddress, BulkAddressQualificationResponse qualification) {
        // Prefer the list of install types if available (from updated POJO)
        if (!CollectionUtilities.isEmptyOrNull(qualification.getQualCBandInstallTypes())) {
            enrichedAddress.setQualCBandInstallTypes(new ArrayList<>(qualification.getQualCBandInstallTypes()));
        } else if (StringUtilities.isNotEmptyOrNull(qualification.getInstallType())) {
            // Fallback to single installType wrapped in list
            enrichedAddress.setQualCBandInstallTypes(Collections.singletonList(qualification.getInstallType()));
        }
    }

    /**
     * Safely parses a String to Integer, returning defaultValue on failure.
     */
    private Integer parseIntegerSafely(String value, String fieldName, int defaultValue) {
        if (StringUtilities.isEmptyOrNull(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Unable to parse {} value '{}' to Integer, using default: {}", fieldName, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Safely parses a String (potentially with decimals like "15.0") to Integer.
     */
    private Integer parseDoubleToIntSafely(String value, String fieldName, int defaultValue) {
        if (StringUtilities.isEmptyOrNull(value)) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("Unable to parse {} value '{}' to Integer, using default: {}", fieldName, value, defaultValue);
            return defaultValue;
        }
    }

    // ==================================================================================
    // ERROR HANDLING AND RESPONSE BUILDERS
    // ==================================================================================

    /**
     * Handles Nautilus service errors gracefully.
     */
    private Mono<FiveGCoverageCheckResponse> handleNautilusServiceError(
            List<CustomerAddress> customerAddresses, Throwable error) {

        logger.error("Handling Nautilus error gracefully: {}", error.getMessage());
        return Mono.just(createFailureResponseForAllAddresses(customerAddresses,
                "5G coverage check failed: " + error.getMessage()));
    }

    /**
     * Creates FiveGCoverageCheckResponse with failure status for all addresses.
     */
    private FiveGCoverageCheckResponse createFailureResponseForAllAddresses(
            List<CustomerAddress> customerAddresses, String errorMessage) {

        List<CustomerAddress> failedAddresses = customerAddresses.stream()
                .map(addr -> {
                    CustomerAddress copy = createCustomerAddressCopy(addr);
                    copy.setQualified(false);
                    copy.setStatusMsg(errorMessage);
                    return copy;
                })
                .collect(Collectors.toList());

        FiveGCoverageCheckResponse response = new FiveGCoverageCheckResponse();
        response.setCustomerAddress(failedAddresses);
        response.setStatus(SmbConstants.SUCCESS_STATUS);  // Still success at API level
        response.setErrors(Collections.emptyList());
        response.setBypassAddressValidation(false);
        return response;
    }

    /**
     * Creates an empty FiveGCoverageCheckResponse.
     */
    private FiveGCoverageCheckResponse createEmptyResponse() {
        FiveGCoverageCheckResponse response = new FiveGCoverageCheckResponse();
        response.setCustomerAddress(Collections.emptyList());
        response.setStatus(SmbConstants.SUCCESS_STATUS);
        response.setErrors(Collections.emptyList());
        response.setBypassAddressValidation(false);
        return response;
    }

    // ==================================================================================
    // UTILITY METHODS - ADDRESS COPYING AND VALIDATION
    // ==================================================================================

    /**
     * Creates a deep copy of CustomerAddress to maintain immutability in reactive streams.
     * This is CRITICAL - never mutate the original address in reactive chains.
     */
    private CustomerAddress createCustomerAddressCopy(CustomerAddress original) {
        if (original == null) {
            return new CustomerAddress();
        }

        CustomerAddress copy = new CustomerAddress();

        // Copy basic address fields
        copy.setAddressLine1(original.getAddressLine1());
        copy.setAddressLine2(original.getAddressLine2());
        copy.setCity(original.getCity());
        copy.setState(original.getState());
        copy.setZipCode(original.getZipCode());
        copy.setZipCodePlus4(original.getZipCodePlus4());
        copy.setCountry(original.getCountry());
        copy.setAddressType(original.getAddressType());

        // Copy parsed address components
        copy.setStreetNum(original.getStreetNum());
        copy.setStreetName(original.getStreetName());
        copy.setType(original.getType());
        copy.setDir(original.getDir());
        copy.setAptNumber(original.getAptNumber());
        copy.setPoBoxNo(original.getPoBoxNo());

        // Copy address descriptors (shallow copy of list)
        if (original.getAddressDesc() != null) {
            copy.setAddressDesc(new ArrayList<>(original.getAddressDesc()));
        }

        // Copy tracking fields
        copy.setEventCorrelationId(original.getEventCorrelationId());

        // Copy status fields
        copy.setQualified(original.isQualified());
        copy.setStatusMsg(original.getStatusMsg());

        return copy;
    }

    /**
     * Creates updated CustomerAddress from Split Address response.
     */
    private CustomerAddress createUpdatedAddressFromSplit(
            CustomerAddress original,
            onevz.soe.smbenrollment.responses.spectrumadapter.Address validatedAddress) {

        CustomerAddress updated = new CustomerAddress();

        // Preserve original metadata
        updated.setAddressType(original.getAddressType());
        updated.setCountry(original.getCountry());
        updated.setAddressDesc(original.getAddressDesc());
        updated.setEventCorrelationId(original.getEventCorrelationId());

        // Set validated/parsed address fields from Split Address response
        updated.setStreetNum(validatedAddress.getStreetNum());
        updated.setStreetName(validatedAddress.getStreetName());
        updated.setAptNumber(validatedAddress.getAptNum());
        updated.setPoBoxNo(validatedAddress.getPobox());
        updated.setType(validatedAddress.getType());
        updated.setDir(validatedAddress.getDir());

        // Reconstruct addressLine1 from parsed components
        String addressLine1 = buildAddressLine1(validatedAddress);
        updated.setAddressLine1(addressLine1);

        // Set addressLine2 - prefer aptNum, fallback to original
        updated.setAddressLine2(StringUtilities.isNotEmptyOrNull(validatedAddress.getAptNum())
                ? validatedAddress.getAptNum()
                : original.getAddressLine2());

        // Set location fields
        updated.setCity(validatedAddress.getCity());
        updated.setState(validatedAddress.getState());
        updated.setZipCode(validatedAddress.getZipCode());
        updated.setZipCodePlus4(validatedAddress.getZipCode4());

        return updated;
    }

    /**
     * Builds addressLine1 from parsed address components.
     */
    private String buildAddressLine1(onevz.soe.smbenrollment.responses.spectrumadapter.Address address) {
        StringBuilder sb = new StringBuilder();

        if (StringUtilities.isNotEmptyOrNull(address.getStreetNum())) {
            sb.append(address.getStreetNum()).append(" ");
        }
        if (StringUtilities.isNotEmptyOrNull(address.getStreetName())) {
            sb.append(address.getStreetName()).append(" ");
        }
        if (StringUtilities.isNotEmptyOrNull(address.getType())) {
            sb.append(address.getType());
        }

        return sb.toString().trim().replaceAll("\\s{2,}", " ");
    }

    /**
     * Validates if customer addresses contain required fields.
     */
    public boolean hasInvalidCustomerAddress(List<CustomerAddress> customerAddresses) {
        return CollectionUtilities.isEmptyOrNull(customerAddresses) ||
                customerAddresses.stream().anyMatch(address ->
                        address == null ||
                        StringUtilities.isEmptyOrNull(address.getAddressLine1()) ||
                        StringUtilities.isEmptyOrNull(address.getZipCode())
                );
    }

    /**
     * Validates Split Address response structure.
     */
    public boolean isValidSplitAddressResponse(SplitAddressResponse splitAddressResponse) {
        return splitAddressResponse != null
                && splitAddressResponse.getData() != null
                && splitAddressResponse.getData().getAddressSplit() != null
                && splitAddressResponse.getData().getAddressSplit().getResponse() != null
                && splitAddressResponse.getData().getAddressSplit().getResponse().getAddress() != null;
    }

    /**
     * Extracts Address from SplitAddressResponse.
     */
    private onevz.soe.smbenrollment.responses.spectrumadapter.Address extractSplitAddress(
            SplitAddressResponse splitAddressResponse) {
        return splitAddressResponse.getData().getAddressSplit().getResponse().getAddress();
    }

    // ==================================================================================
    // RESPONSE WRAPPER BUILDERS
    // ==================================================================================

    private SoeDataWrapper<SmbResponseWrapper<FiveGCoverageCheckResponse>> buildSuccessResponse(
            FiveGCoverageCheckResponse response) {

        SoeDataWrapper<SmbResponseWrapper<FiveGCoverageCheckResponse>> wrapper = new SoeDataWrapper<>();
        SmbResponseWrapper<FiveGCoverageCheckResponse> smbWrapper = new SmbResponseWrapper<>();
        smbWrapper.setResponse(response);
        smbWrapper.setStatusCode(SmbConstants.SUCCESS_STATUS);
        wrapper.setData(smbWrapper);
        return wrapper;
    }

    private Mono<SoeDataWrapper<SmbResponseWrapper<FiveGCoverageCheckResponse>>> buildErrorResponse(Throwable error) {
        logger.error("Failed to process 5G coverage check: {}", error.getMessage(), error);
        SoeDataWrapper<SmbResponseWrapper<FiveGCoverageCheckResponse>> errorWrapper = new SoeDataWrapper<>();
        SmbResponseWrapper<FiveGCoverageCheckResponse> smbWrapper = new SmbResponseWrapper<>();
        smbWrapper.setStatusCode(SmbConstants.FAILURE_STATUS);
        smbWrapper.setErrors(ValidationUtils.formatErrorMessage(error.getMessage()));
        errorWrapper.setData(smbWrapper);
        return Mono.just(errorWrapper);
    }
}
