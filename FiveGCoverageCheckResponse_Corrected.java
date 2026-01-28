package onevz.soe.smbenrollment.responses.spectrumadapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import onevz.soe.smbenrollment.model.CustomerAddress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response object for Check 5G Coverage API.
 * 
 * This matches the expected API response structure:
 * {
 *   "header": {},
 *   "errors": [],
 *   "status": 1,
 *   "customerAddress": [...],
 *   "isBypassAddressValidation": false
 * }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FiveGCoverageCheckResponse {

    /**
     * Response header (typically empty)
     */
    private Map<String, Object> header = new HashMap<>();

    /**
     * List of errors if any occurred during processing
     */
    private List<Object> errors;

    /**
     * Response status: 1 = success, 0 = failure
     */
    private Integer status;

    /**
     * List of customer addresses with 5G coverage qualification details
     */
    private List<CustomerAddress> customerAddress;

    /**
     * Indicates if address validation was bypassed
     */
    private boolean isBypassAddressValidation = false;
}
