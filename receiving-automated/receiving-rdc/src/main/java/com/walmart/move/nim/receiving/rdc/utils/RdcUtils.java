package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.label.LabelDataMiscInfo;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;

public class RdcUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcUtils.class);
  private static final Gson gson = new Gson();

  private RdcUtils() {}

  public static Date stringToDate(String dateString) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    try {
      return dateFormat.parse(dateString);
    } catch (ParseException e) {
    }
    return null;
  }

  public static String getLabelFormatDateAndTime(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss");
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static String getLabelFormatDate(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static String getLabelFormatTime(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static String getSSTKTimestampLabelFormatDate(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static String getSSTKTimestampLabelFormatTime(ZonedDateTime zonedDateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static ZonedDateTime getZonedDateTimeByDCTimezone(String dcTimeZone, Date inputDate) {
    dcTimeZone = StringUtils.isBlank(dcTimeZone) ? UTC_TIME_ZONE : dcTimeZone;
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    final Instant nowInUtc = inputDate.toInstant();
    return ZonedDateTime.ofInstant(nowInUtc, dcZoneId);
  }

  public static Date getDateByDCTimezone(String dcTimeZone, Date inputDate) {
    if (inputDate == null) return null;
    return Date.from(getZonedDateTimeByDCTimezone(dcTimeZone, inputDate).toInstant());
  }

  public static String getSSTKTimestampLabelTimeCode(ZonedDateTime zonedDateTime) {
    if (Objects.isNull(zonedDateTime)) return StringUtils.EMPTY;
    LocalTime localZonedTime =
        LocalTime.of(
            zonedDateTime.toLocalTime().getHour(),
            zonedDateTime.toLocalTime().getMinute(),
            zonedDateTime.toLocalTime().getSecond());
    return getTimeCode(localZonedTime);
  }

  /**
   * This method compares DC local time with the associates shift schedule time and returns the
   * specific time code for the current shift
   *
   * <p>12am - 2:59:59 am = 1; 3am - 5:59:59 am = 2; 6am - 8:59:59 am = 3; 9am - 11:59:59 am = 4;
   * 12pm - 2:59:59 pm = 5; 3pm - 5:59:59 pm = 6; 6pm - 8:59:59 pm = 7; 9pm - 11:59:59 pm = 8;
   *
   * @param localZonedTime
   * @return String
   */
  public static String getTimeCode(LocalTime localZonedTime) {
    LocalTime localtime000000 = LocalTime.of(0, 0, 0);
    LocalTime localtime025959 = LocalTime.of(2, 59, 59);
    LocalTime localtime030000 = LocalTime.of(3, 0, 0);
    LocalTime localtime055959 = LocalTime.of(5, 59, 59);
    LocalTime localtime060000 = LocalTime.of(6, 0, 0);
    LocalTime localtime085959 = LocalTime.of(8, 59, 59);
    LocalTime localtime090000 = LocalTime.of(9, 0, 0);
    LocalTime localtime115959 = LocalTime.of(11, 59, 59);
    LocalTime localtime120000 = LocalTime.of(12, 0, 0);
    LocalTime localtime145959 = LocalTime.of(14, 59, 59);
    LocalTime localtime150000 = LocalTime.of(15, 0, 0);
    LocalTime localtime175959 = LocalTime.of(17, 59, 59);
    LocalTime localtime180000 = LocalTime.of(18, 0, 0);
    LocalTime localtime205959 = LocalTime.of(20, 59, 59);
    LocalTime localtime210000 = LocalTime.of(21, 0, 0);
    LocalTime localtime235959 = LocalTime.of(23, 59, 59);

    if (localZonedTime.compareTo(localtime000000) >= 0
        && localZonedTime.compareTo(localtime025959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_1;
    if (localZonedTime.compareTo(localtime030000) >= 0
        && localZonedTime.compareTo(localtime055959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_2;
    if (localZonedTime.compareTo(localtime060000) >= 0
        && localZonedTime.compareTo(localtime085959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_3;
    if (localZonedTime.compareTo(localtime090000) >= 0
        && localZonedTime.compareTo(localtime115959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_4;
    if (localZonedTime.compareTo(localtime120000) >= 0
        && localZonedTime.compareTo(localtime145959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_5;
    if (localZonedTime.compareTo(localtime150000) >= 0
        && localZonedTime.compareTo(localtime175959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_6;
    if (localZonedTime.compareTo(localtime180000) >= 0
        && localZonedTime.compareTo(localtime205959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_7;
    if (localZonedTime.compareTo(localtime210000) >= 0
        && localZonedTime.compareTo(localtime235959) <= 0)
      return RdcConstants.TIMESTAMP_LABEL_TIME_CODE_8;

    return StringUtils.EMPTY;
  }

  public static void validateMandatoryRequestHeaders(HttpHeaders httpHeaders) {
    String wmtLocationId = httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID);
    String locationType = httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE);
    String sccCode = httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE);
    if (isBlank(wmtLocationId)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA,
          String.format(INVALID_HEADER_ERROR_MSG, RdcConstants.WFT_LOCATION_ID, wmtLocationId));
    }
    if (isBlank(locationType)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA,
          String.format(INVALID_HEADER_ERROR_MSG, RdcConstants.WFT_LOCATION_TYPE, locationType));
    }
    if (isBlank(sccCode)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA,
          String.format(INVALID_HEADER_ERROR_MSG, RdcConstants.WFT_SCC_CODE, sccCode));
    }
  }

  public static String getStringValue(Object input) {
    return Objects.nonNull(input) ? String.valueOf(input) : StringUtils.EMPTY;
  }

  public static boolean validateIfDestTypeIsMFC(String destType, boolean isMfcIndicatorEnabled) {
    return isMfcIndicatorEnabled && LabelType.MFC.name().equals(destType);
  }

  public static String getExternalServiceBaseUrlByTenant(String baseUrl) {
    if (Objects.nonNull(baseUrl)) {
      JsonObject tenantBasedUrlJson = gson.fromJson(baseUrl, JsonObject.class);

      JsonElement ServiceUrlJsonElement =
          tenantBasedUrlJson.get(TenantContext.getFacilityNum().toString());
      if (Objects.nonNull(ServiceUrlJsonElement)) {
        return ServiceUrlJsonElement.getAsString();
      } else {
        throw new ReceivingInternalException(
            ExceptionCodes.CONFIGURATION_ERROR,
            String.format(
                ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
      }
    } else {
      LOGGER.error("Base url is empty in getServiceBaseUrlByTenant()");
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(
              ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
    }
  }

  public static ReceivingBadDataException convertToReceivingBadDataException(
      ReceivingException receivingException) {
    String errorCode =
        StringUtils.isNotEmpty(receivingException.getErrorResponse().getErrorCode())
            ? receivingException.getErrorResponse().getErrorCode()
            : ExceptionCodes.RECEIVING_INTERNAL_ERROR;
    return new ReceivingBadDataException(
        errorCode, receivingException.getErrorResponse().getErrorMessage().toString());
  }

  public static String getMappedTenant(String nimRdsBaseUrl) {
    return nimRdsBaseUrl.split(ReceivingConstants.NGR_SITE_ID_PATTERN)[1];
  }

  /**
   * @param slottingPalletResponse
   * @param deliveryDocumentLine
   */
  public static void populateSlotInfoInDeliveryDocument(
      SlottingPalletResponse slottingPalletResponse, DeliveryDocumentLine deliveryDocumentLine) {
    if (Objects.nonNull(slottingPalletResponse)
        && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
      ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
      itemData.setPrimeSlot(slottingPalletResponse.getLocations().get(0).getLocation());
      itemData.setPrimeSlotSize(
          (int) slottingPalletResponse.getLocations().get(0).getLocationSize());
      if (org.apache.commons.lang.StringUtils.isNotEmpty(
          slottingPalletResponse.getLocations().get(0).getAsrsAlignment())) {
        itemData.setAsrsAlignment(slottingPalletResponse.getLocations().get(0).getAsrsAlignment());
      }
      itemData.setSlotType(slottingPalletResponse.getLocations().get(0).getSlotType());
      deliveryDocumentLine.setAdditionalInfo(itemData);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOTTING_INVALID_RESPONSE,
          String.format(
              ReceivingConstants.SMART_SLOTTING_INVALID_RESPONSE,
              deliveryDocumentLine.getItemNbr()),
          deliveryDocumentLine.getItemNbr().toString());
    }
  }

  /**
   * Returns new HttpHeaders using upstream httpHeaders and removing all not required headers for
   * down streams
   *
   * @param httpHeaders Http headers received by controller
   * @return httpheaders that can be forwarded to downstream processes for rest calls
   */
  public static HttpHeaders getForwardableHttpHeadersWithLocationInfo(HttpHeaders httpHeaders) {
    HttpHeaders forwardableHeaders = new HttpHeaders();

    String correlationId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    if (StringUtils.isNotBlank(httpHeaders.getFirst(USER_ID_HEADER_KEY)))
      forwardableHeaders.add(USER_ID_HEADER_KEY, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    if (StringUtils.isNotBlank(correlationId))
      forwardableHeaders.add(CORRELATION_ID_HEADER_KEY, correlationId);
    forwardableHeaders.add(TENENT_FACLITYNUM, getFacilityNum().toString());
    forwardableHeaders.add(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    if (StringUtils.isNotBlank(httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID))) {
      forwardableHeaders.add(
          RdcConstants.WFT_LOCATION_ID, httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID));
    }
    if (StringUtils.isNotBlank(httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE))) {
      forwardableHeaders.add(
          RdcConstants.WFT_LOCATION_TYPE, httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE));
    }
    if (StringUtils.isNotBlank(httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE))) {
      forwardableHeaders.add(
          RdcConstants.WFT_SCC_CODE, httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE));
    }
    if (StringUtils.isNotBlank(httpHeaders.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY))) {
      forwardableHeaders.add(
          RdcConstants.DA_RECEIVING_CAPABILITY,
          httpHeaders.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY));
    }
    forwardableHeaders.add(CONTENT_TYPE, APPLICATION_JSON);

    return forwardableHeaders;
  }

  public static int getBreakPackRatio(DeliveryDocumentLine deliveryDocumentLine) {
    int breakPackRatio;
    if (ObjectUtils.allNotNull(
        deliveryDocumentLine.getVendorPack(), deliveryDocumentLine.getWarehousePack())) {
      breakPackRatio =
          Math.floorDiv(
              deliveryDocumentLine.getVendorPack(), deliveryDocumentLine.getWarehousePack());
    } else {
      breakPackRatio = RdcConstants.DEFAULT_BREAK_PACK_RATIO;
    }
    return breakPackRatio;
  }

  public static String getPackTypeCodeByBreakPackRatio(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcUtils.getBreakPackRatio(deliveryDocumentLine) > 1
        ? RdcConstants.BREAK_PACK_TYPE_CODE
        : RdcConstants.CASE_PACK_TYPE_CODE;
  }

  public static boolean isBreakPackItem(Integer vendorPack, Integer warehousePack) {
    if (ObjectUtils.allNotNull(vendorPack, warehousePack)) {
      return Math.floorDiv(vendorPack, warehousePack) > RdcConstants.DEFAULT_BREAK_PACK_RATIO;
    }
    return false;
  }

  public static Integer pickQuantity(
      Integer receivedQty, DeliveryDocumentLine deliveryDocumentLine) {
    String packAndHandlingMethod =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    if (RdcConstants.DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE.equals(packAndHandlingMethod)
        || RdcConstants.DA_CASEPACK_VOICE_PUT_HANDLING_METHODS_MAP.containsKey(
            packAndHandlingMethod)
        || RdcConstants.DA_BREAKPACK_VOICE_PUT_HANDLING_METHODS_MAP.containsKey(
            packAndHandlingMethod)) {
      return receivedQty;
    } else {
      return receivedQty * RdcUtils.getBreakPackRatio(deliveryDocumentLine);
    }
  }

  /**
   * @param deliveryDocumentLine
   * @return
   */
  public static boolean isBreakPackConveyPicks(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE.equalsIgnoreCase(
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
  }

  public static boolean isNonConveyableItem(DeliveryDocumentLine deliveryDocumentLine) {
    return Arrays.asList(RdcConstants.NON_CON_ITEM_HANDLING_CODES)
        .contains(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
  }

  /**
   * User will have both SSTK & DA Receiving capability
   *
   * @param httpHeaders
   * @return
   */
  public static boolean isUserAllowedToReceiveDaFreight(HttpHeaders httpHeaders) {
    boolean isDaReceivingAllowed = false;
    if (Objects.nonNull(httpHeaders.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY))) {
      isDaReceivingAllowed =
          Boolean.parseBoolean(httpHeaders.getFirst(RdcConstants.DA_RECEIVING_CAPABILITY));
    }
    return isDaReceivingAllowed;
  }

  /**
   * @param deliveryDocument
   * @param totalReceivedQty
   * @return
   */
  public static void populateReceivedQtyDetailsInDeliveryDocument(
      DeliveryDocument deliveryDocument, Integer totalReceivedQty) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Integer totalOrderedQty = deliveryDocumentLine.getTotalOrderQty();
    int openQty = totalOrderedQty - totalReceivedQty;
    int maxReceivedQty = totalOrderedQty + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(totalReceivedQty);
    deliveryDocument.getDeliveryDocumentLines().get(0).setOpenQty(openQty);
    deliveryDocument.getDeliveryDocumentLines().get(0).setMaxReceiveQty(maxReceivedQty);
  }

  /**
   * This method parse RDS DSDC Response Errors and provide the error message only. Sample Input for
   * RDS Error Message "RDS DSDC validation failed => Error: ASN information was not found"
   *
   * @param dsdcReceiveResponse
   * @return
   */
  public static String getparsedDsdcErrorMessage(DsdcReceiveResponse dsdcReceiveResponse) {
    String errorMessage =
        StringUtils.replaceAll(
            dsdcReceiveResponse.getMessage(),
            RdcConstants.DSDC_PACK_ERROR_MSG_QUOTES_REPLACE,
            StringUtils.EMPTY);
    List<String> message =
        Arrays.asList(errorMessage.split(RdcConstants.DSDC_PACK_ERROR_MSG_REGEX));
    return (!CollectionUtils.isEmpty(message) && message.size() > 0)
        ? StringUtils.trim(message.get(1))
        : null;
  }

  public static String getSiteId() {
    String siteId = StringUtils.leftPad(TenantContext.getFacilityNum().toString(), 5, "0");
    return siteId;
  }

  public static boolean isWorkStationAndScanToPrintReceivingModeEnabled(String featureType) {
    return Arrays.asList(RdcConstants.DA_WORK_STATION_AND_SCAN_TO_PRINT_FEATURE_TYPES)
        .contains(featureType);
  }

  /**
   * This method returns the store range for the list of zones returned by RDS for more than 1
   * destination eligible DA freights (PUT). It returns zone range as 1-4
   *
   * @param destinations
   * @return
   */
  public static String getZoneRange(List<Destination> destinations) {
    String zoneRange = null;
    List<String> zoneList =
        destinations
            .stream()
            .map(Destination::getZone)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(zoneList)) {
      List<Integer> zoneListSorted =
          zoneList.stream().map(Integer::parseInt).distinct().collect(Collectors.toList());
      Optional<Integer> minZone =
          zoneListSorted.stream().reduce((zone1, zone2) -> Math.min(zone1, zone2));
      Optional<Integer> maxZone =
          zoneListSorted.stream().reduce((zone1, zone2) -> Math.max(zone1, zone2));
      zoneRange = minZone.map(integer -> integer + "-" + maxZone.get()).orElse(StringUtils.EMPTY);
      return zoneRange;
    }
    return StringUtils.EMPTY;
  }

  /**
   * This method gets the received qty for Slotting request. If user selects Multi Pallet receiving
   * then we accept only 1 pallet for the time being in Atlas.
   *
   * @param receiveInstructionRequest
   * @param receiveQty
   * @return
   */
  public static int getReceiveQtyForSlottingRequest(
      ReceiveInstructionRequest receiveInstructionRequest, int receiveQty) {
    boolean isMultiPalletReceivingEnabledForAutoSlotting = false;
    boolean isManualSlotting =
        Objects.nonNull(receiveInstructionRequest)
            && Objects.nonNull(receiveInstructionRequest.getSlotDetails())
            && Objects.nonNull(receiveInstructionRequest.getSlotDetails().getSlot());
    // allow only 1 pallet receiving for atlas items
    if (Objects.nonNull(receiveInstructionRequest)) {
      isMultiPalletReceivingEnabledForAutoSlotting =
          !CollectionUtils.isEmpty(receiveInstructionRequest.getPalletQuantities());
    }
    if (isMultiPalletReceivingEnabledForAutoSlotting) {
      receiveQty = receiveInstructionRequest.getPalletQuantities().get(0).getQuantity();
    } else if (isManualSlotting) {
      receiveQty = receiveInstructionRequest.getQuantity();
    }
    return receiveQty;
  }

  public static void buildCommonReceivedContainerDetails(
      String labelTrackingId,
      ReceivedContainer receivedContainer,
      DeliveryDocument deliveryDocument) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    receivedContainer.setLabelTrackingId(labelTrackingId);
    receivedContainer.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    receivedContainer.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    receivedContainer.setPocode(deliveryDocument.getPoTypeCode());
    receivedContainer.setPoevent(deliveryDocumentLine.getEvent());
    String departmentNumber =
        StringUtils.isNotBlank(deliveryDocumentLine.getDepartment())
            ? deliveryDocumentLine.getDepartment()
            : StringUtils.isNotBlank(deliveryDocument.getDeptNumber())
                ? deliveryDocument.getDeptNumber()
                : StringUtils.EMPTY;
    if (StringUtils.isNotBlank(departmentNumber)) {
      receivedContainer.setDepartment(Integer.parseInt(deliveryDocumentLine.getDepartment()));
    }
  }

  /**
   * Preparing distributions
   *
   * @param instructionDownloadDistributionsDTOS
   * @return
   */
  public static List<Distribution> prepareDistributions(
      List<InstructionDownloadDistributionsDTO> instructionDownloadDistributionsDTOS,
      String buNumber) {
    return instructionDownloadDistributionsDTOS
        .stream()
        .map(
            instructionDownloadDistribution -> {
              Distribution distribution = new Distribution();
              distribution.setItem(
                  ReceivingUtils.convertJsonToMap(
                      gson.toJson(instructionDownloadDistribution.getItem())));
              distribution.setOrderId(instructionDownloadDistribution.getOrderId());
              if (StringUtils.isNotBlank(buNumber)) {
                distribution.setDestNbr(Integer.parseInt(buNumber));
              }
              return distribution;
            })
        .collect(Collectors.toList());
  }

  public static String getLabelFormatDateAndTimeByTimeInMilliSeconds(long milliSeconds) {
    Date date = new Date(milliSeconds);
    DateFormat formatter = new SimpleDateFormat(RdcConstants.REPRINT_LABEL_TIMESTAMP_PATTERN);
    return formatter.format(date);
  }

  public static List<LabelData> filterLabelDataWith25DigitLpns(List<LabelData> labelDataList) {
    return labelDataList
        .stream()
        .filter(labelData -> labelData.getTrackingId().length() == ReceivingConstants.LPN_LENGTH_25)
        .collect(Collectors.toList());
  }

  /**
   * Creates item update request for Hawkeye
   *
   * @param itemNumber
   * @param deliveryNumber
   * @param rejectReason
   * @param catalogGtin
   * @param isHandlingCodeUpdate
   * @return
   */
  public static HawkeyeItemUpdateRequest createHawkeyeItemUpdateRequest(
      Long itemNumber,
      Long deliveryNumber,
      RejectReason rejectReason,
      String catalogGtin,
      boolean isHandlingCodeUpdate) {

    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
        HawkeyeItemUpdateRequest.builder()
            .itemNumber(String.valueOf(itemNumber))
            .groupNumber(Objects.nonNull(deliveryNumber) ? String.valueOf(deliveryNumber) : null)
            .build();

    if (isHandlingCodeUpdate) {
      hawkeyeItemUpdateRequest.setReject(
          Objects.nonNull(rejectReason) ? rejectReason.getRejectCode() : StringUtils.EMPTY);
    } else {
      hawkeyeItemUpdateRequest.setCatalogGTIN(catalogGtin);
    }
    return hawkeyeItemUpdateRequest;
  }

  /**
   * Validate break pack non conveyable or not
   *
   * @param deliveryDocumentLine
   * @return
   */
  public static boolean isBreakPackNonConveyable(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.DA_BREAK_PACK_NON_CONVEYABLE_ITEM_HANDLING_CODE.equalsIgnoreCase(
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
  }

  public static Set<Long> fetchItemNumbersFromDeliveryDocuments(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments
        .stream()
        .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
        .map(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine::getItemNbr)
        .collect(Collectors.toSet());
  }
  /**
   * This method populates slotting details to DeliveryDocumentLine additional info from LabelData
   * MiscInfo object for SSTK labels
   *
   * @param labelData
   * @param deliveryDocumentLine
   */
  public static void populateSlotInfoInDeliveryDocumentFromLabelData(
      LabelData labelData, DeliveryDocumentLine deliveryDocumentLine) {
    LabelDataMiscInfo labelDataMiscInfo =
        gson.fromJson(labelData.getLabelDataMiscInfo(), LabelDataMiscInfo.class);
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    itemData.setPrimeSlot(labelDataMiscInfo.getLocation());
    itemData.setPrimeSlotSize(labelDataMiscInfo.getLocationSize());
    if (org.apache.commons.lang.StringUtils.isNotEmpty(labelDataMiscInfo.getAsrsAlignment())) {
      itemData.setAsrsAlignment(labelDataMiscInfo.getAsrsAlignment());
    }
    itemData.setSlotType(labelDataMiscInfo.getSlotType());
    deliveryDocumentLine.setAdditionalInfo(itemData);
  }

  /**
   * Validate break pack NonCon RtsPut atlas item
   *
   * @param deliveryDocumentLine
   * @return
   */
  public static boolean isBreakPackNonConRtsPutItem(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.BREAK_PACK_TYPE_CODE.equalsIgnoreCase(
            deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())
        && RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE.equalsIgnoreCase(
            deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
  }
}
