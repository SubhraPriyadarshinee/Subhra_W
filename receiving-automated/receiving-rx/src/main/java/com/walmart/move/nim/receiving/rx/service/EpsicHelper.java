package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_UPDATE_STATUS_URL;
import static org.springframework.http.HttpStatus.OK;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisReceiveIdentity;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.PackData;
import com.walmart.move.nim.receiving.core.message.common.PackItemData;
import com.walmart.move.nim.receiving.core.message.common.ShipmentInfo;
import com.walmart.move.nim.receiving.core.message.common.ShipmentRequest;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.rx.model.ExceptionInfo;
import com.walmart.move.nim.receiving.rx.model.FixitAttpRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.logging.commons.lang3.StringUtils;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Service
public class EpsicHelper {
  private static final Logger log = LoggerFactory.getLogger(EpsicHelper.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private Gson gson;
  @Autowired private RestUtils restUtils;

  @Resource RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Resource EpcisService epcisService;

  @Transactional
  @InjectTenantFilter
  public void publishFixitEventsToAttp(
      @Valid @RequestBody FixitAttpRequest fixitAttpRequest, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ShipmentInfo shipmentInfo = constructGDMRequestForCasesAndEaches(fixitAttpRequest, httpHeaders);
    callGdmToUpdatePackStatus(shipmentInfo, httpHeaders);
    constructAndOutBoxDamage(fixitAttpRequest, httpHeaders);
  }

  protected void constructAndOutBoxDamage(
      FixitAttpRequest fixitAttpRequest, HttpHeaders httpHeaders) {

    String fullGln = epcisService.getFullGln(httpHeaders);
    List<String> arrivingEpcList = new ArrayList<>();
    for (ManufactureDetail scannedCase : fixitAttpRequest.getScannedDataList()) {
      epcisService.childSsccOrSgtin(arrivingEpcList, scannedCase, false);
    }

    List<String> epcList = new ArrayList<>();
    for (ManufactureDetail scannedCase : fixitAttpRequest.getScannedDataList()) {
      epcisService.childSsccOrSgtin(epcList, scannedCase, true);
    }

    // Builds a unique tracking ID for outbox based on first scanned epc
    String trackingId = String.join("_", epcList.get(0), FIXIT);

    EpcisRequest arriveEpcisRequest = arriveEpcisRequest(fullGln);
    EpcisRequest arrivingEpcisRequest =
        epcisService.constructArriving(
            arriveEpcisRequest,
            String.valueOf(fixitAttpRequest.getExceptionInfo().getVendorNbrDeptSeq()),
            arrivingEpcList);
    rxCompleteInstructionOutboxHandler.outboxEpcisEvent(
        arrivingEpcisRequest, httpHeaders, trackingId, Instant.now());

    arriveEpcisRequest.setEpcList(null);
    Set<String> scannedCases =
        fixitAttpRequest
            .getScannedDataList()
            .stream()
            .filter(scannedCase -> StringUtils.isNotEmpty(scannedCase.getSscc()))
            .map(scannedCase -> scannedCase.getSscc())
            .collect(Collectors.toSet());

    for (String sscc : scannedCases) {
      trackingId = publishUnpackCase(httpHeaders, trackingId, arriveEpcisRequest, sscc);
    }

    EpcisRequest epcisRequest = new EpcisRequest();
    setDispositionBasedFeilds(epcisRequest, fixitAttpRequest.getExceptionInfo(), fullGln);
    epcisService.constructAndOutBoxFixit(epcisRequest, httpHeaders, fullGln, trackingId, epcList);
  }

  private String publishUnpackCase(
      HttpHeaders httpHeaders, String trackingId, EpcisRequest arriveEpcisRequest, String sscc) {
    String palletOfCase;
    if (StringUtils.isNotBlank(sscc)) {
      palletOfCase =
          epcisService.appendEpcValue(
              ReceivingConstants.EMPTY_STRING,
              ApplicationIdentifier.SSCC,
              epcisService.transformSscc(sscc));
      trackingId = String.join("_", palletOfCase, FIXIT);
      EpcisRequest unpackingCaseEpcisRequest =
          epcisService.constructUnpackingCase(arriveEpcisRequest, palletOfCase);
      rxCompleteInstructionOutboxHandler.outboxEpcisEvent(
          unpackingCaseEpcisRequest, httpHeaders, trackingId, Instant.now());
    }
    return trackingId;
  }

  private EpcisRequest arriveEpcisRequest(String fullGln) {
    EpcisRequest epcisRequest = new EpcisRequest();
    String tenantSGln = epcisService.readFacilityFormattedGln(fullGln);
    String bizLocation = URN_SGLN.concat(tenantSGln);

    epcisRequest.setIch(true);
    epcisRequest.setValidationPerformed(true);
    epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
    epcisRequest.setReadPoint(bizLocation);
    epcisRequest.setBizLocation(bizLocation);
    return epcisRequest;
  }

  private void setDispositionBasedFeilds(
      EpcisRequest epcisRequest, ExceptionInfo exceptionInfo, String fullGln) {

    switch (exceptionInfo.getDisposition()) {
      case DISPOSITION_DESTROY:
        epcisRequest.setAction(EPCIS_ACTION_DELETE);
        epcisRequest.setBizStep(URN_BIZSTEP_DECOMMISSION);
        epcisRequest.setDisposition(URN_DISP_INACTIVE);
        epcisRequest.setReasonCode(exceptionInfo.getReasonCode());
        break;

      case DISPOSITION_RETURN_TO_VENDOR:
        epcisRequest.setAction(EPCIS_ACTION_OBSERVE);
        epcisRequest.setBizStep(URN_BIZSTEP_SHIPPING_RETURN);
        epcisRequest.setDisposition(URN_DISP_RETURNED);
        epcisRequest.setReasonCode(exceptionInfo.getReasonCode());
        epcisRequest.setBizTransactionList(transactionList(exceptionInfo, fullGln));
        epcisRequest.setSourceList(
            epcisService.glnList(
                URN_OWNING_PARTY, URN_SGLN.concat(epcisService.readFacilityFormattedGln(fullGln))));
        epcisRequest.setDestinationList(
            epcisService.glnList(
                URN_OWNING_PARTY_ID, String.valueOf(exceptionInfo.getVendorNbrDeptSeq())));
        epcisRequest.setReturnType(EPCIS_RETUNR_TYPE_PREREC);
        break;
    }
  }

  private List<EpcisReceiveIdentity> transactionList(ExceptionInfo exceptionInfo, String fullGln) {
    List<EpcisReceiveIdentity> identities = new ArrayList<>();
    epcisService.constructReceiveIdentity(
        identities, URN_PO, URN_PREPEND + fullGln + COLON + exceptionInfo.getPurchaseReference());

    if (StringUtils.isNotBlank(exceptionInfo.getWmra())) {
      epcisService.constructReceiveIdentity(
          identities, URN_DESADV, URN_PREPEND + fullGln + COLON + exceptionInfo.getWmra());
    }
    return identities;
  }

  protected ShipmentInfo constructGDMRequestForCasesAndEaches(
      FixitAttpRequest fixitAttpRequest, HttpHeaders httpHeaders) throws ReceivingException {
    String caseEachesReceivingStatus = getReceivingStatus(fixitAttpRequest.getExceptionInfo());
    List<ManufactureDetail> scannedCases = fixitAttpRequest.getScannedDataList();

    ManufactureDetail primaryScannedCase = scannedCases.get(0);
    List<PackItemData> packItemDataList = new ArrayList<>();
    for (ManufactureDetail eachesContainer : scannedCases) {
      if (Uom.EACHES.equals(eachesContainer.getReportedUom())) {
        packItemDataList.add(
            PackItemData.builder()
                .documentPackId(eachesContainer.getDocumentPackId())
                .gtin(eachesContainer.getGtin())
                .serial(eachesContainer.getSerial())
                .receivingStatus(caseEachesReceivingStatus)
                .build());
      }
    }

    String documentId = primaryScannedCase.getDocumentId();

    PackData.PackDataBuilder packData =
        PackData.builder()
            .documentId(documentId)
            .documentPackId(primaryScannedCase.getDocumentPackId());

    if (Uom.CA.equals(primaryScannedCase.getReportedUom())) {
      packData.receivingStatus(caseEachesReceivingStatus);
    }
    if (CollectionUtils.isNotEmpty(packItemDataList)) {
      packData.items(packItemDataList);
    }

    Set<PackData> packDataList = new HashSet<>();
    packDataList.add(packData.build());

    return ShipmentInfo.builder()
        .documentId(documentId)
        .shipmentNumber(primaryScannedCase.getShipmentNumber())
        .documentType(ReceivingConstants.EPCIS)
        .packs(packDataList)
        .build();
  }

  protected String getReceivingStatus(ExceptionInfo exceptionInfo) {
    switch (exceptionInfo.getDisposition()) {
      case DISPOSITION_RETURN_TO_VENDOR:
        return ReceivingConstants.RETURNED_TO_VENDOR_STATUS;
      default:
        return ReceivingConstants.DECOMISSIONED_STATUS;
    }
  }

  protected void callGdmToUpdatePackStatus(ShipmentInfo shipmentInfo, HttpHeaders httpHeaders)
      throws ReceivingException {
    String request;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    ResponseEntity<String> response;
    String url = appConfig.getGdmBaseUrl() + GDM_UPDATE_STATUS_URL;
    request = gson.toJson(ShipmentRequest.builder().shipment(shipmentInfo).build());
    log.info("callGdmToUpdatePackStatus: For  Request={}, Headers={}", request, httpHeaders);
    response = restUtils.put(url, httpHeaders, new HashMap<>(), request);
    if (OK != response.getStatusCode()) {
      log.error(
          "Error calling GDM update status API: url={}  Request={}, response={}, Headers={}",
          url,
          request,
          response,
          httpHeaders);
      throw new ReceivingException(
          GDM_UPDATE_STATUS_API_ERROR,
          response.getStatusCode(),
          GDM_UPDATE_STATUS_API_ERROR_CODE,
          GDM_UPDATE_STATUS_API_ERROR_HEADER);
    }
  }

  public void validateRequest(FixitAttpRequest fixitAttpRequest) throws ReceivingException {
    validateExecptionInfo(fixitAttpRequest.getExceptionInfo());
    validateScannedInfo(fixitAttpRequest);
  }

  private static void validateExecptionInfo(ExceptionInfo exceptionInfo) throws ReceivingException {
    if (Objects.isNull(exceptionInfo)) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_EXCEPTIONINFO_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }

    if (StringUtils.isBlank(exceptionInfo.getDisposition())) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_DIPOSITION_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }

