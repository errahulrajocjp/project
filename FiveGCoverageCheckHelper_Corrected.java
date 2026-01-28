package onevz.soe.smbenrollment.helper;

import onevz.soe.smbenrollment.model.CustomerAddress;
import onevz.soe.smbenrollment.requests.spectrumadapter.AddressLineBased;
import onevz.soe.smbenrollment.requests.spectrumadapter.NautilusAddressInfo;
import onevz.soe.smbenrollment.requests.spectrumadapter.NautilusQualificationRequest;
import onevz.soe.smbenrollment.requests.spectrumadapter.SplitAddressRequest;
import onevz.soe.smbenrollment.constants.SmbConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FiveGCoverageCheckHelper {

    private static final Logger logger = LoggerFactory.getLogger(FiveGCoverageCheckHelper.class);

    /**
     * Builds SplitAddressRequest from CustomerAddress for Split Address service call.
     */
    public SplitAddressRequest buildSplitCustomerAddressRequest(CustomerAddress customerAddress) {
        SplitAddressRequest splitRequest = new SplitAddressRequest();
        AddressLineBased addressLineBased = new AddressLineBased();

        addressLineBased.setAddressLine1(customerAddress.getAddressLine1());
        addressLineBased.setAddressLine2(customerAddress.getAddressLine2());
        addressLineBased.setCity(customerAddress.getCity());
        addressLineBased.setState(customerAddress.getState());
        addressLineBased.setZipCode(customerAddress.getZipCode());

        splitRequest.setAddressLineBased(addressLineBased);
        splitRequest.setClientAppName(SmbConstants.CLIENT_ID);
        splitRequest.setStrictValidationRequired(false);
        splitRequest.setTraffic(SmbConstants.TRAFFIC);

        return splitRequest;
    }

    /**
     * Builds NautilusQualificationRequest from list of CustomerAddresses.
     * 
     * IMPORTANT: recordIdentifier is 1-based (starts from "1", "2", "3"...)
     * This matches the Nautilus API specification.
     */
    public NautilusQualificationRequest buildNautilusQualificationRequest(List<CustomerAddress> customerAddresses) {
        NautilusQualificationRequest request = new NautilusQualificationRequest();
        request.setIncludeCband(true);
        request.setRequestType("Bulk");

        List<NautilusAddressInfo> addressList = new ArrayList<>();
        
        for (int i = 0; i < customerAddresses.size(); i++) {
            CustomerAddress addr = customerAddresses.get(i);
            NautilusAddressInfo nautilusAddr = new NautilusAddressInfo();
            
            // IMPORTANT: recordIdentifier is 1-based per API specification
            nautilusAddr.setRecordIdentifier(String.valueOf(i + 1));  // "1", "2", "3"...
            
            nautilusAddr.setAddressLine1(addr.getAddressLine1());
            nautilusAddr.setAddressLine2(addr.getAddressLine2());
            nautilusAddr.setCity(addr.getCity());
            nautilusAddr.setState(addr.getState());
            nautilusAddr.setZip(addr.getZipCode());
            
            addressList.add(nautilusAddr);
        }

        request.setAddressList(addressList);

        logger.info("Built Nautilus qualification request with {} addresses", addressList.size());
        return request;
    }
}
