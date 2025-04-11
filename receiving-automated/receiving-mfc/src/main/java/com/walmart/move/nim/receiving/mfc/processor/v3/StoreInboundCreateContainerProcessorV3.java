package com.walmart.move.nim.receiving.mfc.processor.v3;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PALLET_RELATIONS;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.processor.v2.StoreInboundCreateContainerProcessorV2;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.service.MFCProblemService;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreInboundCreateContainerProcessorV3 extends StoreInboundCreateContainerProcessorV2 {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreInboundCreateContainerProcessorV3.class);

  @Resource(name = MFCConstant.MFC_PROBLEM_SERVICE)
  private MFCProblemService problemService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private ProcessInitiator processInitiator;
  @Autowired private MFCDeliveryService deliveryService;
  @Autowired private ContainerService containerService;
  @Autowired private ContainerPersisterService containerPersisterService;

  @Override
  public String createContainer(ContainerScanRequest containerScanRequest) {

    ASNDocument asnDocument = getAsnDocument(containerScanRequest, true);
    containerScanRequest.setAsnDocument(asnDocument);

    //    Check for parent/child relation trackingIds
    List<String> trackingIdList = getTrackingIdList(containerScanRequest);

    List<Pack> packs = asnDocument.getPacks();
    String result = new String();

    if (!CollectionUtils.isEmpty(trackingIdList)) {
      List<Container> existingContainers =
          containerPersisterService.findAllBySSCCIn(trackingIdList);

      List<String> trackingIdsToProcess = new ArrayList<>(trackingIdList);

      if (!existingContainers.isEmpty()) {
        for (Container container : existingContainers) {
          String trackingId = container.getSsccNumber();
          if (trackingIdsToProcess.contains(trackingId)) {
            trackingIdsToProcess.remove(trackingId);
          }
        }
      }

      if (!trackingIdsToProcess.isEmpty()) {
        for (String trackingId : trackingIdsToProcess) {
          containerScanRequest.setTrackingId(trackingId);
          asnDocument.setPacks(packs);
          result = processContainer(containerScanRequest, asnDocument);
        }
      }
    }
    return result;
  }

  private List<String> getTrackingIdList(ContainerScanRequest containerScanRequest) {

    List<String> trackingIdList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(containerScanRequest.getAsnDocument().getPacks())
        && Objects.nonNull(
            containerScanRequest.getAsnDocument().getPacks().get(0).getAdditionalInfo())) {
      List<Pack> packs =
          containerScanRequest
              .getAsnDocument()
              .getPacks()
              .stream()
              .filter(
                  pack ->
                      StringUtils.equalsIgnoreCase(
                          pack.getPalletNumber(), containerScanRequest.getTrackingId()))
              .collect(Collectors.toList());

      if (Objects.nonNull(packs.get(0).getAdditionalInfo())
          && packs.get(0).getAdditionalInfo().containsKey(PALLET_RELATIONS)) {
        List<String> palletRelations =
            (List<String>) packs.get(0).getAdditionalInfo().get(PALLET_RELATIONS);

        trackingIdList.addAll(palletRelations);
        trackingIdList.add(containerScanRequest.getTrackingId());
      }
    }
    return trackingIdList;
  }
}