    if (StringUtils.isBlank(exceptionInfo.getVendorNbrDeptSeq())) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_VENDOR_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }

    if (DISPOSITION_RETURN_TO_VENDOR.equals(exceptionInfo.getDisposition())) {
      if (StringUtils.isBlank(exceptionInfo.getPurchaseReference())) {
        throw new ReceivingException(
            ReceivingException.FIXIT_EVENT_PO_MISSING,
            HttpStatus.BAD_REQUEST,
            ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
      }
    }
  }

  private static void validateScannedInfo(FixitAttpRequest fixitAttpRequest)
      throws ReceivingException {
    List<ManufactureDetail> scannedCases = fixitAttpRequest.getScannedDataList();
    if (CollectionUtils.isEmpty(scannedCases)) {
      throw new ReceivingException(
          ReceivingException.NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }
    ManufactureDetail primaryScannedCase = scannedCases.get(0);
    if ((StringUtils.isAllBlank(primaryScannedCase.getGtin())
            || StringUtils.isAllBlank(primaryScannedCase.getSerial()))
        && StringUtils.isAllBlank(primaryScannedCase.getSscc())) {
      throw new ReceivingException(
          ReceivingException.NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }

    if (StringUtils.isAllBlank(primaryScannedCase.getReportedUom())) {
      throw new ReceivingException(
          ReceivingException.UOM_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }

    if (StringUtils.isAllBlank(primaryScannedCase.getShipmentNumber())) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_SHIPMENT_NUMBER_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }
    if (StringUtils.isAllBlank(primaryScannedCase.getDocumentId())) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_DOCUMENT_ID_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }
    if (StringUtils.isAllBlank(primaryScannedCase.getDocumentPackId())) {
      throw new ReceivingException(
          ReceivingException.FIXIT_EVENT_DOCUMENT_PACK_ID_MISSING,
          HttpStatus.BAD_REQUEST,
          ExceptionCodes.BAD_REQUEST_FOR_FIXIT_EVENT);
    }
  }
}
