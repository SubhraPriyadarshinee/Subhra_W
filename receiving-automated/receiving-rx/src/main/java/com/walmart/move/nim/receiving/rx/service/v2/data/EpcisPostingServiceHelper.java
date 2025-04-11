package com.walmart.move.nim.receiving.rx.service.v2.data;

import static com.walmart.move.nim.receiving.rx.common.RxUtils.buildOutboxEvent;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.WM_WORKFLOW_DSD;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProcessingInfo;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EpcisPostingServiceHelper extends EpcisService {

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private OutboxConfig outboxConfig;

  @Resource private Gson gson;
  @Resource private ProcessingInfoRepository processingInfoRepository;
  @Resource private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Resource private OutboxEventSinkService outboxEventSinkService;

  public EpcisPostingData loadData(Instruction instruction, HttpHeaders httpHeaders) {
    // gln computation
    Map<String, String> glnDetails = gson.fromJson(appConfig.getGlnDetails(), HashMap.class);
    String fullGln = glnDetails.get(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    String tenantSGln = readFacilityFormattedGln(fullGln);
    String bizLocation = URN_SGLN.concat(tenantSGln);

    // epcis
    EpcisRequest epcisRequest = new EpcisRequest();
    epcisRequest.setIch(true);
    epcisRequest.setValidationPerformed(true);
    epcisRequest.setEventTimeZoneOffset(GMT_OFFSET);
    epcisRequest.setReadPoint(bizLocation);
    epcisRequest.setBizLocation(bizLocation);

    // getters
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Content content = instruction.getChildContainers().get(0).getContents().get(0);

    DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
        deliveryDocument.getGdmCurrentNodeDetail();

    // gdmRootContainer
    SsccScanResponse.Container gdmRootContainer =
        gdmCurrentNodeDetail.getAdditionalInfo().getContainers().get(0);
    String gdmRootContainerId = gdmRootContainer.getId();
    String gdmRootContainerSscc = gdmRootContainer.getSscc();
    String gdmRootContainerSerial = gdmRootContainer.getSerial();
    String gdmRootContainerGtin = gdmRootContainer.getGtin();

    log.info(
        "[ESR] gdmRootContainerId {}, gdmRootContainerSscc {}, gdmRootContainerSerial {}, gdmRootContainerGtin {}",
        gdmRootContainerId,
        gdmRootContainerSscc,
        gdmRootContainerSerial,
        gdmRootContainerGtin);

    // gdmCurrentContainer
    SsccScanResponse.Container gdmCurrentContainer = gdmCurrentNodeDetail.getContainers().get(0);
    String gdmCurrentContainerId = gdmCurrentContainer.getId();
    String gdmCurrentContainerSscc = gdmCurrentContainer.getSscc();
    String gdmCurrentContainerSerial = gdmCurrentContainer.getSerial();
    String gdmCurrentContainerGtin = gdmCurrentContainer.getGtin();

    log.info(
        "[ESR] gdmCurrentContainerId {}, gdmCurrentContainerSscc {}, gdmRootContainerSerial {}, gdmRootContainerGtin {}",
        gdmCurrentContainerId,
        gdmCurrentContainerSscc,
        gdmCurrentContainerSerial,
        gdmCurrentContainerGtin);

    // systemContainerId
    String systemContainerId =
        StringUtils.isBlank(gdmRootContainerId) ? gdmCurrentContainerId : gdmRootContainerId;

    log.info("[ESR] Querying processing_info with systemContainerId {}", systemContainerId);

    // val
    String ssccOrSgtin = null;
    if (StringUtils.isNotBlank(gdmRootContainerSscc)) {
      ssccOrSgtin =
          appendEpcValue(
              ReceivingConstants.EMPTY_STRING,
              ApplicationIdentifier.SSCC,
              transformSscc(gdmRootContainerSscc));
    } else if (StringUtils.isNotBlank(gdmRootContainerGtin)
        && StringUtils.isNotBlank(gdmRootContainerSerial)) {
      ssccOrSgtin =
          appendEpcValue(
              appendEpcValue(
                  ReceivingConstants.EMPTY_STRING,
                  ApplicationIdentifier.GTIN,
                  gdmRootContainerGtin),
              ApplicationIdentifier.SERIAL,
              gdmRootContainerSerial);
    } else if (StringUtils.isNotBlank(gdmCurrentContainerSscc)) {
      ssccOrSgtin =
          appendEpcValue(
              ReceivingConstants.EMPTY_STRING,
              ApplicationIdentifier.SSCC,
              transformSscc(gdmCurrentContainerSscc));
    } else if (StringUtils.isNotBlank(gdmCurrentContainerGtin)
        && StringUtils.isNotBlank(gdmCurrentContainerSerial)) {
      ssccOrSgtin =
          appendEpcValue(
              appendEpcValue(
                  ReceivingConstants.EMPTY_STRING,
                  ApplicationIdentifier.GTIN,
                  gdmCurrentContainerGtin),
              ApplicationIdentifier.SERIAL,
              gdmCurrentContainerSerial);
    }

    log.info("[ESR] Finally computed ssccOrSgtin {}", ssccOrSgtin);

    ProcessingInfo processingInfoRecord =
        processingInfoRepository.findBySystemContainerId(systemContainerId);

    return EpcisPostingData.builder()
        .epcisRequest(epcisRequest)
        .deliveryDocument(deliveryDocument)
        .deliveryDocumentLine(deliveryDocumentLine)
        .content(content)
        .gdmRootContainer(gdmRootContainer)
        .gdmRootContainerId(gdmRootContainerId)
        .gdmCurrentContainerId(gdmCurrentContainerId)
        .processingInfo(processingInfoRecord)
        .systemContainerId(systemContainerId)
        .ssccOrSgtin(ssccOrSgtin)
        .fullGln(fullGln)
        .bizLocation(bizLocation)
        .build();
  }

  public void outboxEpcisEvent(
      EpcisRequest epcisRequest,
      HttpHeaders httpHeaders,
      String gdmRootContainerId,
      String correlationIdData,
      Instant executionTs) {
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    String eventId = countryCode + "_" + facilityNum + "_" + gdmRootContainerId + "_PS";
    String correlationId = countryCode + "_" + facilityNum + "_" + correlationIdData;
    String epcisJson = gson.toJson(epcisRequest);
    Map<String, Object> headers = constructEpcisHeaders(correlationId, httpHeaders, facilityNum);

    OutboxEvent outboxEvent =
        buildOutboxEvent(
            headers,
            epcisJson,
            eventId,
            MetaData.emptyInstance(),
            outboxConfig.getOutboxPolicyHttpPsV1Serialize(),
            executionTs);

    log.info(
        "ATTP eventId:{} | correlationId:{} | eventJson: {}", eventId, correlationId, epcisJson);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  public void outboxEpcisEvents(
      Set<EpcisRequest> epcisRequests,
      String clubbed,
      HttpHeaders httpHeaders,
      String gdmRootContainerId,
      Instant executionTs) {
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    String eventId = countryCode + "_" + facilityNum + "_" + gdmRootContainerId + "_PS";
    String correlationId = countryCode + "_" + facilityNum + "_" + gdmRootContainerId;
    String epcisJson = gson.toJson(epcisRequests);
    Map<String, Object> headers = constructEpcisHeaders(correlationId, httpHeaders, facilityNum);

    OutboxEvent outboxEvent =
        buildOutboxEvent(
            headers,
            epcisJson,
            eventId,
            MetaData.with("clubbed", clubbed),
            outboxConfig.getOutboxPolicyHttpPsV1SerializeClubbed(),
            executionTs);

    log.info(
        "CLUBBED ATTP eventId:{} | clubbed:{} | correlationId:{} | eventJson: {}",
        eventId,
        clubbed,
        correlationId,
        epcisJson);
    outboxEventSinkService.saveEvent(outboxEvent);
  }

  public Map<String, Object> constructEpcisHeaders(
      String correlationId, HttpHeaders httpHeaders, int facilityNum) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ACCEPT, APPLICATION_JSON_VALUE);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    headers.put(WM_CORRELATIONID, correlationId);
    headers.put(WM_USERID, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    headers.put(WM_SITEID, valueOf(facilityNum));
    headers.put(WM_CLIENT, WM_CLIENT_DC);
    headers.put(WM_WORKFLOW, WM_WORKFLOW_DSD);
    return headers;
  }

  @Data
  @Builder
  public static class EpcisPostingData {
    private EpcisRequest epcisRequest;
    private DeliveryDocument deliveryDocument;
    private DeliveryDocumentLine deliveryDocumentLine;
    private Content content;
    private SsccScanResponse.Container gdmRootContainer;
    private String gdmRootContainerId;
    private String gdmCurrentContainerId;
    private ProcessingInfo processingInfo;
    private String systemContainerId;
    private String ssccOrSgtin;
    private String fullGln;
    private String bizLocation;
  }
}
