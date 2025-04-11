package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVING;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisKeyValue;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisReceiveIdentity;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRestClient;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisVerifyRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EpcisService {
  private static final Logger LOGGER = LoggerFactory.getLogger(EpcisService.class);

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RxManagedConfig rxManagedConfig;

  @Autowired private EpcisRestClient epcisRestClient;
  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Resource private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Autowired private RxInstructionHelperService rxInstructionHelperService;
  @Resource private InstructionPersisterService instructionPersisterService;
  @Resource private ContainerService containerService;

  /**
   * Verify serialized data with EPCIS
   *
   * @param scannedDataMap
   * @param deliveryDocumentLine
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void verifySerializedData(
      Map<String, ScannedData> scannedDataMap,
      ShipmentDetails shipmentDetails,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders) {
    if (!configUtils.getConfiguredFeatureFlag(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        RxConstants.ENABLE_EPCIS_VERIFICATION_FEATURE_FLAG)) {
      return;
    }
    if (isItemSerializedDepartment(deliveryDocumentLine, httpHeaders)) {
      if (Objects.nonNull(shipmentDetails)) {
        String gln = shipmentDetails.getDestinationGlobalLocationNumber();
        EpcisVerifyRequest epcisVerifyRequest = epcisVerifyPayload(scannedDataMap, gln);
        ResponseEntity<String> verifyResponseEntity =
            epcisRestClient.verifySerializedData(epcisVerifyRequest, httpHeaders);
        if (isCodeInvalid(verifyResponseEntity)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.EPCIS_VERIFY_ERROR, ReceivingException.EPCIS_SERIALIZED_CODE_INVALID);
        }
      }
    } else {
      LOGGER.info(
          "D40 Item, not verified against EPCIS, PO {} PoLine {}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
  }

  /**
   * Publish received Serialized data to EPCIS
   *
   * @param instruction
   * @param deliveryDocumentLine
   * @param httpHeaders
   */
  @Async
  @Timed(
      name = "publishSerializedDataTimed",
      level1 = "uwms-receiving",
      level2 = "epcisService",
      level3 = "publishSerializedData")
  @ExceptionCounted(
      name = "publishSerializedDataExceptionCount",
      level1 = "uwms-receiving",
      level2 = "epcisService",
      level3 = "publishSerializedData")
  public void publishSerializedData(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders) {
    try {
      if (isItemSerializedDepartment(instruction)) {
        List<EpcisRequest> request =
            epcisCapturePayload(instruction, completeInstructionRequest, httpHeaders);
        publishReceiveEvents(request, httpHeaders, instruction.getId().toString());
      } else {
        LOGGER.info("D40 Item, not published to EPCIS, Instruction Id {}", instruction.getId());
      }
    } catch (Exception e) {
      LOGGER.error(
          "Exception while publishing to EPICS for Instruction Id {}", instruction.getId(), e);
    }
  }

  @Async
  public void publishReceiveEvents(
      List<EpcisRequest> epcisRequestList, HttpHeaders httpHeaders, String instructionId) {
    if (CollectionUtils.isNotEmpty(epcisRequestList)) {
      rxCompleteInstructionOutboxHandler.outboxEpcisEvents(
          epcisRequestList, httpHeaders, instructionId);
    }
  }

  private boolean isCodeInvalid(ResponseEntity<String> verifyResponseEntity) {
    return (Objects.isNull(verifyResponseEntity)
        && HttpStatus.OK == verifyResponseEntity.getStatusCode());
  }

  public List<EpcisRequest> epcisCapturePayload(
      Instruction instruction,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders) {
    if (isItemSerializedDepartment(instruction)) {
      DeliveryDocument deliveryDocument =
          gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      return constructEpcisEvents(
          instruction, deliveryDocumentLine, completeInstructionRequest, httpHeaders);
    }
    return Collections.<EpcisRequest>emptyList();
  }

  private EpcisVerifyRequest epcisVerifyPayload(
      Map<String, ScannedData> scannedDataMap, String gln) {
    EpcisVerifyRequest epcisVerifyRequest = new EpcisVerifyRequest();
    ScannedData scannedData = scannedDataMap.get(ApplicationIdentifier.SSCC.getKey());
    if (scannedData != null) {
      ssccVerifyData(scannedData, epcisVerifyRequest);
    } else if (scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()) != null
        && scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()) != null) {
      scannedData = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey());

      String code =
          constructSerializedVerificationCode(ReceivingConstants.EMPTY_STRING, scannedData);
      epcisVerifyRequest.setType(scannedData.getKey().toUpperCase());
      scannedData = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey());
      code = constructSerializedVerificationCode(code, scannedData);

      epcisVerifyRequest.setCode(code);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.EPCIS_VERIFY_ERROR, ReceivingException.SERIALIZED_FIELDS_NOT_AVAILABLE);
    }
    epcisVerifyRequest.setGln(gln);
    epcisVerifyRequest.setActivity(RECEIVING);

    return epcisVerifyRequest;
  }

  private EpcisVerifyRequest ssccVerifyData(
      ScannedData scannedData, EpcisVerifyRequest epcisVerifyRequest) {
    epcisVerifyRequest.setCode(
        constructSerializedVerificationCode(ReceivingConstants.EMPTY_STRING, scannedData));
    epcisVerifyRequest.setType(scannedData.getKey().toUpperCase());
    return epcisVerifyRequest;
  }

  private List<EpcisRequest> constructEpcisEvents(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders) {
    Map<String, String> glnDetails = new HashMap<String, String>();
    glnDetails = gson.fromJson(appConfig.getGlnDetails(), glnDetails.getClass());
    String fullGln =
        glnDetails.get(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM).toString());

    String tenantSGln = readFacilityFormattedGln(fullGln);
    String bizLocation = URN_SGLN.concat(tenantSGln);
    String palletSscc = deliveryDocumentLine.getPalletSSCC();
    EpcisRequest request;
    List<EpcisRequest> epcisRequests = new ArrayList<>();
    List<ContainerDetails> containerDetails = instruction.getChildContainers();
    Map<String, List<ContainerDetails>> groupByLotContainers =
        containerDetails
            .stream()
            .collect(groupingBy(container -> container.getContents().get(0).getLot()));
    Iterator<Map.Entry<String, List<ContainerDetails>>> iterator =
        groupByLotContainers.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<ContainerDetails>> entry = iterator.next();

      request = new EpcisRequest();
      request.setAction(EPCIS_ACTION_ADD);
      request.setBizLocation(bizLocation);
      request.setBizTransactionList(transactionList(deliveryDocumentLine, fullGln));
      request.setBizStep(URN_BIZSTEP_RECEIVING);

      request.setDestinationList(glnList(URN_OWNING_PARTY, URN_SGLN.concat(tenantSGln)));
      request.setSourceList(
          glnList(URN_OWNING_PARTY_ID, String.valueOf(deliveryDocumentLine.getVendorNbrDeptSeq())));
      request.setDisposition(URN_DISP_ACTIVE);
      request.setEpcList(receivedContainersSerializedData(palletSscc, entry.getValue()));
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat(EPCIS_PUBLISH_DATE_FMT);
      simpleDateFormat.setTimeZone(TimeZone.getTimeZone(RxConstants.TIMEZONE_UTC));
      String eventTime = simpleDateFormat.format(new Date());
      request.setEventTime(eventTime);
      request.setRecordTime(eventTime);
      request.setEventTimeZoneOffset(GMT_OFFSET);
      request.setReadPoint(bizLocation);
      asnDetails(
          deliveryDocumentLine,
          entry.getValue(),
          request,
          completeInstructionRequest.isPartialContainer());
      epcisRequests.add(request);
    }
    return epcisRequests;
  }

  protected String readFacilityFormattedGln(String gln) {
    StringBuilder sb = new StringBuilder(gln.substring(0, 12));
    sb.insert(7, ".");
    sb.insert(13, ".");
    sb.append("0");
    return sb.toString();
  }

  private List<String> receivedContainersSerializedData(
      String palletSscc, List<ContainerDetails> childContainer) {
    List<String> childEpcList = new ArrayList<>();
    populateSerializedData(childContainer, childEpcList);
    return childEpcList;
  }

  private void populateSerializedData(
      List<ContainerDetails> childContainers, List<String> childEpcList) {
    childContainers.forEach(
        childContainer ->
            childEpcList.add(
                appendEpcValue(
                    appendEpcValue(
                        ReceivingConstants.EMPTY_STRING,
                        ApplicationIdentifier.GTIN,
                        childContainer.getContents().get(0).getGtin()),
                    ApplicationIdentifier.SERIAL,
                    childContainer.getContents().get(0).getSerial())));
  }

  private void asnDetails(
      DeliveryDocumentLine deliveryDocumentLine,
      List<ContainerDetails> childContainers,
      EpcisRequest request,
      boolean isPartialContainer) {
    Content content = childContainers.get(0).getContents().get(0);
    request.setBatchNumber(epcisKeyValue(URN_BATCH_NUM, content.getLot()));
    String expiryDate = null;
    try {
      expiryDate = RxUtils.formatDate(content.getRotateDate());
    } catch (ParseException e) {
      LOGGER.error(
          "Invalid Date while generating expiry Date {} PO# {} POLine # {} Batch/lot {}",
          content.getRotateDate(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber(),
          content.getLot());
    }
    request.setExpiryDate(epcisKeyValue(URN_DATAEX, expiryDate));
    int lotQty = childContainers.size();
    if (isPartialContainer) {
      request.setQty(epcisKeyValue(URN_QTY, String.valueOf(lotQty)));
    } else {
      int receivedQty =
          ReceivingUtils.conversionToVendorPack(
              childContainers
                  .stream()
                  .mapToInt(childContainer -> childContainer.getContents().get(0).getQty())
                  .sum(),
              ReceivingConstants.Uom.EACHES,
              content.getVendorPack(),
              content.getWarehousePack());
      receivedQty = receivedQty <= 0 ? lotQty : receivedQty;
      request.setQty(epcisKeyValue(URN_QTY, String.valueOf(receivedQty)));
    }
  }

  private List<EpcisReceiveIdentity> transactionList(
      DeliveryDocumentLine deliveryDocumentLine, String tenantGln) {
    List<EpcisReceiveIdentity> identities = new ArrayList<>();
    constructReceiveIdentity(
        identities,
        URN_PO,
        URN_PREPEND + tenantGln + COLON + deliveryDocumentLine.getPurchaseReferenceNumber());

    return identities;
  }

  void constructReceiveIdentity(List<EpcisReceiveIdentity> identities, String type, String value) {
    identities.add(epcisReceiveIdentity(type, value));
  }

  private EpcisReceiveIdentity epcisReceiveIdentity(String type, String value) {
    return EpcisReceiveIdentity.builder().type(type).value(value).build();
  }

  private EpcisKeyValue epcisKeyValue(String key, String value) {
    return EpcisKeyValue.builder().key(key).value(value).build();
  }

  List<EpcisReceiveIdentity> glnList(String type, String value) {
    List<EpcisReceiveIdentity> identities = new ArrayList<>();
    constructReceiveIdentity(identities, type, value);
    return identities;
  }

  private boolean isItemSerializedDepartment(
      DeliveryDocumentLine deliveryDocumentLine, HttpHeaders httpHeaders) {
    return Boolean.FALSE.equals(
        RxUtils.isDscsaExemptionIndEnabled(
            deliveryDocumentLine,
            configUtils.getConfiguredFeatureFlag(
                httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
                RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG)));
  }

  private boolean isItemSerializedDepartment(Instruction instruction) {
    return !RxInstructionType.isExemptedItemInstructionGroup(instruction.getInstructionCode());
  }

  private String constructSerializedVerificationCode(String code, ScannedData scannedData) {
    StringBuilder builder = new StringBuilder(code);
    builder.append(LEFT_PARANTHESIS);
    builder.append(scannedData.getApplicationIdentifier());
    builder.append(RIGHT_PARANTHESIS);
    builder.append(scannedData.getValue());

    return builder.toString();
  }

  public String appendEpcValue(
      String epcValue, ApplicationIdentifier applicationIdentifier, String appendValue) {
    StringBuilder builder = new StringBuilder(epcValue);
    builder.append(LEFT_PARANTHESIS);
    builder.append(applicationIdentifier.getApplicationIdentifier());
    builder.append(RIGHT_PARANTHESIS);
    builder.append(appendValue);
    return builder.toString();
  }

  /**
   * construct epcis event request(s) and outbox
   *
   * @param container parent container
   * @param instruction instruction
   * @param httpHeaders headers
   * @implSpec <a href="https://confluence.walmart.com/x/0OLFWg">Algorithm and pseudo code</a>
   * @throws ReceivingException error fetching unit containers
   */
  public void constructAndOutboxEpcisEvent(
      Container container, Instruction instruction, HttpHeaders httpHeaders)
      throws ReceivingException {
    // gln computation
    Map<String, String> glnDetails = new HashMap<>();
    glnDetails = gson.fromJson(appConfig.getGlnDetails(), glnDetails.getClass());
    String fullGln =
        glnDetails.get(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM).toString());
    String tenantSGln = readFacilityFormattedGln(fullGln);
    String bizLocation = URN_SGLN.concat(tenantSGln);
    AtomicInteger lag = new AtomicInteger();
    EpcisRequest request = new EpcisRequest();
    request.setIch(true);
    request.setValidationPerformed(true);
    request.setEventTimeZoneOffset(GMT_OFFSET);
    request.setReadPoint(bizLocation);
    request.setBizLocation(bizLocation);

    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    boolean isSkipEvents = additionalInfo.getSkipEvents();
    boolean isCompliancePack = additionalInfo.getIsCompliancePack();
    String trackingId = container.getTrackingId();
    String instructionCode = instruction.getInstructionCode();
    String ndc = deliveryDocumentLine.getNdc();
    Set<Container> childContainers = container.getChildContainers();
    Content content = instruction.getChildContainers().get(0).getContents().get(0);
    String palletSscc =
        appendEpcValue(
            ReceivingConstants.EMPTY_STRING,
            ApplicationIdentifier.SSCC,
            transformSscc(deliveryDocumentLine.getPalletSSCC()));
    String palletOfCase =
        appendEpcValue(
            ReceivingConstants.EMPTY_STRING,
            ApplicationIdentifier.SSCC,
            transformSscc(additionalInfo.getPalletOfCase()));
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();

    // pallet rcv
    if (Arrays.asList(
            RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType(),
            RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType())
        .contains(instructionCode) && !additionalInfo.isPalletFlowInMultiSku()) {
      if (!isSkipEvents) {
        clubbedEventTypes.add(CLUBBED_ARRIVING);
        clubbedEpcisEvents.add(
            constructArriving(request, deliveryDocumentLine, singletonList(palletSscc)));
      }
      if (instruction.getProjectedReceiveQty() == instruction.getReceivedQuantity()
          && !isSkipEvents
          && !additionalInfo.getPartialPallet()
          && !additionalInfo.getMultiPOPallet()) {
        clubbedEventTypes.add(CLUBBED_RECEIVING);
        clubbedEpcisEvents.add(
            constructReceiving(
                request,
                deliveryDocumentLine,
                content,
                instruction.getReceivedQuantity(),
                fullGln,
                singletonList(palletSscc),
                lag.addAndGet(lagInterval)));
        if (!isCompliancePack) {
          clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
          clubbedEpcisEvents.add(
              constructUnpackingAll(request, palletSscc, lag.addAndGet(lagInterval)));
        }
      } else {
        List<String> childNoEaForceSgtin =
            childSsccOrSgtin(childContainers, additionalInfo, ndc, false, true);
        if (!isSkipEvents) {
          clubbedEventTypes.add(CLUBBED_UNPACKING_CASE);
          clubbedEpcisEvents.add(
              constructUnpackingCase(request, palletSscc, lag.addAndGet(lagInterval)));
        }
        clubbedEventTypes.add(CLUBBED_RECEIVING);
        clubbedEpcisEvents.add(
            constructReceiving(
                request,
                deliveryDocumentLine,
                content,
                instruction.getReceivedQuantity(),
                fullGln,
                childNoEaForceSgtin,
                lag.addAndGet(lagInterval)));
        if (!isCompliancePack) {
          lag.set(lag.addAndGet(lagInterval));
          clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
          childNoEaForceSgtin.forEach(
              parentId ->
                  clubbedEpcisEvents.add(constructUnpackingAll(request, parentId, lag.get())));
        }
      }
    }

    // case recv
    if (Arrays.asList(
            RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType(),
            RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionType())
        .contains(instructionCode) || (Arrays.asList(
                    RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType())
            .contains(instructionCode)  && additionalInfo.isPalletFlowInMultiSku())) {
      boolean isForceSgtin;
      if (null != additionalInfo.getPalletOfCase()) {
        if (!isSkipEvents) {
          clubbedEventTypes.add(CLUBBED_ARRIVING);
          clubbedEpcisEvents.add(
              constructArriving(request, deliveryDocumentLine, singletonList(palletOfCase)));
          clubbedEventTypes.add(CLUBBED_UNPACKING_CASE);
          clubbedEpcisEvents.add(
              constructUnpackingCase(request, palletOfCase, lag.addAndGet(lagInterval)));
        }
        isForceSgtin = true;
      } else {
        List<String> childNoEa =
            childSsccOrSgtin(childContainers, additionalInfo, ndc, false, false);
        clubbedEventTypes.add(CLUBBED_ARRIVING);
        clubbedEpcisEvents.add(constructArriving(request, deliveryDocumentLine, childNoEa));
        isForceSgtin = false;
      }

      Optional<Container> first = childContainers.stream().findFirst();
      AtomicInteger caseQty = new AtomicInteger();
      first.ifPresent(child -> caseQty.set(child.getContainerItems().get(0).getVnpkQty()));

      if (instruction.getReceivedQuantity() < caseQty.get() && childContainers.size() == 1) {
        if (!isSkipEvents) {
          List<String> childNoEaAndOrForceSgtin =
              childSsccOrSgtin(childContainers, additionalInfo, ndc, false, isForceSgtin);
          if (!isCompliancePack) {
            lag.set(lag.addAndGet(lagInterval));
            clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
            childNoEaAndOrForceSgtin.forEach(
                parentId ->
                    clubbedEpcisEvents.add(constructUnpackingAll(request, parentId, lag.get())));
          }
        }
        // fetch unit container data
        Container unitContainer = null;
        for (Container child : childContainers) {
          unitContainer =
              containerService.getContainerWithChildsByTrackingId(child.getTrackingId(), true);
        }
        if (null != unitContainer) {
          if(!isCompliancePack) {
            List<String> childSsccOrSgtins =  childSsccOrSgtin(unitContainer.getChildContainers(), additionalInfo, ndc, true, true);
            clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
            childSsccOrSgtins.forEach(
                    parentId ->
                            clubbedEpcisEvents.add(constructUnpackingAll(request, parentId, lag.get())));
        }
          clubbedEventTypes.add(CLUBBED_RECEIVING);
          clubbedEpcisEvents.add(
              constructReceiving(
                  request,
                  deliveryDocumentLine,
                  content,
                  instruction.getReceivedQuantity(),
                  fullGln,
                  childSsccOrSgtin(
                      unitContainer.getChildContainers(), additionalInfo, ndc, true, false),
                  lag.addAndGet(lagInterval)));
        }
      } else {
        List<String> childNoEaAndOrForceSgtin =
            childSsccOrSgtin(childContainers, additionalInfo, ndc, false, isForceSgtin);
        clubbedEventTypes.add(CLUBBED_RECEIVING);
        clubbedEpcisEvents.add(
            constructReceiving(
                request,
                deliveryDocumentLine,
                content,
                instruction.getReceivedQuantity(),
                fullGln,
                childNoEaAndOrForceSgtin,
                lag.addAndGet(lagInterval)));
        if (!isCompliancePack) {
          lag.set(lag.addAndGet(lagInterval));
          clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
          childNoEaAndOrForceSgtin.forEach(
              parentId ->
                  clubbedEpcisEvents.add(constructUnpackingAll(request, parentId, lag.get())));
        }
      }

      Instruction instructionMultiSku =
          rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentForCompleteIns(
              instruction.getDeliveryNumber(),
              instruction.getPurchaseReferenceNumber(),
              httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
      if (null != instructionMultiSku) {
        List<String> documentPackIds = new ArrayList<>();
        DeliveryDocument multiSkuDeliveryDoc =
            gson.fromJson(instructionMultiSku.getDeliveryDocument(), DeliveryDocument.class);
        List<DeliveryDocumentLine> multiSkuDeliveryDocumentLines =
            multiSkuDeliveryDoc.getDeliveryDocumentLines();

        for (Container childContainer :childContainers){
          if(null!= childContainer.getContainerMiscInfo()){
            documentPackIds.add((String)childContainer.getContainerMiscInfo().get(ReceivingConstants.DOCUMENT_PACK_ID));
          }
        }

        multiSkuDeliveryDocumentLines.forEach(
            line -> {
              if(null!=line.getAdditionalInfo() && null!= line.getAdditionalInfo().getPacksOfMultiSkuPallet()) {
                line.getAdditionalInfo().getPacksOfMultiSkuPallet().stream().filter(packsOfMultiSkuPallet -> documentPackIds.contains(packsOfMultiSkuPallet.getDocumentPackId())).forEach(pack -> pack.setReceivingStatus(ReceivingConstants.PACK_STATUS_RECEIVED));
              }
            });
        multiSkuDeliveryDoc.setDeliveryDocumentLines(multiSkuDeliveryDocumentLines);
        instructionMultiSku.setDeliveryDocument(gson.toJson(multiSkuDeliveryDoc));
        persistInstruction(instructionMultiSku);
      }
    }

    // partial case recv
    if (RxInstructionType.RX_SER_BUILD_UNITS_SCAN
        .getInstructionType()
        .equalsIgnoreCase(instructionCode)) {
      // fetch unit container data
      Container unitContainer = null;
      for (Container child : childContainers) {
        unitContainer =
            containerService.getContainerWithChildsByTrackingId(child.getTrackingId(), true);
      }

      if (null != additionalInfo.getPalletOfCase()) {
        if (!isSkipEvents) {
          clubbedEventTypes.add(CLUBBED_ARRIVING);
          clubbedEpcisEvents.add(
              constructArriving(request, deliveryDocumentLine, singletonList(palletOfCase)));
          clubbedEventTypes.add(CLUBBED_UNPACKING_CASE);
          clubbedEpcisEvents.add(
              constructUnpackingCase(request, palletOfCase, lag.addAndGet(lagInterval)));
        }
        if (!isCompliancePack) {
          clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
          clubbedEpcisEvents.add(
              constructUnpackingAll(
                  request,
                  childSsccOrSgtin(childContainers, additionalInfo, ndc, false, true).get(0),
                  lag.addAndGet(lagInterval)));
        }
      } else {
        if (!isSkipEvents) {
          List<String> childNoEa =
              childSsccOrSgtin(childContainers, additionalInfo, ndc, false, false);
          clubbedEventTypes.add(CLUBBED_ARRIVING);
          clubbedEpcisEvents.add(constructArriving(request, deliveryDocumentLine, childNoEa));
          if (!isCompliancePack) {
            clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
            clubbedEpcisEvents.add(
                constructUnpackingAll(request, childNoEa.get(0), lag.addAndGet(lagInterval)));
          }
        }
      }
      if (null != unitContainer) {
        clubbedEventTypes.add(CLUBBED_RECEIVING);
        clubbedEpcisEvents.add(
            constructReceiving(
                request,
                deliveryDocumentLine,
                content,
                instruction.getReceivedQuantity(),
                fullGln,
                childSsccOrSgtin(
                    unitContainer.getChildContainers(), additionalInfo, ndc, true, false),
                lag.addAndGet(lagInterval)));
      }
    }

    // check if we are sending only one event
    // then no need to use clubbed flow, use the singular event flow
    if (clubbedEventTypes.size() == 1 && clubbedEpcisEvents.size() == 1) {
      Optional<EpcisRequest> epcisRequest = clubbedEpcisEvents.parallelStream().findFirst();
      Optional<String> eventType = clubbedEventTypes.parallelStream().findFirst();
      if (epcisRequest.isPresent() && eventType.isPresent()) {
        rxCompleteInstructionOutboxHandler.outboxEpcisEvent(
            epcisRequest.get(), httpHeaders, trackingId, Instant.now());
      }
    } else {
      rxCompleteInstructionOutboxHandler.outboxClubbedEpcisEvents(
          clubbedEpcisEvents,
          String.join("-", clubbedEventTypes),
          httpHeaders,
          trackingId,
          Instant.now());
    }
  }

  /**
   * updates fields for ARRIVING event
   *
   * @param request epcis request
   * @param deliveryDocumentLine instruction delivery document line
   * @param epcList epc list
   * @return epcis ARRIVING request
   */
  protected EpcisRequest constructArriving(
      EpcisRequest request, DeliveryDocumentLine deliveryDocumentLine, List<String> epcList) {
    return constructArriving(request, deliveryDocumentLine.getVendorStockNumber(), epcList);
  }

  /**
   * updates fields for ARRIVING event
   *
   * @param request epcis request
   * @param epcList epc list
   * @return epcis ARRIVING request
   */
  EpcisRequest constructArriving(
      EpcisRequest request, String vendorNbrDeptSeq, List<String> epcList) {
    if (Objects.isNull(gson)) gson = new Gson();
    EpcisRequest epcisRequest = gson.fromJson(gson.toJson(request), EpcisRequest.class);
    Instant now = Instant.now();
    epcisRequest.setEventTime(now.toString());
    epcisRequest.setRecordTime(now.toString());
    epcisRequest.setEpcList(epcList);
    epcisRequest.setAction(EPCIS_ACTION_OBSERVE);
    epcisRequest.setBizStep(URN_BIZSTEP_ARRIVING);
    epcisRequest.setDisposition(URN_DISP_IN_PROGRESS);
    epcisRequest.setSourceList(glnList(URN_OWNING_PARTY_ID, String.valueOf(vendorNbrDeptSeq)));
    epcisRequest.setDestinationList(glnList(URN_OWNING_PARTY, epcisRequest.getBizLocation()));
    return epcisRequest;
  }

  /**
   * updates fields for RECEIVING event
   *
   * @param request epcis request
   * @param deliveryDocumentLine instruction delivery document line
   * @param content instruction first child container first content
   * @param receivedQuantity instruction received quantity
   * @param fullGln gln
   * @param epcList epc list
   * @param lag seconds to add
   * @return epcis RECEIVING request
   */
  @SneakyThrows
  protected EpcisRequest constructReceiving(
      EpcisRequest request,
      DeliveryDocumentLine deliveryDocumentLine,
      Content content,
      int receivedQuantity,
      String fullGln,
      List<String> epcList,
      int lag) {
    if (Objects.isNull(gson)) gson = new Gson();
    EpcisRequest epcisRequest = gson.fromJson(gson.toJson(request), EpcisRequest.class);
    Instant eventTime = Instant.now().plusSeconds(lag);
    epcisRequest.setEventTime(eventTime.toString());
    epcisRequest.setRecordTime(eventTime.toString());
    epcisRequest.setEpcList(epcList);
    epcisRequest.setAction(EPCIS_ACTION_OBSERVE);
    epcisRequest.setBizStep(URN_BIZSTEP_RECEIVING);
    epcisRequest.setDisposition(URN_DISP_IN_PROGRESS);
    epcisRequest.setBizTransactionList(transactionList(deliveryDocumentLine, fullGln));
    epcisRequest.setSourceList(
        glnList(URN_OWNING_PARTY_ID, String.valueOf(deliveryDocumentLine.getVendorNbrDeptSeq())));
    epcisRequest.setDestinationList(glnList(URN_OWNING_PARTY, epcisRequest.getBizLocation()));
    if (null != content) {
      epcisRequest.setBatchNumber(epcisKeyValue(URN_BATCH_NUM, content.getLot()));
      epcisRequest.setExpiryDate(
          epcisKeyValue(URN_DATAEX, RxUtils.formatDate(content.getRotateDate())));
    }
    epcisRequest.setQty(epcisKeyValue(URN_QTY, String.valueOf(receivedQuantity))); // TODO: confirm
    return epcisRequest;
  }

  /**
   * updates fields for UNPACKING CASE event
   *
   * @param request epcis request
   * @param parentId epcis parent id
   * @param lag seconds to add
   * @return epcis UNPACKING CASE request
   */
  protected EpcisRequest constructUnpackingCase(EpcisRequest request, String parentId, int lag) {
    EpcisRequest epcisRequest = gson.fromJson(gson.toJson(request), EpcisRequest.class);
    Instant eventTime = Instant.now().plusSeconds(lag);
    epcisRequest.setEventTime(eventTime.toString());
    epcisRequest.setRecordTime(eventTime.toString());
    epcisRequest.setParentID(parentId);
    epcisRequest.setAction(EPCIS_ACTION_DELETE);
    epcisRequest.setBizStep(URN_BIZSTEP_UNPACKING_CASE);
    epcisRequest.setDisposition(URN_DISP_IN_PROGRESS);
    return epcisRequest;
  }

  public EpcisRequest constructUnpackingCase(EpcisRequest request, String parentId) {
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();
    return constructUnpackingCase(request, parentId, lag.addAndGet(lagInterval));
  }

  /**
   * updates fields for UNPACKING ALL event
   *
   * @param request epcis request
   * @param parentId epcis parent id
   * @param lag seconds to add
   * @return epcis UNPACKING ALL request
   */
  protected EpcisRequest constructUnpackingAll(EpcisRequest request, String parentId, int lag) {
    EpcisRequest epcisRequest = gson.fromJson(gson.toJson(request), EpcisRequest.class);
    Instant eventTime = Instant.now().plusSeconds(lag);
    epcisRequest.setEventTime(eventTime.toString());
    epcisRequest.setRecordTime(eventTime.toString());
    epcisRequest.setParentID(parentId);
    epcisRequest.setAction(EPCIS_ACTION_DELETE);
    epcisRequest.setBizStep(URN_BIZSTEP_UNPACKING_ALL);
    epcisRequest.setDisposition(URN_DISP_IN_PROGRESS);
    return epcisRequest;
  }

  /**
   * utility method to populate scannedCase and serializedInfo
   *
   * @param childContainers child containers
   * @param additionalInfo additional info object
   * @param ndc ndc
   * @param includeEa flag to include eaches filter
   * @param forceSgtin flag if sgtin should take precedence
   * @return epc list
   */
  private List<String> childSsccOrSgtin(
      Set<Container> childContainers,
      ItemData additionalInfo,
      String ndc,
      boolean includeEa,
      boolean forceSgtin) {
    List<String> epcList = new ArrayList<>();

    // scanned case
    ManufactureDetail scannedCase = additionalInfo.getScannedCase();
    if (null != scannedCase && !includeEa) {
      childSsccOrSgtin(epcList, scannedCase, forceSgtin);
    }

    // child cases
    additionalInfo
        .getSerializedInfo()
        .stream()
        .filter(info -> includeEa == info.getReportedUom().equalsIgnoreCase(Uom.EACHES))
        .forEach(
            info -> {
              String trackingId = ndc + "_" + info.getSerial();
              Optional<Container> childContainer =
                  childContainers
                      .stream()
                      .filter(child -> child.getTrackingId().equalsIgnoreCase(trackingId))
                      .findFirst();
              childContainer.ifPresent(child -> childSsccOrSgtin(epcList, info, forceSgtin));
            });
    return epcList;
  }

  /**
   * utility method to populate epc list as sscc or sgtin
   *
   * @param epcList epc list
   * @param scannedSerialCase case
   * @param forceSgtin flag if sgtin should take precedence
   */
  public void childSsccOrSgtin(
      List<String> epcList, ManufactureDetail scannedSerialCase, boolean forceSgtin) {
    String sscc = scannedSerialCase.getSscc();
    String gtin = scannedSerialCase.getGtin();
    String serial = scannedSerialCase.getSerial();
    String sgtin = null;
    if (isNotBlank(sscc)) { // sscc
      sscc =
          appendEpcValue(
              ReceivingConstants.EMPTY_STRING, ApplicationIdentifier.SSCC, transformSscc(sscc));
    }
    if (isNotBlank(gtin) && isNotBlank(serial)) { // sgtin
      sgtin =
          appendEpcValue(
              appendEpcValue(ReceivingConstants.EMPTY_STRING, ApplicationIdentifier.GTIN, gtin),
              ApplicationIdentifier.SERIAL,
              serial);
    }
    if (forceSgtin && isNotBlank(sgtin)) { // force sgtin if not null
      epcList.add(sgtin);
    } else {
      if (isNotBlank(sscc)) { // sscc
        epcList.add(sscc);
      } else if (isNotBlank(sgtin)) { // sgtin
        epcList.add(sgtin);
      }
    }
  }

  /**
   * utility method to transform sscc number to 18 chars if not already
   *
   * @param sscc variable char sscc number
   * @return 18 char sscc number or null
   */
  public String transformSscc(String sscc) {
    return Optional.ofNullable(sscc)
        .map(
            ogSscc -> {
              String trimmedSscc = ogSscc.substring(2);
              return trimmedSscc.length() == 18 ? trimmedSscc : ogSscc;
            })
        .orElse(null);
  }

  @Transactional
  @InjectTenantFilter
  public void persistInstruction(Instruction instruction) {
    instructionPersisterService.saveInstruction(instruction);
  }

  String getFullGln(HttpHeaders httpHeaders) {
    Map<String, String> glnDetails = new HashMap<>();
    glnDetails = gson.fromJson(appConfig.getGlnDetails(), glnDetails.getClass());
    return glnDetails.get(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM).toString());
  }

  public void constructAndOutBoxFixit(
      EpcisRequest epcisRequest,
      HttpHeaders httpHeaders,
      String fullGln,
      String trackingId,
      List<String> epcList) {
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();
    AtomicInteger lag = new AtomicInteger();
    Instant eventTime = Instant.now().plusSeconds(lag.addAndGet(lagInterval * 2));
    String tenantSGln = readFacilityFormattedGln(fullGln);
    String bizLocation = URN_SGLN.concat(tenantSGln);

    epcisRequest.setEventTime(eventTime.toString());
    epcisRequest.setRecordTime(eventTime.toString());
    epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
    epcisRequest.setIch(true);
    epcisRequest.setValidationPerformed(true);
    epcisRequest.setBizLocation(bizLocation);
    epcisRequest.setReadPoint(bizLocation);
    epcisRequest.setEpcList(epcList);

    rxCompleteInstructionOutboxHandler.outboxEpcisEvent(
        epcisRequest, httpHeaders, trackingId, eventTime);
  }
}
