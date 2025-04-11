package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.FAILED;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.HAUL;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.OPEN;
import static com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient.PUTAWAY;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_DATA;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getUserId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_DEMATIC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_SCHAEFER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_SWISSLOG;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.springframework.util.StringUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Error;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Found;
import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.DeliveryMessageEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.LabelServiceImpl;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * common utils to be used across project must contain only static methods this class cannot have
 * state variables except logger or performance instrumetation cases.
 *
 * @author a0s01qi
 */
public class ReceivingUtils {
  private static final Logger log = LoggerFactory.getLogger(ReceivingUtils.class);

  /**
   * Gets Atlas' compulsory headers.
   *
   * @return the headers
   */
  public static HttpHeaders getHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(TENENT_FACLITYNUM, getFacilityNum().toString());
    httpHeaders.add(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    httpHeaders.add(CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    httpHeaders.add(USER_ID_HEADER_KEY, retrieveUserId());
    httpHeaders.add(CONTENT_TYPE, "application/json");
    return httpHeaders;
  }

  public static String retrieveUserId() {

    return Objects.isNull(TenantContext.getAdditionalParams())
        ? ReceivingConstants.DEFAULT_USER
        : Objects.isNull(TenantContext.getAdditionalParams().get(USER_ID_HEADER_KEY))
            ? ReceivingConstants.DEFAULT_USER
            : TenantContext.getAdditionalParams().get(USER_ID_HEADER_KEY).toString();
  }

  /**
   * GDM V3 header content
   *
   * @return headers to request GDM service
   */
  public static HttpHeaders getHeaderForGDMV3SearchAPI() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(TENENT_FACLITYNUM, getFacilityNum().toString());
    httpHeaders.add(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    httpHeaders.add(USER_ID_HEADER_KEY, retrieveUserId());
    httpHeaders.add(CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    httpHeaders.add(CONTENT_TYPE, ReceivingConstants.GDM_DELIVERY_SEARCH_V3_CONTENT_TYPE);
    return httpHeaders;
  }

  public static HttpHeaders getHeaderForGDMV3API() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(TENENT_FACLITYNUM, getFacilityNum().toString());
    httpHeaders.add(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    httpHeaders.add(USER_ID_HEADER_KEY, retrieveUserId());
    httpHeaders.add(CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    httpHeaders.add(CONTENT_TYPE, ReceivingConstants.GDM_DOCUMENT_GET_BY_POLEGACY_V3_CONTENT_TYPE);
    httpHeaders.add(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE);
    return httpHeaders;
  }

  public static HttpHeaders getForwardableWithOrgUnitId(HttpHeaders httpHeaders) {
    final String orgUnitId = httpHeaders.getFirst(ORG_UNIT_ID_HEADER);
    final String subcenterId = httpHeaders.getFirst(SUBCENTER_ID_HEADER);
    if (isBlank(orgUnitId)) {
      throw new ReceivingBadDataException(MISSING_ORG_UNIT_ID_CODE, MISSING_ORG_UNIT_ID_DESC);
    }
    httpHeaders = getForwardableHttpHeaders(httpHeaders);
    httpHeaders.set(ORG_UNIT_ID_HEADER, orgUnitId);
    httpHeaders.set(SUBCENTER_ID_HEADER, subcenterId);

    return httpHeaders;
  }

  public static Map<String, String> getHeadersForOutbox() {
    return getHeaders().toSingleValueMap();
  }

  /**
   * @param deliveryNumber
   * @return default osdr summary response when complete/reopen delivery
   */
  public static OsdrSummary getOsdrDefaultSummaryResponse(Long deliveryNumber) {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    /*
     * This change is required to fix the code break for the scenarios
     * like pure offline flow where the delivery is not present in NGR, by default we can make audit pending as false
     */
    osdrSummary.setDeliveryNumber(deliveryNumber);
    osdrSummary.setAuditPending(false);
    osdrSummary.setEventType(OSDR_EVENT_TYPE_VALUE);
    osdrSummary.setUserId(DEFAULT_AUDIT_USER);
    osdrSummary.setTs(new Date());
    return osdrSummary;
  }
  /**
   * @param headers Http headers received by controller
   * @return headers that can be forwarded to downstream processes
   */
  public static Map<String, Object> getForwardablHeader(HttpHeaders headers) {
    Map<String, Object> forwardableHeaders = new HashMap<>();
    String userId = headers.getFirst(USER_ID_HEADER_KEY);
    String correlationId = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    if (!isEmpty(userId)) forwardableHeaders.put(USER_ID_HEADER_KEY, userId);
    if (!isEmpty(correlationId)) forwardableHeaders.put(CORRELATION_ID_HEADER_KEY, correlationId);

    final String subcenterId = headers.getFirst(SUBCENTER_ID_HEADER);
    if (isNotBlank(subcenterId)) forwardableHeaders.put(SUBCENTER_ID_HEADER, subcenterId);
    final String orgUnitId = headers.getFirst(ORG_UNIT_ID_HEADER);
    if (isNotBlank(orgUnitId)) forwardableHeaders.put(ORG_UNIT_ID_HEADER, orgUnitId);
    return forwardableHeaders;
  }

  /**
   * @param headers Http headers received by controller
   * @return headers that can be forwarded to downstream processes
   */
  public static Map<String, Object> getForwardablHeaderWithTenantData(HttpHeaders headers) {
    Map<String, Object> forwardableHeaders = getForwardablHeader(headers);

    forwardableHeaders.put(TENENT_FACLITYNUM, getFacilityNum().toString());
    forwardableHeaders.put(TENENT_COUNTRY_CODE, getFacilityCountryCode());

    return forwardableHeaders;
  }
  /**
   * @param clientHttpHeaders Http clientHttpHeaders received by controller
   * @return clientHttpHeaders that can be forwarded to downstream processes
   */
  public static Map<String, Object> getForwardableHeadersWithRequestOriginator(
      HttpHeaders clientHttpHeaders) {
    final Map<String, Object> forwardableHeaders = getForwardablHeader(clientHttpHeaders);
    String clientRequestOriginator = clientHttpHeaders.getFirst(REQUEST_ORIGINATOR);
    if (isNotBlank(clientRequestOriginator)) {
      forwardableHeaders.put(REQUEST_ORIGINATOR, clientRequestOriginator);
    } else {
      forwardableHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    }
    return forwardableHeaders;
  }
  /**
   * Returns only required HttpHeaders for down streams with RequestOriginator. if upstream
   * httpHeaders already has RequestOriginator then uses it else server uses receiving-api
   *
   * @param clientHttpHeaders
   * @return HttpHeaders
   */
  public static HttpHeaders getForwardableHttpHeadersWithRequestOriginator(
      HttpHeaders clientHttpHeaders) {
    HttpHeaders forwardableHttpHeaders = getForwardableHttpHeaders(clientHttpHeaders);
    addRequestOriginator(clientHttpHeaders, forwardableHttpHeaders);
    return forwardableHttpHeaders;
  }

  private static void addRequestOriginator(
      HttpHeaders httpHeadersFromUI, HttpHeaders forwardableHttpHeaders) {
    final String requestOriginatorFromUI = httpHeadersFromUI.getFirst(REQUEST_ORIGINATOR);
    forwardableHttpHeaders.add(
        REQUEST_ORIGINATOR,
        isBlank(requestOriginatorFromUI) ? APP_NAME_VALUE : requestOriginatorFromUI);
  }

  public static Gson getGsonBuilderForUTCConverter() {
    return new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
        .create();
  }

  /**
   * use @getForwardableHttpHeadersV2 Retrieves HTTP headers prioritizing TenantContext over request
   * attributes.
   *
   * <p>TenantContext might be modified by a different thread when the request is handled by a
   * thread pool.
   *
   * <p>It creates a new HttpHeaders object by merging the provided `httpHeaders` with request
   * attributes, while removing any headers not required for downstream calls.
   *
   * @param httpHeaders The HTTP headers received by the controller.
   * @return A new HttpHeaders object suitable for forwarding to downstream processes for REST
   *     calls.
   */
  @Deprecated
  public static HttpHeaders getForwardableHttpHeaders(HttpHeaders httpHeaders) {
    HttpHeaders forwardableHeaders = new HttpHeaders();

    if (!isEmpty(httpHeaders.getFirst(USER_ID_HEADER_KEY)))
      forwardableHeaders.add(USER_ID_HEADER_KEY, httpHeaders.getFirst(USER_ID_HEADER_KEY));

    String correlationId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);

    forwardableHeaders.add(
        CORRELATION_ID_HEADER_KEY,
        !isEmpty(correlationId) ? correlationId : randomUUID().toString());

    forwardableHeaders.add(
        TENENT_FACLITYNUM,
        !isEmpty(getFacilityNum())
            ? getFacilityNum().toString()
            : httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));

    forwardableHeaders.add(
        TENENT_COUNTRY_CODE,
        !isEmpty(getFacilityCountryCode())
            ? getFacilityCountryCode()
            : httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));

    forwardableHeaders.add(CONTENT_TYPE, APPLICATION_JSON);

    return forwardableHeaders;
  }

  /**
   * Retrieves HTTP headers prioritizing request attributes over TenantContext.
   *
   * <p>This is because TenantContext might be modified by a different thread when the request is
   * handled by a thread pool.
   *
   * <p>It creates a new HttpHeaders object by merging the provided `httpHeadersFromUI` with request
   * attributes, while removing any headers not required for downstream calls.
   *
   * @param httpHeadersFromUI The HTTP headers received by the controller.
   * @return A new HttpHeaders object suitable for forwarding to downstream processes for REST
   *     calls.
   */
  public static HttpHeaders getForwardableHttpHeadersV2(HttpHeaders httpHeadersFromUI) {
    HttpHeaders forwardableHeaders = new HttpHeaders();
    final String correlationId = httpHeadersFromUI.getFirst(CORRELATION_ID_HEADER_KEY);
    final String faclityNum = httpHeadersFromUI.getFirst(TENENT_FACLITYNUM);
    final String countryCode = httpHeadersFromUI.getFirst(TENENT_COUNTRY_CODE);

    forwardableHeaders.add(
        TENENT_FACLITYNUM, isBlank(faclityNum) ? getFacilityNum().toString() : faclityNum);
    forwardableHeaders.add(
        TENENT_COUNTRY_CODE, isBlank(countryCode) ? getFacilityCountryCode() : countryCode);

    forwardableHeaders.add(
        CORRELATION_ID_HEADER_KEY,
        isBlank(correlationId) ? randomUUID().toString() : correlationId);
    forwardableHeaders.add(CONTENT_TYPE, APPLICATION_JSON);
    forwardableHeaders.add(USER_ID_HEADER_KEY, httpHeadersFromUI.getFirst(USER_ID_HEADER_KEY));

    addRequestOriginator(httpHeadersFromUI, forwardableHeaders);

    return forwardableHeaders;
  }

