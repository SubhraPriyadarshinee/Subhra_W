package com.walmart.move.nim.receiving.acc.service;

import static java.util.Objects.isNull;

import com.google.common.collect.Iterables;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.model.facility.mdm.BusinessUnitDetail;
import com.walmart.move.nim.receiving.acc.model.facility.mdm.DateRange;
import com.walmart.move.nim.receiving.acc.model.facility.mdm.DcAlignment;
import com.walmart.move.nim.receiving.acc.model.facility.mdm.FacilityList;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class FacilityMDMImpl implements FacilityMDM {

  private static final Logger LOGGER = LoggerFactory.getLogger(FacilityMDMImpl.class);

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  /**
   * Checks if current time stamp lies between provided range
   *
   * @param start start date
   * @param end end date
   * @return Return true if current timestamp lies between the date range else false
   */
  private boolean isCurrentDateAfterStartAndBeforeEnd(Date start, Date end) {
    Date now = new Date();
    return now.after(start) && now.before(end);
  }

  /**
   * Get Store to DC alignment for list for stores
   *
   * @param storeList list of store no.
   * @param httpHeaders headers
   * @return map of store and dc
   */
  private ResponseEntity<String> getStoreToDCMappingFromFacilityMDM(
      List<String> storeList, HttpHeaders httpHeaders) {
    httpHeaders.add(ReceivingConstants.HEADER_AUTH_KEY, accManagedConfig.getFacilityMDMApiKey());
    String uri =
        accManagedConfig.getFacilityMDMBaseUrl() + accManagedConfig.getFacilityMDMDCAlignmentPath();

    ResponseEntity<String> dcAlignment = null;
    try {
      dcAlignment =
          restConnector.post(
              uri, JacksonParser.writeValueAsString(storeList), httpHeaders, String.class);
    } catch (Exception exception) {
      LOGGER.error(
          "Got error in dc alignment call for store list batch = {} , errorMessage = {} ",
          storeList.toString(),
          exception.getMessage());
    }
    return dcAlignment;
  }

  /**
   * Fnd valid alignment for store from a list of alignment values
   *
   * @param buNumberCC store no.
   * @param dcAlignment list of alignments for a store
   * @return map of store and dc
   */
  private Map<String, Integer> getValidAlignments(
      String buNumberCC, List<DcAlignment> dcAlignment) {
    Map<String, Integer> storeToDCMap = new HashMap<>();
    Map<String, Date> dcLatestDateMap = new HashMap<>();
    String buNumber;
    for (DcAlignment alignment : dcAlignment) {
      int dcNumber = alignment.getNumber();
      DcAlignment.DcCategory category = alignment.getCategory();
      if (Objects.nonNull(category) && !CollectionUtils.isEmpty(category.getSubCategory()))
        for (DcAlignment.DcCategory.DcSubCategory subCategory : category.getSubCategory()) {
          String subCategoryCode = subCategory.getCode();
          if (!StringUtils.isEmpty(subCategoryCode)
              && subCategoryCode.equalsIgnoreCase(
                  accManagedConfig.getFacilityMDMDCAlignmentSubCategory())) {
            DateRange dateRange = subCategory.getDateRange();
            if (Objects.nonNull(dateRange))
              if (isCurrentDateAfterStartAndBeforeEnd(dateRange.getStart(), dateRange.getEnd())) {
                /**
                 * * Pseudocode If that DC is already added Then check if next dc's start date is
                 * greater than previous dc' start date if yes then next DC is the correct alignment
                 * if no then previous DC is the correct alignment If DC is not added Then add it to
                 * our map *
                 */
                buNumber = ACCUtils.extractNumber(buNumberCC);
                Date lastComputedStartDate = dcLatestDateMap.get(buNumber);
                if (isNull(lastComputedStartDate)
                    || lastComputedStartDate.before(dateRange.getStart())) {
                  dcLatestDateMap.put(buNumber, dateRange.getStart());
                  // TODO: Need to check if we want to use store id appended with country code or
                  // not
                  storeToDCMap.put(buNumber, dcNumber);
                }
              }
          }
        }
    }
    return storeToDCMap;
  }

  /**
   * Get store to DC mapping
   *
   * @param storeList list of store no.
   * @param httpHeaders headers
   * @return Map containing key as store and DC as value
   */
  @Override
  public Map<String, Integer> getStoreToDCMapping(List<String> storeList, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Store to DC Alignment: Partition the list and find store to DC alignment in batches. Store list size {}:",
        storeList.size());
    FacilityList facilityList;
    Map<String, Integer> storeToDCMapping = new HashMap<>();
    for (List<String> storeListBatch :
        Iterables.partition(storeList, accManagedConfig.getFacilityMDMApiCallBatchSize())) {
      try {
        ResponseEntity<String> responseFromMDM =
            getStoreToDCMappingFromFacilityMDM(storeListBatch, httpHeaders);
        if (!isNull(responseFromMDM) && responseFromMDM.getStatusCode() == HttpStatus.OK) {
          facilityList =
              JacksonParser.convertJsonToObject(responseFromMDM.getBody(), FacilityList.class);
          LOGGER.info(
              "Alignment - found Location: {}  nor found location: {}",
              facilityList.getFoundBusinessUnits().size(),
              facilityList.getNotFoundBusinessUnits().size());
          for (BusinessUnitDetail foundBusinessUnit : facilityList.getFoundBusinessUnits()) {
            storeToDCMapping.putAll(
                getValidAlignments(
                    foundBusinessUnit.getBuNumberCC(), foundBusinessUnit.getDcAlignment()));
          }
        } else {
          LOGGER.info("Erroneous response form facility MDM response: {}", responseFromMDM);
        }
      } catch (Exception exception) {
        LOGGER.error(
            "Get Store-DC mapping failed for stores: {} with exception: {}",
            storeList.toString(),
            exception);
      }
    }
    return storeToDCMapping;
  }
}
