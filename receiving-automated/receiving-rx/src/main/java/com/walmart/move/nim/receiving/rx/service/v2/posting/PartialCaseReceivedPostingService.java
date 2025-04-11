package com.walmart.move.nim.receiving.rx.service.v2.posting;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
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
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
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
public class PartialCaseReceivedPostingService extends EpcisService implements EpcisPostingService {

  @ManagedConfiguration RxManagedConfig rxManagedConfig;

  @Resource private Gson gson;
  @Resource private ProcessingInfoRepository processingInfoRepository;
  @Resource private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Resource private EpcisPostingServiceHelper epcisPostingServiceHelper;

  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();

    EpcisPostingServiceHelper.EpcisPostingData epcisPostingData =
        epcisPostingServiceHelper.loadData(instruction, httpHeaders);

    processArrivingUnpackingAll(
        epcisPostingData, palletContainer, instruction, httpHeaders, lag.addAndGet(lagInterval));

    processReceiving(
        epcisPostingData, palletContainer, instruction, httpHeaders, lag.addAndGet(lagInterval));
  }

  private void processArrivingUnpackingAll(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Container palletContainer,
      Instruction instruction,
      HttpHeaders httpHeaders,
      int lag) {
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();
    ProcessingInfo processingInfoRecord = epcisPostingData.getProcessingInfo();
    String systemContainerId = epcisPostingData.getSystemContainerId();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    String ssccOrSgtin = epcisPostingData.getSsccOrSgtin();
    SsccScanResponse.Container gdmRootContainer = epcisPostingData.getGdmRootContainer();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();

    if (Objects.isNull(processingInfoRecord)) {
      log.info(
          "[ESR] Executing PARTIAL-CASE scenario for systemContainerId {}, instructionId {}, trackingId {} ...",
          systemContainerId,
          instruction.getId(),
          palletContainer.getTrackingId());

      clubbedEventTypes.add(CLUBBED_ARRIVING);
      clubbedEpcisEvents.add(
          constructArriving(epcisRequest, deliveryDocumentLine, singletonList(ssccOrSgtin)));

      clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
      clubbedEpcisEvents.add(constructUnpackingAll(epcisRequest, ssccOrSgtin, lag));

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
  }

  private void processReceiving(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Container palletContainer,
      Instruction instruction,
      HttpHeaders httpHeaders,
      int lag) {
    List<String> epcList = new ArrayList<>();
    String systemContainerId = epcisPostingData.getSystemContainerId();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    Content content = epcisPostingData.getContent();
    String fullGln = epcisPostingData.getFullGln();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();

    palletContainer
        .getChildContainers()
        .forEach(
            caseContainer -> {
              String gdmContainerId =
                  (String) caseContainer.getContainerMiscInfo().get(GDM_CONTAINER_ID);
              Container unitContainer =
                  rxCompleteInstructionOutboxHandler.getContainerWithChildsByTrackingId(
                      caseContainer.getTrackingId());
              unitContainer
                  .getChildContainers()
                  .forEach(
                      unit -> {
                        Map<String, Object> unitContainerMiscInfo = unit.getContainerMiscInfo();
                        String gtin = (String) unitContainerMiscInfo.get(KEY_GTIN);
                        String serial = (String) unitContainerMiscInfo.get(KEY_SERIAL);
                        String sgtin =
                            appendEpcValue(
                                appendEpcValue(
                                    ReceivingConstants.EMPTY_STRING,
                                    ApplicationIdentifier.GTIN,
                                    gtin),
                                ApplicationIdentifier.SERIAL,
                                serial);
                        epcList.add(sgtin);
                      });

              log.info(
                  "[ESR] Executing PARTIAL-CASE scenario for systemContainerId {}, instructionId {}, trackingId {} epcListSize {} ...",
                  systemContainerId,
                  instruction.getId(),
                  palletContainer.getTrackingId(),
                  epcList.size());

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
                  gdmContainerId + instruction.getId(),
                  Instant.now());

              // reset epcList
              epcList.clear();
            });
  }
}