  public static HttpHeaders getForwardableHttpHeadersForInventoryAPI(HttpHeaders httpHeaders) {
    HttpHeaders forwardableHttpHeaders =
        getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    final String flowDescriptor = httpHeaders.getFirst(ReceivingConstants.FLOW_DESCRIPTOR);
    if (StringUtils.isNotBlank(flowDescriptor)) {
      forwardableHttpHeaders.set(ReceivingConstants.FLOW_DESCRIPTOR, flowDescriptor);
    }
    final String inventoryEvent = httpHeaders.getFirst(ReceivingConstants.INVENTORY_EVENT);
    if (StringUtils.isNotBlank(inventoryEvent)) {
      forwardableHttpHeaders.set(ReceivingConstants.INVENTORY_EVENT, inventoryEvent);
    }
    final String flowName = httpHeaders.getFirst(ReceivingConstants.FLOW_NAME);
    if (StringUtils.isNotBlank(flowName)) {
      forwardableHttpHeaders.set(ReceivingConstants.FLOW_NAME, flowName);
    }
    final String eventType = httpHeaders.getFirst(ReceivingConstants.EVENT_TYPE);
    if (StringUtils.isNotBlank(eventType)) {
      forwardableHttpHeaders.set(ReceivingConstants.EVENT_TYPE, eventType);
    }
    return forwardableHttpHeaders;
  }

  public static HttpHeaders getHttpHeadersFromMessageHeaders(MessageHeaders messageHeaders) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(TENENT_FACLITYNUM, String.valueOf(getFacilityNum()));
    headers.set(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    headers.set(
        USER_ID_HEADER_KEY, String.valueOf(messageHeaders.get(ReceivingConstants.JMS_USER_ID)));
    headers.set(
        CORRELATION_ID_HEADER_KEY,
        String.valueOf(messageHeaders.get(ReceivingConstants.JMS_CORRELATION_ID)));

    final String requestOriginator =
        (String) messageHeaders.get(ReceivingConstants.REQUEST_ORIGINATOR);
    if (isNotBlank(requestOriginator)) {
      headers.set(ReceivingConstants.REQUEST_ORIGINATOR, requestOriginator);
    }

    final String flowDescriptor = (String) messageHeaders.get(ReceivingConstants.FLOW_DESCRIPTOR);
    if (isNotBlank(flowDescriptor)) {
      headers.set(ReceivingConstants.FLOW_DESCRIPTOR, flowDescriptor);
    }

    return headers;
  }

  /**
   * @param httpHeaders Http headers received by controller
   * @return httpheaders that can be forwarded to downstream processes for rest calls
   */
  public static Map<String, Object> getForwardableHeaderWithEventType(
      HttpHeaders httpHeaders, String eventType) {
    Map<String, Object> forwardableHeaders = new HashMap<>();
    String correlationId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    if (!isEmpty(correlationId)) forwardableHeaders.put(CORRELATION_ID_HEADER_KEY, correlationId);

    forwardableHeaders.put(TENENT_FACLITYNUM, getFacilityNum().toString());
    forwardableHeaders.put(TENENT_COUNTRY_CODE, getFacilityCountryCode());
    forwardableHeaders.put(ReceivingConstants.EVENT_TYPE, eventType);
    forwardableHeaders.put(ReceivingConstants.VERSION, ReceivingConstants.DEFAULT_VERSION);
    forwardableHeaders.put(
        ReceivingConstants.MSG_TIME_STAMP, ReceivingUtils.dateConversionToUTC(new Date()));

    return forwardableHeaders;
  }

  /**
   * This method is responsible for creating service mesh http headers from existing http headers
   * with @receivingConsumerId, @serviceName and @serviceEnv
   *
   * @param httpHeaders
   * @param receivingConsumerId
   * @param serviceName
   * @param serviceEnv
   * @return
   */
  public static HttpHeaders getServiceMeshHeaders(
      HttpHeaders httpHeaders, String receivingConsumerId, String serviceName, String serviceEnv) {
    httpHeaders.set(ReceivingConstants.WM_CONSUMER_ID, receivingConsumerId);
    httpHeaders.set(ReceivingConstants.WM_SVC_NAME, serviceName);
    httpHeaders.set(ReceivingConstants.WM_SVC_ENV, serviceEnv);
    return httpHeaders;
  }

  /**
   * This method is responsible for creating service mesh http headers from existing http headers
   * with @receivingConsumerId, @serviceName, @serviceEnv and @serviceVersion
   *
   * @param httpHeaders
   * @param receivingConsumerId
   * @param serviceName
   * @param serviceEnv
   * @return
   */
  public static HttpHeaders getServiceMeshHeaders(
      HttpHeaders httpHeaders,
      String receivingConsumerId,
      String serviceName,
      String serviceEnv,
      String serviceVersion) {
    httpHeaders = getServiceMeshHeaders(httpHeaders, receivingConsumerId, serviceName, serviceEnv);
    httpHeaders.set(ReceivingConstants.WM_SVC_VERSION, serviceVersion);
    return httpHeaders;
  }

  public static HttpHeaders convertMapToHeader(Map<String, Object> forwardableHeaders) {
    HttpHeaders requestHeader = new HttpHeaders();
    requestHeader.set(TENENT_FACLITYNUM, forwardableHeaders.get(TENENT_FACLITYNUM).toString());
    requestHeader.set(TENENT_COUNTRY_CODE, forwardableHeaders.get(TENENT_COUNTRY_CODE).toString());
    requestHeader.set(
        CORRELATION_ID_HEADER_KEY, forwardableHeaders.get(CORRELATION_ID_HEADER_KEY).toString());
    requestHeader.set(USER_ID_HEADER_KEY, forwardableHeaders.get(USER_ID_HEADER_KEY).toString());
    return requestHeader;
  }

