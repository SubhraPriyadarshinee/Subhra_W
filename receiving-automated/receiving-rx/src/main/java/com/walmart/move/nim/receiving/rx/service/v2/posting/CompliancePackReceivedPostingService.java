package com.walmart.move.nim.receiving.rx.service.v2.posting;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static java.util.Collections.singletonList;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProcessingInfo;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.v2.data.EpcisPostingServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompliancePackReceivedPostingService extends EpcisService
    implements EpcisPostingService {

  @ManagedConfiguration RxManagedConfig rxManagedConfig;

  @Resource private Gson gson;
  @Resource private ProcessingInfoRepository processingInfoRepository;
  @Resource private EpcisPostingServiceHelper epcisPostingServiceHelper;

  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();

    EpcisPostingServiceHelper.EpcisPostingData epcisPostingData =
        epcisPostingServiceHelper.loadData(instruction, httpHeaders);
    String systemContainerId = epcisPostingData.getSystemContainerId();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();
    String gdmCurrentContainerId = epcisPostingData.getGdmCurrentContainerId();

    log.info(
        "[ESR] Executing HANDLE-AS-CASEPACK scenario for systemContainerId {}, instructionId {} trackingId {} ...",
        systemContainerId,
        instruction.getId(),
        palletContainer.getTrackingId());

    if (gdmRootContainerId.equalsIgnoreCase(gdmCurrentContainerId)) {
      processArrivingReceivingUnpackingCase(epcisPostingData, instruction, httpHeaders);
    } else {
      processArrivingUnpackingCase(
          epcisPostingData, instruction, httpHeaders, lag.addAndGet(lagInterval));
      processReceiving(
          epcisPostingData, palletContainer, instruction, httpHeaders, lag.addAndGet(lagInterval));
    }
  }

  private void processArrivingReceivingUnpackingCase(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Instruction instruction,
      HttpHeaders httpHeaders) {
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    String ssccOrSgtin = epcisPostingData.getSsccOrSgtin();
    Content content = epcisPostingData.getContent();
    String fullGln = epcisPostingData.getFullGln();
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();

    clubbedEventTypes.add(CLUBBED_ARRIVING);
    clubbedEpcisEvents.add(
        constructArriving(epcisRequest, deliveryDocumentLine, singletonList(ssccOrSgtin)));

    clubbedEventTypes.add(CLUBBED_RECEIVING);
    clubbedEpcisEvents.add(
        constructReceiving(
            epcisRequest,
            deliveryDocumentLine,
            content,
            instruction.getReceivedQuantity(),
            fullGln,
            singletonList(ssccOrSgtin),
            lag.addAndGet(lagInterval)));

    processUnpackingCase(
        epcisPostingData,
        instruction,
        clubbedEventTypes,
        clubbedEpcisEvents,
        httpHeaders,
        lag.addAndGet(lagInterval));
  }

  private void processArrivingUnpackingCase(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Instruction instruction,
      HttpHeaders httpHeaders,
      int lag) {
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();
    ProcessingInfo processingInfoRecord = epcisPostingData.getProcessingInfo();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    String ssccOrSgtin = epcisPostingData.getSsccOrSgtin();

    if (Objects.isNull(processingInfoRecord)) {

      clubbedEventTypes.add(CLUBBED_ARRIVING);
      clubbedEpcisEvents.add(
          constructArriving(epcisRequest, deliveryDocumentLine, singletonList(ssccOrSgtin)));

      processUnpackingCase(
          epcisPostingData, instruction, clubbedEventTypes, clubbedEpcisEvents, httpHeaders, lag);
    }
  }

  private void processUnpackingCase(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Instruction instruction,
      LinkedHashSet<String> clubbedEventTypes,
      LinkedHashSet<EpcisRequest> clubbedEpcisEvents,
      HttpHeaders httpHeaders,
      int lag) {
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    String ssccOrSgtin = epcisPostingData.getSsccOrSgtin();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();
    SsccScanResponse.Container gdmRootContainer = epcisPostingData.getGdmRootContainer();

    clubbedEventTypes.add(CLUBBED_UNPACKING_CASE);
    clubbedEpcisEvents.add(constructUnpackingCase(epcisRequest, ssccOrSgtin, lag));

    ProcessingInfo processingInfo = new ProcessingInfo();
    processingInfo.setSystemContainerId(gdmRootContainerId);
    processingInfo.setReferenceInfo(gson.toJson(gdmRootContainer));
    processingInfo.setInstructionId(instruction.getId());
    processingInfo.setCreateUserId(instruction.getCreateUserId());
    processingInfo.setStatus(String.join("-", clubbedEventTypes));
    processingInfoRepository.save(processingInfo);

    epcisPostingServiceHelper.outboxEpcisEvents(
        clubbedEpcisEvents,
        String.join("-", clubbedEventTypes),
        httpHeaders,
        gdmRootContainerId,
        Instant.now());
  }

  private void processReceiving(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Container palletContainer,
      Instruction instruction,
      HttpHeaders httpHeaders,
      int lag) {
    List<String> epcList = new ArrayList<>();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    Content content = epcisPostingData.getContent();
    String fullGln = epcisPostingData.getFullGln();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();
    String gdmPalletContainerId =
        (String) palletContainer.getContainerMiscInfo().get(GDM_CONTAINER_ID);

    palletContainer
        .getChildContainers()
        .forEach(
            caseContainer -> {
              Map<String, Object> caseContainerMiscInfo = caseContainer.getContainerMiscInfo();
              String serial = (String) caseContainerMiscInfo.get(ReceivingConstants.KEY_SERIAL);
              String gtin = (String) caseContainerMiscInfo.get(ReceivingConstants.KEY_GTIN);
              String sgtin =
                  appendEpcValue(
                      appendEpcValue(
                          ReceivingConstants.EMPTY_STRING, ApplicationIdentifier.GTIN, gtin),
                      ApplicationIdentifier.SERIAL,
                      serial);
              epcList.add(sgtin);
            });

    EpcisRequest epcisReceivingRequest =
        constructReceiving(
            epcisRequest,
            deliveryDocumentLine,
            content,
            instruction.getReceivedQuantity(),
            fullGln,
            epcList,
            lag);

    epcisPostingServiceHelper.outboxEpcisEvent(
        epcisReceivingRequest,
        httpHeaders,
        gdmRootContainerId,
        gdmPalletContainerId,
        Instant.now());
  }
}
