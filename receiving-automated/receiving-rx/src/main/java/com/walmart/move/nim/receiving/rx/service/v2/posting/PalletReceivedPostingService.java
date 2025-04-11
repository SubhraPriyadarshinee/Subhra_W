package com.walmart.move.nim.receiving.rx.service.v2.posting;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static java.util.Collections.singletonList;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProcessingInfo;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.ProcessingInfoRepository;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.v2.data.EpcisPostingServiceHelper;
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
public class PalletReceivedPostingService extends EpcisService implements EpcisPostingService {

  @ManagedConfiguration RxManagedConfig rxManagedConfig;

  @Resource private Gson gson;
  @Resource private ProcessingInfoRepository processingInfoRepository;
  @Resource private EpcisPostingServiceHelper epcisPostingServiceHelper;
  @Resource private EpcisPostingService caseReceivedPostingService;

  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();

    EpcisPostingServiceHelper.EpcisPostingData epcisPostingData =
        epcisPostingServiceHelper.loadData(instruction, httpHeaders);
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    String fullGln = epcisPostingData.getFullGln();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    Content content = epcisPostingData.getContent();
    String systemContainerId = epcisPostingData.getSystemContainerId();
    String ssccOrSgtin = epcisPostingData.getSsccOrSgtin();
    SsccScanResponse.Container gdmRootContainer = epcisPostingData.getGdmRootContainer();
    String gdmRootContainerId = epcisPostingData.getGdmRootContainerId();
    String gdmCurrentContainerId = epcisPostingData.getGdmCurrentContainerId();
    ProcessingInfo processingInfoRecord = epcisPostingData.getProcessingInfo();

    // execute caseReceivedPosting if rootContainerId != currentContainerId
    if (!gdmCurrentContainerId.equalsIgnoreCase(gdmRootContainerId)) {
      log.info(
          "[ESR] FULL-PALLET scenario gdmRootContainerId != gdmCurrentContainerId, executing caseReceivedPosting...");
      caseReceivedPostingService.publishSerializedData(palletContainer, instruction, httpHeaders);
      return;
    }

    if (Objects.isNull(processingInfoRecord)) {
      log.info(
          "[ESR] Executing FULL-PALLET scenario for systemContainerId {}, instructionId {} trackingId {} ...",
          systemContainerId,
          instruction.getId(),
          palletContainer.getTrackingId());

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

      clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
      clubbedEpcisEvents.add(
          constructUnpackingAll(epcisRequest, ssccOrSgtin, lag.addAndGet(lagInterval)));

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
}