  public static void addOrReplaceHeader(
      Map<String, Object> headers, boolean isUpdateHeader, String key, String value) {
    if (headers == null || !isUpdateHeader) {
      return;
    }
    log.info("add/replace header={} with value={}", key, value);
    headers.put(key, value);
  }
  /**
   * This method will convert quantity into eache's
   *
   * @param quantity
   * @param uom
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public static Integer conversionToEaches(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Integer eachQty = 0;
    switch (uom) {
      case ReceivingConstants.Uom.VNPK:
      case ReceivingConstants.Uom.CA:
        eachQty = quantity * vnpkQty;
        break;
      case ReceivingConstants.Uom.WHPK:
        eachQty = quantity * whpkQty;
        break;
      default:
        eachQty = quantity;
    }
    return eachQty;
  }

  /**
   * This method will convert quantity into vendor pack
   *
   * @param quantity
   * @param uom
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public static Integer conversionToVendorPack(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Integer vendorPackQty = 0;
    switch (uom) {
      case ReceivingConstants.Uom.EACHES:
      case EACHES: // Inventory sends as string EACHES in upper
        vendorPackQty = quantity / vnpkQty;
        break;
      case ReceivingConstants.Uom.WHPK:
        vendorPackQty = (quantity * whpkQty) / vnpkQty;
        break;
      default:
        vendorPackQty = quantity;
    }
    return vendorPackQty;
  }

  /**
   * This method will convert quantity into Warehouse pack
   *
   * @param quantity
   * @param uom
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public static Integer conversionToWareHousePack(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Integer wareHousePackQty = 0;
    switch (uom) {
      case ReceivingConstants.Uom.EACHES:
        wareHousePackQty = quantity / whpkQty;
        break;
      case ReceivingConstants.Uom.VNPK:
      case ReceivingConstants.Uom.CA:
        wareHousePackQty = (vnpkQty / whpkQty) * quantity;
        break;
      default:
        wareHousePackQty = quantity;
    }
    return wareHousePackQty;
  }

  /**
   * This method will convert quantity into vendor pack and Round up
   *
   * @param quantity
   * @param uom
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public static Integer conversionToVendorPackRoundUp(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    double vendorPackQty = 0;
    switch (uom) {
      case ReceivingConstants.Uom.EACHES:
        vendorPackQty = (double) quantity / vnpkQty;
        break;
      case ReceivingConstants.Uom.WHPK:
        vendorPackQty = (double) (quantity * whpkQty) / vnpkQty;
        break;
      default:
        vendorPackQty = quantity;
    }
    return (int) Math.ceil(vendorPackQty);
  }

  /**
   * Convert to ISO 8601 date format of YYYY-MM-DDThh:mm:ss.sTZD with the "Z" notation for UTC
   *
   * @param date
   * @return date in UTC
   */
  public static String dateConversionToUTC(Date date) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    return dateFormat.format(date);
  }

  /**
   * Convert input date to specific pattern
   *
   * @param inputDateString
   * @param pattern
   * @param dcTimeZone
   * @return
   * @throws ParseException
   */
  public static Date parseDateToProvidedTimeZone(
      String inputDateString, String pattern, String dcTimeZone) throws ParseException {
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    SimpleDateFormat inputSimpleDateFormat = new SimpleDateFormat(pattern);
    inputSimpleDateFormat.setTimeZone(TimeZone.getTimeZone(dcZoneId));
    return inputSimpleDateFormat.parse(inputDateString);
  }

  /**
   * Convert date to given timezone and pattern
   *
   * @param date
   * @param pattern
   * @param timeZone
   * @return
   */
  public static String dateInProvidedPatternAndTimeZone(
      Date date, String pattern, String timeZone) {
    DateFormat dateFormat = new SimpleDateFormat(pattern);
    dateFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
    return dateFormat.format(date);
  }

  public static String dateInEST() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("EST"));
    return dateFormat.format(new Date());
  }

  public static void verifyUser(Instruction instruction, String userId, RequestType type)
      throws ReceivingException {

    if (!isEmpty(instruction.getProblemTagId())) return;

    String errorHeader = null;

    switch (type) {
      case UPDATE:
        errorHeader = ReceivingException.MULTI_USER_ERROR_HEADER_UPDATE;
        break;
      case COMPLETE:
        errorHeader = ReceivingException.MULTI_USER_ERROR_HEADER_COMPLETE;
        break;
      case CANCEL:
        errorHeader = ReceivingException.MULTI_USER_ERROR_HEADER_CANCEL;
        break;
      case RECEIVE:
        errorHeader = ReceivingException.MULTI_USER_ERROR_HEADER_RECEIVE;
        break;
    }
    String currentUserId = getInstructionOwner(instruction);
    if (isEmpty(userId) || isEmpty(currentUserId)) {
      log.error(
          "Unable to verify the user. Either instruction or userId or currentUserId is null.");
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.MULTI_USER_UNABLE_TO_VERIFY)
              .errorCode(ReceivingException.MULTI_USER_ERROR_CODE)
              .errorHeader(errorHeader)
              .errorKey(ExceptionCodes.MULTI_USER_UNABLE_TO_VERIFY)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }

    if (!userId.equalsIgnoreCase(currentUserId)) {
      String errorMsg = String.format(ReceivingException.MULTI_USER_ERROR_MESSAGE, currentUserId);
      log.error(errorMsg);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(errorMsg)
              .errorCode(ReceivingException.MULTI_USER_ERROR_CODE)
              .errorHeader(errorHeader)
              .errorKey(ExceptionCodes.MULTI_USER_ERROR_MESSAGE)
              .values(new Object[] {currentUserId})
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public static String getInstructionOwner(Instruction instruction) {
    return !isEmpty(instruction.getLastChangeUserId())
        ? instruction.getLastChangeUserId()
        : instruction.getCreateUserId();
  }

  public static long getTimeDifferenceInMillis(long time) {
    return System.currentTimeMillis() - time;
  }

  /**
   * Computes time difference in MilliSec
   *
   * @param start time
   * @param end time
   * @return long time difference between start and end in MillisSec
   */
  public static long getTimeDifferenceInMillis(long start, long end) {
    return end - start;
  }

  /**
   * * Method to validate if proceed for pre-label generation or not
   *
   * @param eventType
   * @return Boolean
   */
  public static boolean isValidPreLabelEvent(String eventType) {
    return Arrays.asList(
            ReceivingConstants.EVENT_DOOR_ASSIGNED,
            ReceivingConstants.EVENT_PO_ADDED,
            ReceivingConstants.EVENT_PO_UPDATED,
            ReceivingConstants.EVENT_PO_LINE_ADDED,
            ReceivingConstants.EVENT_PO_LINE_UPDATED,
            ReceivingConstants.PRE_LABEL_GEN_FALLBACK)
        .contains(eventType);
  }

  /**
   * Validates if the event is a valid shipment event
   *
   * @param event
   * @return boolean
   */
  public static boolean isValidShipmentEvent(DeliveryMessageEvent event) {
    if (Objects.isNull(event) || isEmpty(event.getType())) return false;
    return Arrays.asList(ReceivingConstants.SHIPMENT_ADDED, ReceivingConstants.SHIPMENT_UPDATED)
        .contains(event.getType());
  }

  /**
   * Method to find valid status for label generation and diverts
   *
   * @param status
   * @return
   */
  public static boolean isValidStatus(DeliveryStatus status) {
    // TODO: Consider Reopen later for that flow
    return Arrays.asList(DeliveryStatus.ARV, DeliveryStatus.OPN, DeliveryStatus.WRK)
        .contains(status);
  }

  /**
   * * Method will return if event is a type of {@link ReceivingConstants#EVENT_PO_UPDATED} , {@link
   * ReceivingConstants#EVENT_PO_ADDED} , {@link ReceivingConstants#EVENT_PO_LINE_UPDATED}, {@link
   * ReceivingConstants#EVENT_PO_LINE_ADDED}
   *
   * @param eventType
   * @return
   */
  public static boolean isPOChangeEvent(String eventType) {
    return Arrays.asList(
            ReceivingConstants.EVENT_PO_ADDED,
            ReceivingConstants.EVENT_PO_UPDATED,
            ReceivingConstants.EVENT_PO_LINE_ADDED,
            ReceivingConstants.EVENT_PO_LINE_UPDATED)
        .contains(eventType);
  }

  public static boolean isPOLineChangeEvent(String eventType) {
    return Arrays.asList(
            ReceivingConstants.EVENT_PO_LINE_ADDED, ReceivingConstants.EVENT_PO_LINE_UPDATED)
        .contains(eventType);
  }

  public static List<Container> getAllParentContainers(List<Container> containers) {
    if (CollectionUtils.isEmpty(containers)) return containers;

    return containers
        .stream()
        .filter(container -> isEmpty(container.getParentTrackingId()))
        .collect(Collectors.toList());
  }

  public static String getContainerUser(Container container) {
    return !isEmpty(container.getLastChangedUser())
        ? container.getLastChangedUser()
        : container.getCreateUser();
  }

  /**
   * Replaces path params
   *
   * @param url
   * @param pathParams
   * @return URI after replacing path params
   */
  public static URI replacePathParams(String url, Map<String, String> pathParams) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
    URI urlAfterPathParamsReplacement = null;
    if (!CollectionUtils.isEmpty(pathParams)) {
      urlAfterPathParamsReplacement = builder.buildAndExpand(pathParams).toUri();
    } else {
      urlAfterPathParamsReplacement = builder.build().toUri();
    }
    return urlAfterPathParamsReplacement;
  }

  /**
   * This method will check if its a single PO
   *
   * @param deliveryDocuments
   * @return true/false
   */
  public static boolean isSinglePO(List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments.size() == 1;
  }

  /**
   * This method will check if its a single POLine
   *
   * @param deliveryDocument
   * @return true/false
   */
  public static boolean isSinglePoLine(DeliveryDocument deliveryDocument) {
    return deliveryDocument.getDeliveryDocumentLines().size() == 1;
  }

  /**
   * This method is used to create URL using query parameters and path params
   *
   * @param url
   * @param pathParams
   * @return URI after replacing path params
   */
  public static URI replacePathParamsAndQueryParams(
      String url, Map<String, String> pathParams, Map<String, String> queryParameters) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

    if (!CollectionUtils.isEmpty(queryParameters)) {
      for (Map.Entry<String, String> queryParam : queryParameters.entrySet()) {
        builder.queryParam(queryParam.getKey(), queryParam.getValue());
      }
    }
    URI urlAfterPathParamsReplacement = null;
    if (!CollectionUtils.isEmpty(pathParams)) {
      urlAfterPathParamsReplacement = builder.buildAndExpand(pathParams).toUri();
    } else {
      urlAfterPathParamsReplacement = builder.build().toUri();
    }
    return urlAfterPathParamsReplacement;
  }

  /**
   * This method will convert Eaches to Vendor Pack / Ware house Pack. If Eaches are not multiple of
   * Vendor Pack / Ware house Pack then rounding logic will be applied.
   *
   * <p>The rounding algorithm is explained below If converted value is less than -1 then
   * Math.ceil() of converted value will be considered. If converted value lies between -1 and 0
   * then -1 will be considered. If converted value is 0 then 0 will be considered. If converted
   * value lies between 0 and 1 then 1 will be considered. If converted value is greated than 1 then
   * Math.floor() of converted value will be considered.
   *
   * @param quantity
   * @param uom
   * @param vnpkQuantity
   * @param whpkQuantity
   * @return
   */
  public static Integer calculateUOMSpecificQuantity(
      Integer quantity, String uom, Integer vnpkQuantity, Integer whpkQuantity) {
    double qty = quantity.doubleValue();
    double vnpkQty = vnpkQuantity.doubleValue();
    double whpkQty = whpkQuantity.doubleValue();

    switch (uom) {
      case ReceivingConstants.Uom.VNPK:
        qty = qty / vnpkQty;
        break;
      case ReceivingConstants.Uom.WHPK:
        qty = qty / whpkQty;
        break;
      default:
        qty = qty;
        break;
    }
    if (qty < -1) {
      return Double.valueOf(Math.ceil(qty)).intValue();
    }
    if (qty > -1 && qty < 0) {
      return -1;
    }
    if (qty == 0) {
      return 0;
    }
    if (qty > 0 && qty < 1) {
      return 1;
    }
    return Double.valueOf(Math.floor(qty)).intValue();
  }

  public static Integer calculateQuantityFromSourceUOMToDestinationUOM(
      Integer quantity,
      String sourceQuantityUOM,
      String destinationQuantityUOM,
      Integer vnpkQuantity,
      Integer whpkQuantity) {
    double qty = quantity.doubleValue();
    double vnpkQty = vnpkQuantity.doubleValue();
    double whpkQty = whpkQuantity.doubleValue();

    switch (sourceQuantityUOM) {
      case ReceivingConstants.Uom.VNPK:
        if (ReceivingConstants.Uom.WHPK.equals(destinationQuantityUOM)) {
          qty = (qty * vnpkQty) / whpkQty;
        } else if (ReceivingConstants.Uom.EACHES.equals(destinationQuantityUOM)) {
          qty = qty * vnpkQty;
        }
        break;
      case ReceivingConstants.Uom.WHPK:
        if (ReceivingConstants.Uom.VNPK.equals(destinationQuantityUOM)) {
          qty = (qty * whpkQty) / vnpkQty;
        } else if (ReceivingConstants.Uom.EACHES.equals(destinationQuantityUOM)) {
          qty = qty * whpkQty;
        }
        break;
      case ReceivingConstants.Uom.EACHES:
        if (ReceivingConstants.Uom.WHPK.equals(destinationQuantityUOM)) {
          qty = qty / whpkQty;
        } else if (ReceivingConstants.Uom.VNPK.equals(destinationQuantityUOM)) {
          qty = qty / vnpkQty;
        }
        break;
      default:
        break;
    }
    if (qty < -1) {
      return (int) Math.ceil(qty);
    } else if (qty > -1 && qty < 0) {
      return -1;
    } else {
      return (int) Math.floor(qty);
    }
  }

  /**
   * Parsing ACL Notification date format from Integration team (Fri Aug 30 17:45:19 UTC 2019)
   *
   * @param dateTimeStr
   * @return
   */
  public static final Date parseUtcDateTime(String dateTimeStr) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
    dateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
    try {
      return dateFormat.parse(dateTimeStr);
    } catch (ParseException e) {
      return null;
    }
  }

  /**
   * For removing facilities starting with "32" from prod The system get property is put in place
   * for the test cases similar to endgame test cases
   *
   * @return
   */
  public static void validateApiAccessibility() {
    String envName =
        System.getenv(ONEOPS_ENVIRONMENT) == null
            ? System.getProperty(ONEOPS_ENVIRONMENT)
            : System.getenv(ONEOPS_ENVIRONMENT);
    if ("prod".equalsIgnoreCase(envName)) {
      throw new ReceivingInternalException(
          ExceptionCodes.RESOURCE_NOT_ACCESSABLE,
          String.format(ReceivingConstants.API_NOT_ACCESSABLE, envName));
    }
  }

  /**
   * This is method is to validate whether a given string is a correct unit of measurement of
   * quantity.
   *
   * @param uom
   * @return
   */
  public static boolean isValidUnitOfMeasurementForQuantity(String uom) {
    return ReceivingConstants.Uom.EACHES.equals(uom)
        || ReceivingConstants.Uom.VNPK.equals(uom)
        || ReceivingConstants.Uom.WHPK.equals(uom);
  }

  /**
   * A valid location should contain only letters, numbers, hyphens and underscores
   *
   * @param locationId
   * @return
   */
  public static boolean isValidLocation(String locationId) {
    final String regex = "^[\\w\\_\\-]+$";
    final Pattern pattern = Pattern.compile(regex);
    if (locationId == null) {
      return false;
    }
    Matcher matcher = pattern.matcher(locationId);
    return matcher.matches();
  }

  public static <T extends BaseMTEntity> List<Integer> getFacilityNumListPresent(
      List<T> entityList) {
    return entityList
        .parallelStream()
        .map(BaseMTEntity::getFacilityNum)
        .distinct()
        .collect(Collectors.toList());
  }

  public static String readClassPathResourceAsString(String fileName) throws Exception {
    File resource = new ClassPathResource(fileName).getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  public static String sanitize(String rawText) {
    String sanitizedString = NULL;
    if (!StringUtils.equals(NULL, rawText)) {
      sanitizedString = StringUtils.replace(HtmlUtils.htmlEscape(rawText, "UTF-8"), "&quot;", "\"");
      sanitizedString = StringUtils.replace(sanitizedString, "&#39;", "'");
      sanitizedString = StringUtils.replace(sanitizedString, "&amp;", "&");
    }
    return sanitizedString;
  }

  public static void setContextFromMsgHeaders(MessageHeaders messageHeaders, String className) {
    String facilityNum =
        Objects.isNull(messageHeaders.get(TENENT_FACLITYNUM))
            ? null
            : messageHeaders.get(TENENT_FACLITYNUM).toString();
    String facilityCountryCode =
        Objects.isNull(messageHeaders.get(TENENT_COUNTRY_CODE))
            ? null
            : messageHeaders.get(TENENT_COUNTRY_CODE).toString();
    String correlationId = null;
    if (Objects.isNull(messageHeaders.get(ReceivingConstants.JMS_CORRELATION_ID))) {
      correlationId = UUID.randomUUID().toString();
      log.info("CorrelationId not found, setting correlation id : {}", correlationId);
    } else {
      correlationId = messageHeaders.get(ReceivingConstants.JMS_CORRELATION_ID).toString();
    }

    setTenantContext(facilityNum, facilityCountryCode, correlationId, className);
  }

  public static void setTenantContext(
      String facilityNum, String facilityCountryCode, String correlationId, String className) {
    TenantContext.setCorrelationId(correlationId);
    if (!isEmpty(facilityNum) && !isEmpty(facilityCountryCode)) {
      TenantContext.setFacilityNum(Integer.parseInt(facilityNum));
      TenantContext.setFacilityCountryCode(facilityCountryCode);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_TENANT,
          String.format(ReceivingConstants.INVALID_TENANT_ERROR_MSG, className));
    }
  }

  public static Date parseDate(String strRotateDate) {
    if (isEmpty(strRotateDate)) {
      return null;
    }
    DateFormat dateFormat = new SimpleDateFormat(ReceivingConstants.UTC_DATE_FORMAT);
    dateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
    Date rotateDate;
    try {
      rotateDate = dateFormat.parse(strRotateDate);
    } catch (ParseException e) {
      log.error(e.getMessage(), e);
      return null;
    }
    return rotateDate;
  }

  public static <T> Collection<List<T>> batchifyCollection(
      Collection<T> partitionColl, int batchSize) {
    AtomicInteger counter = new AtomicInteger();
    return partitionColl
        .parallelStream()
        .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / batchSize))
        .values();
  }
  /**
   * * This method will compress the String message using gzip
   *
   * @param data data to be compressed
   * @param charset charset
   * @return byte[] byte array
   * @throws IOException throws IOException
   */
  public static byte[] compressGZIP(String data, Charset charset) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream out = new GZIPOutputStream(byteArrayOutputStream)) {
      IOUtils.copy(new StringReader(data), out, charset);
    }
    return byteArrayOutputStream.toByteArray();
  }

  /**
   * Compress string data and encode to base 64
   *
   * @param data string data
   * @return base 64 encoded compressed data
   * @throws IOException throws IOException
   */
  public static String compressDataInBase64(String data) throws IOException {
    byte[] compressedACLLabelData = compressGZIP(data, StandardCharsets.UTF_8);
    return new String(Base64.getEncoder().encode(compressedACLLabelData));
  }

  /**
   * Checks if the byte array is gzip compressed
   *
   * @param message compressed message
   * @return true if compressed else false
   */
  private static boolean isCompressed(final byte[] message) {
    return (message[0] == (byte) (GZIPInputStream.GZIP_MAGIC))
        && (message[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
  }

  /**
   * Decompresses and decodes gzipped and base 64 encoded message
   *
   * @param message gzipped and base64 encoded message
   * @return decompressed message
   * @throws IOException returns exception
   */
  public static String decodeBase64EncodedMessageAndDecompress(final String message)
      throws IOException {
    String output = null;
    byte[] base64DecodedMessage =
        Base64.getDecoder().decode(message.getBytes(StandardCharsets.UTF_8));
    if ((base64DecodedMessage == null) || (base64DecodedMessage.length == 0)) {
      throw new IOException("Decompression failed: No Data");
    }
    if (!isCompressed(base64DecodedMessage)) {
      return new String(base64DecodedMessage);
    }
    try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(base64DecodedMessage));
        ByteArrayOutputStream result = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = gis.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      output = result.toString(StandardCharsets.UTF_8.name());
    }
    return output;
  }

  public static boolean isNumeric(String deliveryNumber) {
    String regex = "\\d+";
    return deliveryNumber.matches(regex);
  }

  public static Integer calculateConcealedTotal(
      Integer rcvdQty, Integer overage, Integer shortage) {
    applyDefaultValue(rcvdQty);
    applyDefaultValue(overage);
    applyDefaultValue(shortage);

    return rcvdQty.intValue() + overage.intValue() - shortage.intValue();
  }

  public static Number applyDefaultValue(Number number) {
    return Objects.isNull(number) ? 0 : number;
  }

  public static Integer applyDamage(int rcvQty, OsdrData damage) {

    if (Objects.isNull(damage)) {
      return rcvQty;
    }

    Integer damageQty = applyDefaultValue(damage.getQuantity()).intValue();

    return rcvQty - damageQty;
  }

  // returns DC specific TimeZone date time for given input else returns UTC timezone
  public static String getDcDateTime(String dcTimeZone) {
    dcTimeZone = isBlank(dcTimeZone) ? UTC_TIME_ZONE : dcTimeZone;
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    final Instant nowInUtc = Instant.now();
    final ZonedDateTime dcDateTime = ZonedDateTime.ofInstant(nowInUtc, dcZoneId);

    // convert dcDateTime to Print Label format
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PRINT_LABEL_DATE_FORMAT);
    return dcDateTime.format(formatter);
  }

  public static String convertCommaSeparatedString(List<String> objects) {
    return objects.stream().collect(Collectors.joining(","));
  }

  public static boolean isPOLineCancelled(String purchaseRefStatus, String purchaseRefLineStatus) {
    return (purchaseRefStatus != null && purchaseRefStatus.equalsIgnoreCase(POStatus.CNCL.name()))
        || (purchaseRefLineStatus != null
            && purchaseRefLineStatus.equalsIgnoreCase(POLineStatus.CANCELLED.name()));
  }

  public static List<InstructionStatus> getPendingDockTagStatus() {
    List<InstructionStatus> instructionStatuses = new ArrayList<>();
    instructionStatuses.add(InstructionStatus.CREATED);
    instructionStatuses.add(InstructionStatus.UPDATED);
    return instructionStatuses;
  }

  // TODO Need to revisit if we can use generic to handle all types
  public static List<String> checkDefaultValue(List<String> list) {
    return CollectionUtils.isEmpty(list) ? new ArrayList() : list;
  }

  public static boolean verifyVendorDateComplaince(Date verifiedDateFieldOn) {
    Instant lastYearsDate =
        (Instant.now()).minus(ReceivingConstants.vendorComplianceDuration, ChronoUnit.DAYS);
    return !Objects.isNull(verifiedDateFieldOn)
        && (verifiedDateFieldOn.toInstant().compareTo(lastYearsDate)) >= 0;
  }

  public static void validateTrackingId(String trackingId) throws ReceivingBadDataException {
    validateTrackingId(trackingId, ALPHA_NUMERIC_REGEX_PATTERN);
  }

  /**
   * This method validates if the given trackingId is Atlas LPN container or not. Container shouls
   * start with an alpha character to determine the LPN is valid or not. [a-zA-Z]
   *
   * @param trackingId
   * @return
   */
  public static boolean isValidLpn(String trackingId) {
    boolean isValidAtlasContainer = false;
    try {
      if (isNotBlank(trackingId)
          && (trackingId.length() == LPN_LENGTH_18 || trackingId.length() == LPN_LENGTH_25)) {
        Pattern pattern = Pattern.compile(ATLAS_LPN_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(trackingId);
        isValidAtlasContainer = matcher.matches();
      }
    } catch (Exception e) {
      log.error("lpn={}, error={}, stackTrace={}", trackingId, e.getMessage(), getStackTrace(e));
    }
    return isValidAtlasContainer;
  }

  public static void validateTrackingId(final String trackingId, final String regexPattern)
      throws ReceivingBadDataException {
    try {
      if (isNotBlank(trackingId) && isNotBlank(regexPattern)) {
        Pattern pattern = Pattern.compile(regexPattern, CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(trackingId);
        if (matcher.find()) {
          return; // valid do nothing
        }
      }
    } catch (Exception e) {
      log.error("lpn={}, error={}, stackTrace={}", trackingId, e.getMessage(), getStackTrace(e));
    }
    log.error("lpn={} is invalid for pattern={}", trackingId, regexPattern);
    throw new ReceivingBadDataException(INVALID_DATA, INVALID_LPN_NUMBER);
  }

  public static void validatePoNumber(String poNumber) throws ReceivingBadDataException {
    if (!isNumeric(poNumber)) {
      log.error("poNumber={} should be numeric", poNumber);
      throw new ReceivingBadDataException(INVALID_DATA, INVALID_PO_NUMBER);
    }
  }

  public static String stringfyJson(Object object) {

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    String jsonValue = null;

    try {
      jsonValue = objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      //			LOGGER.error("Unable to print stringify value ", e);
    }
    return jsonValue;
  }

  public static Map<String, Object> jsonStringToMap(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      log.error("Unable to parse JSON String ", e);
      return null;
    }
  }

  public static Map<String, String> convertJsonToMap(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(jsonString, new TypeReference<Map<String, String>>() {});
    } catch (IOException e) {
      log.error("Unable to parse JSON String ", e);
      return null;
    }
  }

  public static long convertMiliSecondsInhours(long timeInMiliSeconds) {
    if (timeInMiliSeconds < 0) return 0;
    return TimeUnit.HOURS.convert(timeInMiliSeconds, TimeUnit.MILLISECONDS);
  }

  public static Date parseIsoTimeFormat(String isoTimeInString) {
    if (isEmpty(isoTimeInString)) {
      return null;
    }
    Date date;
    try {
      date = Date.from(Instant.parse(isoTimeInString));
    } catch (DateTimeParseException re) {
      log.error(re.getMessage(), re);
      return null;
    }
    return date;
  }

  /**
   * Parse the time String in ISO Format to an Instant
   *
   * @param time
   * @return Instant
   */
  public static Instant parseTime(String time) {
    try {
      return Instant.parse(time);
    } catch (Exception ex) {
      log.info("Exception occurred when parsing time {} with message {}", time, ex.getMessage());
      return null;
    }
  }

  /**
   * To convert the time String to match the format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" exactly
   *
   * @param time
   * @return String
   */
  public static String convertToTimestampWithMillisecond(String time) {
    Instant instant = ReceivingUtils.parseTime(time);
    if (instant == null) {
      instant = Instant.now();
    }
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneId.of("UTC"));
    return formatter.format(instant);
  }

  public static List<ReceiptSummaryResponse> getReceiptSummaryResponseForRDC(
      OsdrSummary osdrSummaryResponse) {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    if (osdrSummaryResponse != null
        && org.apache.commons.collections4.CollectionUtils.isNotEmpty(
            osdrSummaryResponse.getSummary())) {
      for (OsdrPo osdrPo : osdrSummaryResponse.getSummary()) {
        for (OsdrPoLine poLine : osdrPo.getLines()) {
          ReceiptSummaryResponse receipt =
              new ReceiptSummaryResponse(
                  osdrPo.getPurchaseReferenceNumber(),
                  poLine.getLineNumber().intValue(),
                  poLine.getRcvdQty().longValue());
          receipt.setQtyUOM(poLine.getRcvdQtyUom());
          receiptSummaryResponseList.add(receipt);
        }
      }
    }
    return receiptSummaryResponseList;
  }

  public static Double parseFloatToDouble(Float value) {
    return value == null ? null : Double.valueOf(value);
  }

  /**
   * This method populates the required inventory headers from the the kafka headers
   *
   * @param kafkaHeaders
   * @return httpHeaders
   */
  public static HttpHeaders populateInventoryHeadersFromKafkaHeaders(
      Map<String, byte[]> kafkaHeaders) {
    Integer facilityNumber = Integer.parseInt(new String(kafkaHeaders.get(TENENT_FACLITYNUM)));
    String facilityCountryCode = new String(kafkaHeaders.get(TENENT_COUNTRY_CODE));
    String correlationId = new String(kafkaHeaders.get(ReceivingConstants.JMS_CORRELATION_ID));
    String userId = new String(kafkaHeaders.get(ReceivingConstants.JMS_USER_ID));
    String eventType = new String(kafkaHeaders.get(ReceivingConstants.EVENT_TYPE));
    String requestOriginator =
        kafkaHeaders.containsKey(ReceivingConstants.REQUEST_ORIGINATOR)
            ? new String(kafkaHeaders.get(ReceivingConstants.REQUEST_ORIGINATOR))
            : ReceivingConstants.INVENTORY_EVENT;

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
    httpHeaders.add(TENENT_FACLITYNUM, String.valueOf(facilityNumber));
    httpHeaders.add(TENENT_COUNTRY_CODE, facilityCountryCode);
    httpHeaders.add(ReceivingConstants.REQUEST_ORIGINATOR, requestOriginator);
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, eventType);
    if (kafkaHeaders.containsKey(ReceivingConstants.INVENTORY_EVENT))
      httpHeaders.add(
          ReceivingConstants.INVENTORY_EVENT,
          new String(kafkaHeaders.get(ReceivingConstants.INVENTORY_EVENT)));
    if (kafkaHeaders.containsKey(ReceivingConstants.FLOW_NAME))
      httpHeaders.add(
          ReceivingConstants.FLOW_NAME, new String(kafkaHeaders.get(ReceivingConstants.FLOW_NAME)));
    if (kafkaHeaders.containsKey(ReceivingConstants.FLOW_DESCRIPTOR))
      httpHeaders.add(
          ReceivingConstants.FLOW_DESCRIPTOR,
          new String(kafkaHeaders.get(ReceivingConstants.FLOW_DESCRIPTOR)));
    return httpHeaders;
  }

  /**
   * This method populates the required instruction download headers from the the kafka headers
   *
   * @param kafkaHeaders
   * @return
   */
  public static HttpHeaders populateInstructionDownloadHeadersFromKafkaHeaders(
      Map<String, byte[]> kafkaHeaders) {
    String eventType = new String(kafkaHeaders.get(ReceivingConstants.EVENT_TYPE));
    String correlationId = new String(kafkaHeaders.get(ReceivingConstants.JMS_CORRELATION_ID));
    String totalMessageCount = new String(kafkaHeaders.get(ReceivingConstants.TOTAL_MESSAGE_COUNT));
    Integer facilityNumber =
        Integer.parseInt(new String(kafkaHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)));
    String facilityCountryCode =
        new String(kafkaHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, eventType);
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
    httpHeaders.add(ReceivingConstants.TOTAL_MESSAGE_COUNT, totalMessageCount);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, String.valueOf(facilityNumber));
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
    return httpHeaders;
  }

  public static boolean isKotlinEnabled(
      final HttpHeaders httpHeaders, final TenantSpecificConfigReader tenantSpecificConfigReader) {
    if (httpHeaders != null && httpHeaders.getFirst(IS_KOTLIN_CLIENT) != null) {
      return TRUE_STRING.equalsIgnoreCase(httpHeaders.getFirst(IS_KOTLIN_CLIENT));
    } else {
      return tenantSpecificConfigReader != null
          && tenantSpecificConfigReader.getConfiguredFeatureFlag(
              getFacilityNum().toString(), KOTLIN_ENABLED, false);
    }
  }

  public static boolean checkIfDeliveryWorkingOrOpen(
      String deliveryStatus, String deliveryLegacyStatus) {
    return (DeliveryStatus.WRK.name().equals(deliveryStatus))
        || ((DeliveryStatus.OPN.name().equals(deliveryStatus))
            && !DeliveryStatus.PNDPT.name().equals(deliveryLegacyStatus));
  }

  public static boolean needToCallReopen(String deliveryStatus, String deliveryLegacyStatus) {

    /*
     * In GDM for OPN, following are the deliveryStatus- deliveryLegacyStatus available
     * OPN-OPN (when receiving opens the door)
     * OPN-PNDPT (if there are pending problem tags)
     * OPN-PNDDT (if there are pending dock tags)
     * OPN-REO (after delivery reopen)
     * Reopen needs to be called only when deliveryLegacyStatus is PNDPT.
     * For other OPN, it is not required.
     */

    // Reopen needs to be called only when deliveryLegacyStatus is PNDPT, for other OPN, it is not
    // required.
    if (DeliveryStatus.OPN.name().equals(deliveryStatus)) {
      if (DeliveryStatus.PNDPT.name().equals(deliveryLegacyStatus)) return true;
      return false;
    }
    // Delivery can be reopened if it has been in Pending Finalized state
    if (DeliveryStatus.PNDFNL.name().equals(deliveryStatus)) return true;

    // Deliveries which are in SCH,ARV,FNL,CNL can not be reopened.
    return false;
  }

  public static ZonedDateTime getDCDateTime(String dcTimeZone) {
    dcTimeZone = isBlank(dcTimeZone) ? UTC_TIME_ZONE : dcTimeZone;
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    final Instant nowInUtc = Instant.now();
    return ZonedDateTime.ofInstant(nowInUtc, dcZoneId);
  }

  public static ZonedDateTime convertUTCToZoneDateTime(Date date, String dcTimeZone) {
    dcTimeZone = isBlank(dcTimeZone) ? UTC_TIME_ZONE : dcTimeZone;
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    return date.toInstant().atZone(dcZoneId);
  }

  public static String getLabelFormatDateAndTime(ZonedDateTime zonedDateTime, String pattern) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    if (zonedDateTime == null) return StringUtils.EMPTY;
    return zonedDateTime.format(formatter);
  }

  public static String getFormattedDateString(Date date, String pattern) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
    if (Objects.isNull(date)) return StringUtils.EMPTY;
    return simpleDateFormat.format(date);
  }

  public static void populateTenantContext(Map<String, Object> headers) {
    TenantContext.setFacilityCountryCode(headers.get(TENENT_COUNTRY_CODE).toString());
    TenantContext.setFacilityNum(Integer.valueOf(headers.get(TENENT_FACLITYNUM).toString()));
    TenantContext.setAdditionalParams(
        USER_ID_HEADER_KEY, headers.get(USER_ID_HEADER_KEY).toString());
  }

  public static final Date parseStringToDateTime(String dateTimeStr) throws ReceivingException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm:ss aa");
    try {
      return dateFormat.parse(dateTimeStr);
    } catch (ParseException e) {
      log.error(e.getMessage(), e);
      throw new ReceivingException(ReceivingException.PRODATE_CONVERSION_ERROR);
    }
  }

  /**
   * This will convert any quantity and its UOM into desired quantity and its UOM
   *
   * @param quantity
   * @param quantityUOM
   * @param desiredUOM
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public static Integer conversionToUOM(
      Integer quantity, String quantityUOM, String desiredUOM, Integer vnpkQty, Integer whpkQty) {
    Integer qty;
    switch (desiredUOM) {
      case ReceivingConstants.Uom.VNPK:
        qty = conversionToVNPK(quantity, quantityUOM, vnpkQty, whpkQty);
        break;
      case ReceivingConstants.Uom.WHPK:
        qty = conversionToWHPK(quantity, quantityUOM, vnpkQty, whpkQty);
        break;
      case ReceivingConstants.Uom.EACHES:
        qty = conversionToEaches(quantity, quantityUOM, vnpkQty, whpkQty);
        break;
      default:
        qty = 0;
    }
    return qty;
  }

  public static Integer conversionToVNPK(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Integer qty;
    switch (uom) {
      case ReceivingConstants.Uom.VNPK:
        qty = quantity;
        break;
      case ReceivingConstants.Uom.WHPK:
        qty = (quantity * whpkQty) / vnpkQty;
        break;
      case ReceivingConstants.Uom.EACHES:
        qty = quantity / vnpkQty;
        break;
      default:
        qty = 0;
    }
    return qty;
  }

  public static Integer conversionToWHPK(
      Integer quantity, String uom, Integer vnpkQty, Integer whpkQty) {
    Integer qty;
    switch (uom) {
      case ReceivingConstants.Uom.VNPK:
        qty = (quantity * vnpkQty) / whpkQty;
        break;
      case ReceivingConstants.Uom.WHPK:
        qty = quantity;
        break;
      case ReceivingConstants.Uom.EACHES:
        qty = quantity / whpkQty;
        break;
      default:
        qty = 0;
    }
    return qty;
  }

  public static String getLabelTypeCode(List<String> pkgInstructions) {
    String labelTypeCode = null;
    if (!CollectionUtils.isEmpty(pkgInstructions)) {
      for (String pkgInstruction : pkgInstructions) {
        switch (pkgInstruction) {
          case ReceivingConstants.PKG_INSTRUCTION_CODE_965:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3480;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_966:
          case ReceivingConstants.PKG_INSTRUCTION_CODE_967:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3481;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_968:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3090;
            break;

          case ReceivingConstants.PKG_INSTRUCTION_CODE_969:
          case ReceivingConstants.PKG_INSTRUCTION_CODE_970:
            labelTypeCode = ReceivingConstants.LITHIUM_LABEL_CODE_3091;
            break;

          default:
            break;
        }
      }
    }
    return labelTypeCode;
  }

  public static <T> T convertValue(Object fromValue, TypeReference<?> toValueTypeRef) {
    ObjectMapper mapper = new ObjectMapper();
    return (T) mapper.convertValue(fromValue, toValueTypeRef);
  }

  public static Container getConsolidatedContainerAndPublishContainer(
      Container parentContainer,
      HttpHeaders httpHeaders,
      boolean putToRetry,
      TenantSpecificConfigReader configUtils,
      ContainerService containerService)
      throws ReceivingException {
    Container consolidatedContainer = containerService.getContainerIncludingChild(parentContainer);
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ReceivingConstants.PUBLISH_CONTAINER)) {
      publishConsolidatedContainer(
          consolidatedContainer, httpHeaders, putToRetry, configUtils, containerService);
    }
    return consolidatedContainer;
  }

  public static void publishConsolidatedContainer(
      Container consolidatedContainer,
      HttpHeaders httpHeaders,
      boolean putToRetry,
      TenantSpecificConfigReader configUtils,
      ContainerService containerService) {
    Map<String, Object> headersToSend = getForwardableHeadersWithRequestOriginator(httpHeaders);
    headersToSend.put(ReceivingConstants.IDEM_POTENCY_KEY, consolidatedContainer.getTrackingId());

    // ignore SCT header
    addOrReplaceHeader(
        headersToSend,
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_IGNORE_SCT_HEADER_ADDED, false),
        IGNORE_SCT,
        TRUE_STRING);

    containerService.publishContainer(consolidatedContainer, headersToSend, putToRetry);
  }

  public static List<Map<String, Object>> getOldPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB, String rotateDate, TenantSpecificConfigReader configUtils)
      throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printJob.get("labelData");
    // if PO's are non-national,then we need add few properties which are required for label
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate, configUtils));
    printJob.put("labelData", labelData);
    printJob.put("data", labelData);
    return Arrays.asList(printJob);
  }

  public static List<Map<String, Object>> getAdditionalParam(
      Instruction instruction, String rotateDate, TenantSpecificConfigReader configUtils)
      throws ReceivingException {

    List<Map<String, Object>> additionalAttributeList = new ArrayList<>();
    int qty = instruction.getReceivedQuantity();
    // Prepare new key/value pair for total case quantity received on pallet
    Map<String, Object> quantityMap = new HashMap<>();
    quantityMap.put("key", "QTY");
    quantityMap.put("value", qty);
    additionalAttributeList.add(quantityMap);

    if (configUtils.isShowRotateDateOnPrintLabelEnabled(getFacilityNum())
        && !org.springframework.util.StringUtils.isEmpty(rotateDate)) {
      Map<String, Object> rotateDateMap = new HashMap<>();
      rotateDateMap.put("key", "ROTATEDATE");
      rotateDateMap.put("value", rotateDate);
      additionalAttributeList.add(rotateDateMap);
    }

    // if PO's are non-national,then we need add few properties which are required for label
    if (instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.POCON.toString())
        || instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.DSDC.toString())) {
      String originalChannel = instruction.getOriginalChannel();
      if (!Objects.isNull(originalChannel)) {
        Map<String, Object> originalPoType = new HashMap<>();
        originalPoType.put("key", "CHANNELMETHOD");
        originalPoType.put("value", instruction.getOriginalChannel());
        additionalAttributeList.add(originalPoType);
      }
    }
    return additionalAttributeList;
  }

  public static Map<String, Object> getPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB,
      String rotateDate,
      LabelServiceImpl labelServiceImpl,
      TenantSpecificConfigReader configUtils)
      throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    // if PO's are non-national,then we need add few properties which are required for label
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate, configUtils));

    List<LabelData> labelDataList = new ArrayList<>();
    labelData
        .stream()
        .forEach(
            labelDataValue -> {
              LabelData labelDataObj =
                  new LabelData(
                      String.valueOf(labelDataValue.get("key")),
                      String.valueOf(labelDataValue.get("value")));
              labelDataList.add(labelDataObj);
            });

    printRequest.put("data", labelServiceImpl.getLocaleSpecificValue(labelDataList));
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);
    return printJob;
  }

  /**
   * Process RDS response for found/error objects
   *
   * <p>If No receiving information found (NIMRDS-023) for PO/POLine in RDS then default received
   * qty as 0 If PO Line is cancelled in RDS (NIMRDS-025) then don't populate both
   * receivedQtyMapByPoAndPoLine & errorResponseMapByPoAndPoLine to filter the PoLine
   *
   * @param rdsReceiptsResponse
   * @return ReceivedQuantityResponseFromRDS
   */
  public static ReceivedQuantityResponseFromRDS handleRDSResponse(
      RdsReceiptsResponse rdsReceiptsResponse) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    Map<String, String> errorResponseMapByPoAndPoLine = new HashMap<>();
    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
        rdsReceiptsResponse.getFound())) {
      List<Found> foundListResponse = rdsReceiptsResponse.getFound();
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(foundListResponse)) {
        foundListResponse.forEach(
            foundResponse -> {
              String key =
                  foundResponse.getPoNumber()
                      + ReceivingConstants.DELIM_DASH
                      + foundResponse.getPoLine();
              Integer receivedQty = foundResponse.getTotal();
              log.info(
                  "Receipts found for PO:{} and POL:{}, totalReceivedQty:{}",
                  foundResponse.getPoNumber(),
                  foundResponse.getPoLine(),
                  receivedQty);
              receivedQtyMapByPoAndPoLine.put(key, Long.valueOf(receivedQty));
            });
      }
    }

    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
        rdsReceiptsResponse.getErrors())) {
      List<Error> errorsList = rdsReceiptsResponse.getErrors();
      log.error("Received an error response:{} from RDS", errorsList);
      errorsList.forEach(
          error -> {
            String key = error.getPoNumber() + ReceivingConstants.DELIM_DASH + error.getPoLine();
            String errorCode = error.getErrorCode();
            if (errorCode.equalsIgnoreCase(
                ReceivingConstants.NO_RECEIPTS_FOUND_IN_RDS_FOR_PO_AND_POL)) {
              receivedQtyMapByPoAndPoLine.put(key, Long.valueOf(0));
            } else if (errorCode.equalsIgnoreCase(ReceivingConstants.PO_LINE_IS_CANCELLED_IN_RDS)) {
              log.error(
                  "PO Line:{} is Cancelled in RDS for PO: {}",
                  error.getPoLine(),
                  error.getPoNumber());
            } else {
              String message = error.getMessage();
              errorResponseMapByPoAndPoLine.put(key, message);
            }
          });
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorResponseMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static final String INVALID_HANDLING_METHOD_OR_PACK_TYPE =
      "Invalid Handling method or Pack type";
  public static final Map<String, String> PACKTYPE_HANDLINGCODE_MAP;

  static {
    Map<String, String> packTypeHandlingCodeMap = new HashMap<>();
    packTypeHandlingCodeMap.put("BC", "Breakpack Conveyable");
    packTypeHandlingCodeMap.put("BN", "Breakpack Non-Conveyable Slotting");
    packTypeHandlingCodeMap.put("BO", "Breakpack Offline Slotting");
    packTypeHandlingCodeMap.put("BM", "Breakpack Conveyable Picks");
    packTypeHandlingCodeMap.put("BP", "Breakpack Voice PUT");
    packTypeHandlingCodeMap.put("BV", "Breakpack Non-Con Voice Pick");
    packTypeHandlingCodeMap.put("BE", "Item Ineligible for Symbotic Breakpack Cases");
    packTypeHandlingCodeMap.put("BB", "Breakpack Non-Conveyable To RTS PUT");
    packTypeHandlingCodeMap.put("BX", "Do Not Receive");
    packTypeHandlingCodeMap.put("BR", "Casepack Restricted");
    packTypeHandlingCodeMap.put("BI", "Breakpack Symbotic Auto");
    packTypeHandlingCodeMap.put("BJ", "Breakpack Symbotic Manual");
    packTypeHandlingCodeMap.put("CC", "Casepack Conveyable");
    packTypeHandlingCodeMap.put("CB", "Casepack Non-Conveyable To RTS PUT");
    packTypeHandlingCodeMap.put("CN", "Casepack Non-Conveyable Slotting");
    packTypeHandlingCodeMap.put("CP", "Casepack Voice PUT");
    packTypeHandlingCodeMap.put("CL", "Casepack Non-Conveyable To Shipping");
    packTypeHandlingCodeMap.put("CD", "Casepack To Depal");
    packTypeHandlingCodeMap.put("CV", "Casepack Non-Con Voice Pick");
    packTypeHandlingCodeMap.put("CS", "Casepack Conveyable Storage");
    packTypeHandlingCodeMap.put("CT", "Casepack Non-Conveyable Storage");
    packTypeHandlingCodeMap.put("CI", "Casepack Automatic Inbound");
    packTypeHandlingCodeMap.put("CJ", "Casepack Manual Inbound");
    packTypeHandlingCodeMap.put("CE", "Item Ineligible for Symbotic");
    packTypeHandlingCodeMap.put("CX", "Do Not Receive");
    packTypeHandlingCodeMap.put("CR", "Casepack Restricted");
    packTypeHandlingCodeMap.put("MC", "Casepack Master Carton Conveyable");
    packTypeHandlingCodeMap.put("MN", "Casepack Master Carton Non-Conveyable");
    packTypeHandlingCodeMap.put("MP", "Casepack Master Carton Voice PUT");
    packTypeHandlingCodeMap.put("MV", "Casepack Master Carton Non-Con Voice Pick");
    packTypeHandlingCodeMap.put("MX", "Do Not Receive");
    packTypeHandlingCodeMap.put("MR", "Casepack Master Carton Restricted");
    packTypeHandlingCodeMap.put("MI", "Casepack Master Carton Symbotic Auto");
    packTypeHandlingCodeMap.put("MJ", "Casepack Master Carton Manual Inbound");
    packTypeHandlingCodeMap.put("ME", "Casepack Master Carton Ineligible");
    packTypeHandlingCodeMap.put("PC", "Breakpack Master Carton Conveyable");
    packTypeHandlingCodeMap.put("PN", "Breakpack Master Carton Non-Conveyable");
    packTypeHandlingCodeMap.put("PP", "Breakpack Master Carton Voice PUT");
    packTypeHandlingCodeMap.put("PV", "Breakpack Master Carton Non-Con Voice Pick");
    packTypeHandlingCodeMap.put("PI", "Breakpack Master Carton Symbotic Auto");
    packTypeHandlingCodeMap.put("PJ", "Breakpack Master Carton Symbotic Manual");
    packTypeHandlingCodeMap.put("PE", "Breakpack Master Carton Symbotic Ineligible");
    packTypeHandlingCodeMap.put("PR", "Breakpack Master Carton Restricted");

    PACKTYPE_HANDLINGCODE_MAP = Collections.unmodifiableMap(packTypeHandlingCodeMap);
  }

  public static boolean isAsnReceivingOverrideEligible(HttpHeaders headers) {
    return (ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_VALUE)
        .equalsIgnoreCase(headers.getFirst(ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_KEY));
  }

  public static <V> V convertStringToObject(String jsonString, TypeReference<V> reference) {
    return convertStringToObject(jsonString, reference, new ObjectMapper());
  }

  public static <V> V convertStringToObject(
      String jsonString, TypeReference<V> reference, ObjectMapper mapper) {
    try {
      return mapper.readValue(jsonString, reference);
    } catch (IOException e) {
      log.error("Unable to parse JSON String ", e);
      return null;
    }
  }

  public static LocalDate convertToLocalDateViaInstant(Date dateToConvert) {
    return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /**
   * checks if pallet's any one of current move TypeAndStatus present in configured list in ccm as
   * big string with comma separated. Returns true if presents else false
   *
   * @param moveContainerDetailList List of current move's Type+Status
   * @param validMoveTypeAndStatusListConfigured this can be string array or big string configured
   *     as type+status
   * @return
   */
  public static boolean isMoveTypeAndStatusPresent(
      List<String> moveContainerDetailList, String validMoveTypeAndStatusListConfigured) {
    return moveContainerDetailList
        .stream()
        .anyMatch(
            moveDetail ->
                validMoveTypeAndStatusListConfigured
                    .toLowerCase()
                    .contains(moveDetail.toLowerCase()));
  }

  /**
   * returns true if given moveTypeStatusList contains any given invalidMoveValueConfigured
   *
   * @param moveTypeStatusList moveTypeStatusList is list of current move type+status values.
   * @param invalidMoveValueConfigured Only one ccm value eg: "working" or "PutawayCompleted". Value
   *     should not be list or string array
   * @return
   */
  public static boolean isInvalidMovePresent(
      List<String> moveTypeStatusList, String invalidMoveValueConfigured) {
    if (isBlank(invalidMoveValueConfigured)) return false;
    return moveTypeStatusList
        .stream()
        .anyMatch(
            aMoveTypeStatus ->
                aMoveTypeStatus.toUpperCase().contains(invalidMoveValueConfigured.toUpperCase()));
  }

  public static Integer computeEffectiveTotalQty(
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean importInd,
      TenantSpecificConfigReader configUtils) {
    if (isImportPoLineFbqEnabled(importInd, configUtils)) {
      return Optional.ofNullable(deliveryDocumentLine.getFreightBillQty()).orElse(0);
    }
    return deliveryDocumentLine.getTotalOrderQty();
  }

  public static Integer computeEffectiveMaxReceiveQty(
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean importInd,
      TenantSpecificConfigReader configUtils) {
    if (isImportPoLineFbqEnabled(importInd, configUtils)) {
      return Optional.ofNullable(deliveryDocumentLine.getFreightBillQty()).orElse(0);
    }
    return deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
  }

  public static Boolean isImportPoLineFbqEnabled(
      Boolean importInd, TenantSpecificConfigReader configUtils) {
    return configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK)
        && Optional.ofNullable(importInd).orElse(Boolean.FALSE);
  }

  public static boolean isOssTransfer(
      Integer poTypeCode, String fromPoLineDCNumber, TenantSpecificConfigReader configUtils) {
    String eligibleTransferPOs =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            ELIGIBLE_TRANSFER_POS_CCM_CONFIG,
            DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    Set<Integer> eligibleTransferPOSet =
        Arrays.stream(eligibleTransferPOs.split(","))
            .map(Integer::valueOf)
            .collect(Collectors.toSet());
    return eligibleTransferPOSet.contains(poTypeCode)
        && String.valueOf(TenantContext.getFacilityNum()).equalsIgnoreCase(fromPoLineDCNumber);
  }

  public static void validateUnloaderEventType(String eventType) throws ReceivingBadDataException {
    if (isBlank(eventType)) {
      throw new ReceivingBadDataException(INVALID_DATA, INVALID_UNLOADER_EVENT_TYPE);
    }
  }

  public static boolean isTransferMerchandiseFromOssToMain(
      Map<String, String> containerItemMiscInfo) {
    if (nonNull(containerItemMiscInfo)
        && containerItemMiscInfo.containsKey(IS_RECEIVE_FROM_OSS)
        && Boolean.valueOf(containerItemMiscInfo.get(IS_RECEIVE_FROM_OSS))) {
      return true;
    }
    return false;
  }

  public static void validateDeliveryNumber(Long deliveryNumbers) {
    if (Objects.isNull(deliveryNumbers)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, ReceivingConstants.INVALID_DELIVERY_NUMBER);
    }
  }

  /**
   * Check BOL weight for a given line
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public static void validateVariableWeightForVariableItem(
      DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
    Float bolWeight = deliveryDocumentLine.getBolWeight();
    log.info(
        "po={} poLine={} itemNbr={} bolWeight={}",
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        deliveryDocumentLine.getItemNbr(),
        bolWeight);

    if (nonNull(deliveryDocumentLine.getAdditionalInfo())
        && VARIABLE_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
            deliveryDocumentLine.getAdditionalInfo().getWeightFormatTypeCode())
        && (Objects.isNull(bolWeight) || bolWeight == 0)) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue("MISSING_BOL_WEIGHT_ERROR");
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(instructionError.getErrorMessage())
              .errorCode(instructionError.getErrorCode())
              .errorHeader(instructionError.getErrorHeader())
              .errorKey(ExceptionCodes.INVALID_BOL_WEIGHT_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public static boolean hasInductedIntoMech(String automationType, Boolean isPrimeSlot) {
    return (AUTOMATION_TYPE_SWISSLOG.equalsIgnoreCase(automationType)
            && (isNull(isPrimeSlot) || !isPrimeSlot))
        || ((AUTOMATION_TYPE_SCHAEFER.equalsIgnoreCase(automationType)
                || AUTOMATION_TYPE_DEMATIC.equalsIgnoreCase(automationType))
            && nonNull(isPrimeSlot)
            && isPrimeSlot);
  }

  public static boolean isMechContainer(Map<String, Object> miscInfo) {
    return nonNull(miscInfo)
        && nonNull(miscInfo.get(IS_MECH_CONTAINER))
        && Boolean.TRUE.equals(miscInfo.get(IS_MECH_CONTAINER));
  }

  public static boolean isReceiveCorrectionPastThreshold(
      LocalDate finalizedDate, String allowedDaysAfterFinalised) {
    if (finalizedDate == null || isBlank(allowedDaysAfterFinalised)) return false;
    final LocalDate thresholdDate =
        LocalDate.now().minusDays(Long.parseLong(allowedDaysAfterFinalised));
    if (!finalizedDate.isBefore(thresholdDate)) {
      return false;
    }
    log.info(
        "ReceiveCorrection not allowed past threshold date={}(={} + {}Days)",
        thresholdDate,
        finalizedDate,
        allowedDaysAfterFinalised);
    return true;
  }

  public static Map<String, Object> convertHttpHeadersToHashMap(HttpHeaders httpHeaders) {
    Map<String, Object> headers = new HashMap();
    headers.putAll(httpHeaders.toSingleValueMap());
    headers.put(ORIGIN_TS, new Date().toString());
    return headers;
  }

  public static Map<String, Object> enrichKafkaHeaderForRapidRelayer(
      Map<String, Object> headers, String correlationId) {
    if (Objects.isNull(headers.get(ReceivingConstants.TENENT_FACLITYNUM))
        || Objects.isNull(headers.get(ReceivingConstants.TENENT_COUNTRY_CODE))) {
      headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
      headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    }

    if (Objects.isNull(headers.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY))) {
      headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
    }
    if (Objects.isNull(headers.get(ReceivingConstants.API_VERSION))) {
      headers.put(ReceivingConstants.API_VERSION, ReceivingConstants.API_VERSION_VALUE);
    }
    if (Objects.isNull(headers.get(ReceivingConstants.REQUEST_ORIGINATOR))) {
      headers.put(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    }
    if (Objects.isNull(headers.get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      headers.put(USER_ID_HEADER_KEY, getUserId());
    }
    return headers;
  }

  public static Map<String, Object> getNewPrintJob(
      Map<String, Object> printJob,
      Instruction instruction,
      TenantSpecificConfigReader configUtils) {
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN)
        && instruction.getChildContainers() != null) {
      List<Map<String, Object>> printRequests =
          (List<Map<String, Object>>) printJob.get("printRequests");
      String diabledLabelFormat =
          configUtils.getCcmValue(
              getFacilityNum(),
              ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT,
              ReceivingConstants.EMPTY_STRING);
      List<Map<String, Object>> filteredLabels =
          printRequests
              .stream()
              .filter(map -> !map.get("formatName").equals(diabledLabelFormat))
              .collect(Collectors.toList());
      printJob.put(ReceivingConstants.PRINT_REQUESTS, filteredLabels);
    }
    return printJob;
  }

  public static boolean validateMoveByHaulPutawayCombo(List<String> containerMoveTypeAndStatuses) {
    // if no moves then allow VTR
    if (isNull(containerMoveTypeAndStatuses) || containerMoveTypeAndStatuses.isEmpty()) return true;

    // if no haul And no putaway listed then allow
    final boolean noHaul =
        containerMoveTypeAndStatuses.stream().noneMatch(mv -> mv.toLowerCase().startsWith(HAUL));
    final boolean noPutaway =
        containerMoveTypeAndStatuses.stream().noneMatch(mv -> mv.toLowerCase().startsWith(PUTAWAY));
    if (noHaul && noPutaway) return true;

    // if no haul listed then check putaway - if putaway-open  or putaway-failed then allow
    boolean isPutAwayOpenOrFailed =
        containerMoveTypeAndStatuses.contains(PUTAWAY + OPEN)
            || containerMoveTypeAndStatuses.contains(PUTAWAY + FAILED);
    if (noHaul && isPutAwayOpenOrFailed) return true;

    // if haul-open, haul-failed then check if putaway-open or putaway-failed then allow
    final boolean isHaulOpenOrFailed =
        containerMoveTypeAndStatuses.contains(HAUL + OPEN)
            || containerMoveTypeAndStatuses.contains(HAUL + FAILED);
    return isHaulOpenOrFailed && isPutAwayOpenOrFailed;
  }

  public static String getString(Object object) {
    return object == null ? null : object.toString();
  }

  /**
   * This method will add freight type as SSTK, DA, DSDC on the receipts headers. Orders will
   * consume this header & filter only for DA Receipts & process them on their end
   *
   * @param httpHeaders
   */
  public static void populateFreightTypeInHeader(
      List<ContainerDTO> containers,
      Map<String, Object> httpHeaders,
      TenantSpecificConfigReader configUtils) {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_EVENT_TYPE_HEADER_ENABLED, false)) {
      String inboundChannelMethod =
          CollectionUtils.isEmpty(containers.get(0).getChildContainers())
              ? containers.get(0).getContainerItems().get(0).getInboundChannelMethod()
              : new ArrayList<>(containers.get(0).getChildContainers())
                  .get(0)
                  .getContainerItems()
                  .get(0)
                  .getInboundChannelMethod();
      if (Objects.nonNull(inboundChannelMethod)) {
        String eventType =
            SSTK_CHANNEL_METHODS_FOR_RDC.contains(inboundChannelMethod)
                ? PURCHASE_REF_TYPE_SSTK
                : DA_CHANNEL_METHODS_FOR_RDC.contains(inboundChannelMethod)
                    ? PURCHASE_REF_TYPE_DA
                    : PURCHASE_REF_TYPE_DSDC;
        httpHeaders.put(EVENT_TYPE, eventType);
      }
    }
  }

  public static HttpHeaders populateHttpHeadersFromKafkaHeaders(Map<String, byte[]> kafkaHeaders) {
    String correlationId = null;
    Integer facilityNumber =
        Integer.parseInt(new String(kafkaHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)));
    String facilityCountryCode =
        new String(kafkaHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    if (Objects.nonNull(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY))) {
      correlationId = new String(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY));
    } else if (Objects.nonNull(kafkaHeaders.get(CORRELATION_ID))) {
      correlationId = new String(kafkaHeaders.get(CORRELATION_ID));
    } else if (Objects.nonNull(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY_FALLBACK))) {
      correlationId = new String(kafkaHeaders.get(CORRELATION_ID_HEADER_KEY_FALLBACK));
    } else {
      correlationId = UUID.randomUUID().toString();
    }
    String userId =
        Objects.nonNull(kafkaHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY))
            ? new String(kafkaHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY))
            : ReceivingConstants.FLIB_USER;
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, String.valueOf(facilityNumber));
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
    if (Objects.nonNull(kafkaHeaders.get(ReceivingConstants.TENENT_GROUP_TYPE))) {
      httpHeaders.add(
          ReceivingConstants.TENENT_GROUP_TYPE,
          new String(kafkaHeaders.get(ReceivingConstants.TENENT_GROUP_TYPE)));
    }
    if (Objects.nonNull(kafkaHeaders.get(ReceivingConstants.GROUP_NBR))) {
      httpHeaders.add(
          ReceivingConstants.DELIVERY_NUMBER,
          new String(kafkaHeaders.get(ReceivingConstants.GROUP_NBR)));
    }
    if (Objects.nonNull(kafkaHeaders.get(EVENT_TYPE))) {
      httpHeaders.add(EVENT_TYPE, new String(kafkaHeaders.get(EVENT_TYPE)));
    }
    return httpHeaders;
  }

  public static Map<String, byte[]> populateKafkaHeadersFromHttpHeaders(HttpHeaders httpHeaders) {
    Map<String, byte[]> kafkaHeaders = new HashMap<>();
    Integer facilityNumber =
        Integer.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    String facilityCountryCode =
        String.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    String correlationId =
        String.valueOf(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    String userId =
        CollectionUtils.isEmpty(httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY))
            ? ReceivingConstants.FLIB_USER
            : String.valueOf(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    String groupType = String.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_GROUP_TYPE));
    kafkaHeaders.put(ReceivingConstants.TENENT_GROUP_TYPE, groupType.getBytes());
    kafkaHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode.getBytes());
    kafkaHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId.getBytes());
    kafkaHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNumber.toString().getBytes());
    kafkaHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, userId.getBytes());
    return kafkaHeaders;
  }

  public static Map<String, Object> getHawkeyePublishHeaders(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put(CORRELATION_ID_HEADER_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    messageHeaders.put(TENENT_FACLITYNUM, httpHeaders.getFirst(TENENT_FACLITYNUM));
    messageHeaders.put(TENENT_COUNTRY_CODE, httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    messageHeaders.put(
        ReceivingConstants.PRODUCT_NAME_HEADER_KEY, ReceivingConstants.APP_NAME_VALUE);
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    messageHeaders.put(
        ReceivingConstants.CONTENT_ENCODING, ReceivingConstants.CONTENT_ENCODING_GZIP);
    return messageHeaders;
  }

  public static boolean isDeliveryExistsOfType(
      List<DeliveryEvent> deliveryEvents, String eventType) {
    return deliveryEvents.parallelStream().anyMatch(o -> o.getEventType().equals(eventType));
  }

  public static List<EventTargetStatus> getPendingAndInProgressEventStatuses() {
    List<EventTargetStatus> eventTargetStatuses = new ArrayList<>();
    eventTargetStatuses.add(EventTargetStatus.PENDING);
    eventTargetStatuses.add(EventTargetStatus.IN_PROGRESS);
    return eventTargetStatuses;
  }

  public static List<EventTargetStatus> getEventStatusesForScheduler() {
    List<EventTargetStatus> eventsForScheduler = new ArrayList<>();
    eventsForScheduler.add(EventTargetStatus.PENDING);
    eventsForScheduler.add(EventTargetStatus.STALE);
    return eventsForScheduler;
  }

  public static HttpHeaders getHawkeyeForwardableHeaders(HttpHeaders httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(WMT_MSG_TIMESTAMP, LocalDateTime.now().toString());
    String correlationId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    requestHeaders.add(
        CORRELATION_ID_HEADER_KEY,
        org.springframework.util.StringUtils.hasText(correlationId)
            ? correlationId
            : randomUUID().toString());
    requestHeaders.add(
        WMT_FACILITY_NUM,
        !Objects.isNull(getFacilityNum())
            ? getFacilityNum().toString()
            : httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    requestHeaders.add(
        WMT_FACILITY_COUNTRY_CODE,
        org.springframework.util.StringUtils.hasText(getFacilityCountryCode())
            ? getFacilityCountryCode()
            : httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    requestHeaders.add(CONTENT_TYPE, APPLICATION_JSON);
    return requestHeaders;
  }

  public static final Date parseStringToDateTime(String dateTimeStr, String pattern)
      throws ReceivingException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
    try {
      return dateFormat.parse(dateTimeStr);
    } catch (ParseException e) {
      log.error(e.getMessage(), e);
      throw new ReceivingException(ReceivingException.PRODATE_CONVERSION_ERROR);
    }
  }

  public static void validateUnloaderInfoRequiredFields(UnloaderInfoDTO unloaderInfo)
      throws ReceivingBadDataException {
    if (Objects.isNull(unloaderInfo)
        || Objects.isNull(unloaderInfo.getDeliveryNumber())
        || isBlank(unloaderInfo.getPurchaseReferenceNumber())
        || Objects.isNull(unloaderInfo.getPurchaseReferenceLineNumber())) {
      throw new ReceivingBadDataException(INVALID_DATA, INVALID_UNLOADER_EVENT_TYPE);
    }
  }

  public static boolean isDsdcDeliveryDocuments(List<DeliveryDocument> deliveryDocuments) {
    boolean isDsdcPoType = false;
    List<DeliveryDocument> filteredDSDCDocuments =
        deliveryDocuments
            .stream()
            .filter(ReceivingUtils::isDSDCDocument)
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(filteredDSDCDocuments)) {
      isDsdcPoType = true;
    }
    return isDsdcPoType;
  }

  public static boolean isDSDCDocument(DeliveryDocument deliveryDocument) {
    Boolean isDSDCDocument = false;
    if (!CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
      List<DeliveryDocumentLine> filteredDSDCDocumentLines =
          deliveryDocument
              .getDeliveryDocumentLines()
              .stream()
              .filter(
                  line ->
                      ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC.equals(
                          line.getPurchaseRefType()))
              .collect(Collectors.toList());
      isDSDCDocument =
          filteredDSDCDocumentLines.size() == deliveryDocument.getDeliveryDocumentLines().size();
    }
    return isDSDCDocument;
  }

  /**
   * A valid dateString should be of format MM/dd/yyyy
   *
   * @param dateString
   * @return
   */
  public static boolean isValidDeliverySearchClientTimeFormat(String dateString) {
    final String regex = "\\d{2}/\\d{2}/\\d{4}";
    final Pattern pattern = Pattern.compile(regex);
    if (StringUtils.isEmpty(dateString)) {
      return false;
    }
    Matcher matcher = pattern.matcher(dateString);
    return matcher.matches();
  }

  /**
   * @param rdsReceiptsResponse
   * @return
   */
  public static ReceivedQuantityResponseFromRDS handleRdsResponseForInProgressPOs(
      RdsReceiptsResponse rdsReceiptsResponse) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    Map<String, String> errorResponseMapByPoAndPoLine = new HashMap<>();
    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
        rdsReceiptsResponse.getFound())) {
      List<Found> foundListResponse = rdsReceiptsResponse.getFound();
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(foundListResponse)) {
        foundListResponse.forEach(
            foundResponse -> {
              String key =
                  foundResponse.getPoNumber()
                      + ReceivingConstants.DELIM_DASH
                      + foundResponse.getPoLine();
              Integer receivedQty = foundResponse.getTotal();
              log.info(
                  "Receipts found for PO:{} and POL:{}, totalReceivedQty:{}",
                  foundResponse.getPoNumber(),
                  foundResponse.getPoLine(),
                  receivedQty);
              receivedQtyMapByPoAndPoLine.put(key, Long.valueOf(receivedQty));
            });
      }
    }

    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
        rdsReceiptsResponse.getErrors())) {
      List<Error> errorsList = rdsReceiptsResponse.getErrors();
      log.error("Received an error response:{} from RDS", errorsList);
      errorsList.forEach(
          error -> {
            String key = error.getPoNumber() + ReceivingConstants.DELIM_DASH + error.getPoLine();
            String errorCode = error.getErrorCode();
            if (errorCode.equalsIgnoreCase(
                    ReceivingConstants.NO_RECEIPTS_FOUND_IN_RDS_FOR_PO_AND_POL)
                || errorCode.equalsIgnoreCase(ReceivingConstants.PO_LINE_IS_CANCELLED_IN_RDS)) {
              receivedQtyMapByPoAndPoLine.put(key, Long.valueOf(0));
            }
          });
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorResponseMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  /**
   * This method validates smart label format
   *
   * @param trackingId
   * @return
   * @throws ReceivingBadDataException
   */
  public static boolean isValidSmartLabelFormat(String trackingId)
      throws ReceivingBadDataException {
    boolean isValidLabelFormat = false;
    try {
      if (isNotBlank(trackingId)) {
        Pattern pattern = Pattern.compile(SMART_LABEL_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(trackingId);
        isValidLabelFormat = matcher.matches();
      }
    } catch (Exception e) {
      log.error("label={}, error={}, stackTrace={}", trackingId, e.getMessage(), getStackTrace(e));
    }
    return isValidLabelFormat;
  }

  /**
   * This method converts Date format and returns String
   *
   * @param inputDate
   * @return
   * @throws ReceivingBadDataException
   */
  public static String convertDateFormat(String inputDate) throws ReceivingException {
    SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");
    SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    Date date = null;
    String outputDateStr = null;

    try {
      date = inputFormat.parse(inputDate);
      outputDateStr = outputFormat.format(date);
    } catch (ParseException e) {
      log.error(e.getMessage(), e);
      throw new ReceivingException(ReceivingException.PRODATE_CONVERSION_ERROR);
    }
    return outputDateStr;
  }

  public static boolean isPassThroughPoLine(String purchaseReferenceType) {
    Predicate<String> passThroughPredicate =
        refType ->
            SetUtils.hashSet(PurchaseReferenceType.DSDC, PurchaseReferenceType.POCON)
                .stream()
                .map(PurchaseReferenceType::name)
                .anyMatch(p -> p.equals(refType));
    return passThroughPredicate.test(purchaseReferenceType);
  }

  public static boolean isPassThroughFreight(
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument document) {
    return document
        .getDeliveryDocumentLines()
        .stream()
        .map(
            com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
                ::getPurchaseRefType)
        .allMatch(ReceivingUtils::isPassThroughPoLine);
  }

  public static boolean isPassThroughFreight(DeliveryDocument document) {
    return document
        .getDeliveryDocumentLines()
        .stream()
        .map(DeliveryDocumentLine::getPurchaseRefType)
        .allMatch(ReceivingUtils::isPassThroughPoLine);
  }
}
