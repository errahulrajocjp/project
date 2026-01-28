# Check 5G Coverage API - Field Mapping Reference

## Response Structure Match Verification

### ✅ FiveGCoverageCheckResponse Fields

| Expected Field | POJO Field | Mapped? | Value |
|----------------|------------|---------|-------|
| `header` | `header` | ✅ | `new HashMap<>()` |
| `errors` | `errors` | ✅ | `Collections.emptyList()` |
| `status` | `status` | ✅ | `SmbConstants.SUCCESS_STATUS` (1) |
| `customerAddress` | `customerAddress` | ✅ | List of enriched addresses |
| `isBypassAddressValidation` | `isBypassAddressValidation` | ✅ | `false` |

---

### ✅ CustomerAddress Field Mappings

#### From Original Request (Preserved)
| CustomerAddress Field | Source | Notes |
|-----------------------|--------|-------|
| `addressLine1` | Original request | Preserved |
| `addressLine2` | Original request | Preserved |
| `city` | Original request | Preserved |
| `state` | Original request | Preserved |
| `zipCode` | Original request | Preserved |
| `addressType` | Original request | e.g., "Billing", "ECPD" |
| `country` | Original request | Preserved |

#### From Split Address Service (After validation)
| CustomerAddress Field | Source | Notes |
|-----------------------|--------|-------|
| `streetNum` | `Address.streetNum` | Parsed component |
| `streetName` | `Address.streetName` | Parsed component |
| `aptNumber` | `Address.aptNum` | Parsed component |
| `poBoxNo` | `Address.pobox` | Parsed component |
| `type` | `Address.type` | e.g., "AV", "ST" |
| `dir` | `Address.dir` | Direction |
| `zipCodePlus4` | `Address.zipCode4` | Extended ZIP |

#### From Nautilus Qualification Service
| CustomerAddress Field | BulkAddressQualificationResponse Source | Type Conversion |
|-----------------------|----------------------------------------|-----------------|
| `qualified` (isQualified) | `fiveGHomeQualified` | Direct boolean |
| `statusMsg` | `returnMessage` or generated | String |
| `qualifiedCBand` | `cBandQualified` | Direct boolean |
| `qualified4GHome` | `LTEQualified` | Direct boolean |
| `addressId` | `addressInfo.addressId` | Direct String |
| `subLocationId` | `addressInfo.locationId` | Direct String |
| `fuzeSiteId` | `addressInfo.fuzeSiteId` | String → Integer |
| `sector` | `addressInfo.sector` | String → Integer |
| `availableCapacityCBand` | `availableCapacityInfo.cbandCapacity` | String "15.0" → Integer 15 |
| `availableCapacity4GHome` | `availableCapacityInfo.lteCapacity` | String → Integer |
| `bundleList` | `eligibilities.fiveGHomeBundle[].bundleName` | List extraction |
| `maxSpeed` | `eligibilities.availableSpeedTier` | Direct String |
| `qualCBandInstallTypes` | `qualCBandInstallTypes` (list) or `installType` | List or wrap single |
| `fwaCbandDownloadSpeedList` | `fwaCbandDownloadSpeedList` | Direct List<String> |
| `fwaCbandUploadSpeedList` | `fwaCbandUploadSpeedList` | Direct List<String> |
| `cbandBYODLine` | N/A | Default: `false` |
| `floorPlanAvl` | N/A | Default: `false` |
| `eventCorrelationId` | Generated | Format: `{zipCode}_{timestamp}_{suffix}` |

---

## Expected vs Actual Response Example

### Expected (from check5GCoverage-API-example.md):
```json
{
  "header": {},
  "errors": [],
  "status": 1,
  "customerAddress": [
    {
      "addressLine1": "1 VERIZON WAY",
      "city": "BASKING RIDGE",
      "state": "NJ",
      "zipCode": "07920",
      "addressType": "Billing",
      "statusMsg": " This address qualifies for 5G service. ",
      "eventCorrelationId": "07920_1767692837686_S7",
      "addressId": "300512786823",
      "subLocationId": "300512786823",
      "qualifiedCBand": true,
      "qualified": false,
      "qualified4GHome": true,
      "availableCapacityCBand": 0,
      "availableCapacity4GHome": 0,
      "fwaCbandDownloadSpeedList": ["150"],
      "fwaCbandUploadSpeedList": ["15", "45", "10", "10"],
      "fuzeSiteId": 0,
      "sector": 0,
      "cbandBYODLine": false,
      "floorPlanAvl": false
    }
  ],
  "isBypassAddressValidation": false
}
```

### Will Be Produced By Implementation: ✅ YES (with corrected POJOs)

---

## ⚠️ IMPORTANT: Required POJO Updates

### 1. FiveGCoverageCheckResponse - ADD these fields:
```java
private Map<String, Object> header = new HashMap<>();
private List<Object> errors;
private Integer status;
private boolean isBypassAddressValidation = false;
```

### 2. BulkAddressQualificationResponse - ADD these fields:
```java
private List<String> fwaCbandDownloadSpeedList;
private List<String> fwaCbandUploadSpeedList;
private List<String> qualCBandInstallTypes;
```

### 3. Verify Nautilus API Response
Please confirm the actual Nautilus API response includes:
- `fwaCbandDownloadSpeedList`
- `fwaCbandUploadSpeedList`
- `qualCBandInstallTypes`

If these fields come from a different source, the mapping logic needs adjustment.

---

## Record Identifier Correlation

```
Request Index    recordIdentifier    Response Lookup
-----------      ---------------     ---------------
     0       →        "1"        →   qualificationMap.get("1")
     1       →        "2"        →   qualificationMap.get("2")
     2       →        "3"        →   qualificationMap.get("3")
     ...
```

**1-BASED indexing per Nautilus API specification!**
