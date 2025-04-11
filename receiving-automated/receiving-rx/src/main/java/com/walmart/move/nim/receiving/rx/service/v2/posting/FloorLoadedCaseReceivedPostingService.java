package com.walmart.move.nim.receiving.rx.service.v2.posting;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.CLUBBED_RECEIVING;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.CLUBBED_UNPACKING_ALL;

import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.EpcisService;
import com.walmart.move.nim.receiving.rx.service.v2.data.EpcisPostingServiceHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FloorLoadedCaseReceivedPostingService extends EpcisService
    implements EpcisPostingService {

  @ManagedConfiguration RxManagedConfig rxManagedConfig;

  @Resource private EpcisPostingServiceHelper epcisPostingServiceHelper;

  @Override
  public void publishSerializedData(
      Container palletContainer, Instruction instruction, HttpHeaders httpHeaders) {
    EpcisPostingServiceHelper.EpcisPostingData epcisPostingData =
        epcisPostingServiceHelper.loadData(instruction, httpHeaders);

    palletContainer
        .getChildContainers()
        .stream()
        .filter(
            x ->
                RxConstants.ReceivingContainerTypes.CASE.equalsIgnoreCase(
                    x.getRcvgContainerType())) // only CASE types
        .forEach(
            caseContainer ->
                processArriveReceiveUnpackingAll(
                    epcisPostingData, caseContainer, instruction, httpHeaders));
  }

  private void processArriveReceiveUnpackingAll(
      EpcisPostingServiceHelper.EpcisPostingData epcisPostingData,
      Container caseContainer,
      Instruction instruction,
      HttpHeaders httpHeaders) {
    LinkedHashSet<String> clubbedEventTypes = new LinkedHashSet<>();
    LinkedHashSet<EpcisRequest> clubbedEpcisEvents = new LinkedHashSet<>();
    EpcisRequest epcisRequest = epcisPostingData.getEpcisRequest();
    DeliveryDocumentLine deliveryDocumentLine = epcisPostingData.getDeliveryDocumentLine();
    String fullGln = epcisPostingData.getFullGln();
    Content content = epcisPostingData.getContent();
    AtomicInteger lag = new AtomicInteger();
    int lagInterval = rxManagedConfig.getAttpEventLagTimeInterval();

    // finalSsccOrSgtin
    Map<String, Object> caseContainerMiscInfo = caseContainer.getContainerMiscInfo();
    String topLevelContainerSscc = (String) caseContainerMiscInfo.get("topLevelContainerSscc");
    String caseContainerSerial = (String) caseContainerMiscInfo.get(ReceivingConstants.KEY_SERIAL);
    String caseContainerGtin = (String) caseContainerMiscInfo.get(ReceivingConstants.KEY_GTIN);
    String gdmRootContainerId = (String) caseContainerMiscInfo.get(RxConstants.TOP_LEVEL_CONTAINER_ID);
    String finalSsccOrSgtin;
    if (Objects.isNull(topLevelContainerSscc)) {
      finalSsccOrSgtin =
          appendEpcValue(
              appendEpcValue(
                  ReceivingConstants.EMPTY_STRING, ApplicationIdentifier.GTIN, caseContainerGtin),
              ApplicationIdentifier.SERIAL,
              caseContainerSerial);
    } else {
      finalSsccOrSgtin =
          appendEpcValue(
              ReceivingConstants.EMPTY_STRING,
              ApplicationIdentifier.SSCC,
              transformSscc(topLevelContainerSscc));
    }

    log.info(
        "[ESR] Executing FLOOR-LOADED-CASE scenario for topLevelContainer {}, instructionId {}, trackingId {} ...",
        finalSsccOrSgtin,
        instruction.getId(),
        caseContainer.getParentTrackingId());

    clubbedEventTypes.add(RxConstants.CLUBBED_ARRIVING);
    clubbedEpcisEvents.add(
        constructArriving(
            epcisRequest, deliveryDocumentLine, Collections.singletonList(finalSsccOrSgtin)));

    clubbedEventTypes.add(CLUBBED_RECEIVING);
    clubbedEpcisEvents.add(
        constructReceiving(
            epcisRequest,
            deliveryDocumentLine,
            content,
            instruction.getReceivedQuantity(),
            fullGln,
            Collections.singletonList(finalSsccOrSgtin),
            lag.addAndGet(lagInterval)));

    clubbedEventTypes.add(CLUBBED_UNPACKING_ALL);
    clubbedEpcisEvents.add(
        constructUnpackingAll(epcisRequest, finalSsccOrSgtin, lag.addAndGet(lagInterval)));

    epcisPostingServiceHelper.outboxEpcisEvents(
        clubbedEpcisEvents,
        String.join("-", clubbedEventTypes),
        httpHeaders,
        gdmRootContainerId,
        Instant.now());
  }
}
