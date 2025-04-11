package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getTimeDifferenceInMillis;

import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** This class has all methods required for Offline Receiving flow. Author: s0g0g7u */
@Service
public class RdcOfflineRecieveUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcReceivingUtils.class);
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Autowired private JMSSorterPublisher jmsSorterPublisher;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  /**
   * @param receivedContainers
   * @param httpHeaders
   * @param instruction
   * @param deliveryDocumentMap
   * @return
   * @throws ReceivingException
   */
  public Collection<OutboxEvent> buildOutboxEventsForOffline(
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders,
      Instruction instruction,
      Map<String, DeliveryDocument> deliveryDocumentMap,
      List<Container> consolidatedContainerList)
      throws ReceivingException {
    LOGGER.info(
        "Executing outbox events for Offline flow for delivery Nbr : {} : ",
        receivedContainers.get(0).getDeliveryNumber());
    Collection<OutboxEvent> outboxEvents = null;
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    for (ReceivedContainer receivedContainer : receivedContainers) {
      if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
        DeliveryDocument deliveryDocument =
            deliveryDocumentMap.get(receivedContainer.getLabelTrackingId());

        Map<String, Container> consolidatedContainerMap =
            consolidatedContainerList
                .stream()
                .collect(Collectors.toMap(Container::getTrackingId, Function.identity()));
        Container consolidatedContainer = null;
        /*
        Checking if the feature flag is true and the containerMap contains the tracking ID - which should be ideally be present.
        If feature flag is true, fetching the consolidated container from the map instead of fetching from Persistence layer.
         */
        if (rdcManagedConfig.getEnableSingleTransactionForOffline()
            && (rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer().isEmpty()
                || rdcManagedConfig
                    .getDcListEligibleForPrepareConsolidatedContainer()
                    .contains(
                        Objects.nonNull(
                                consolidatedContainerList
                                    .get(0)
                                    .getContainerMiscInfo()
                                    .get(RdcConstants.ORIGIN_FACILITY_NUM))
                            ? String.valueOf(
                                consolidatedContainerList
                                    .get(0)
                                    .getContainerMiscInfo()
                                    .get(RdcConstants.ORIGIN_FACILITY_NUM))
                            : StringUtils.EMPTY))
            && consolidatedContainerMap.containsKey(receivedContainer.getLabelTrackingId())) {
          consolidatedContainer =
              consolidatedContainerMap.get(receivedContainer.getLabelTrackingId());
          LOGGER.info(
              "Consolidate Container already prepared for tracking ID: {} and Delivery Number: {}",
              receivedContainer.getLabelTrackingId(),
              consolidatedContainer.getDeliveryNumber());
        } else {
          consolidatedContainer =
              containerPersisterService.getConsolidatedContainerForPublish(
                  receivedContainer.getLabelTrackingId());
        }
        rdcReceivingUtils.buildOutboxEventForInventory(
            consolidatedContainer, outboxPolicyMap, httpHeaders);
        rdcReceivingUtils.buildOutboxEventForPutawayRequest(
            consolidatedContainer,
            receivedContainer,
            instruction,
            deliveryDocument,
            outboxPolicyMap,
            httpHeaders);
        rdcReceivingUtils.buildOutboxEventForSorterDivert(
            consolidatedContainer,
            receivedContainer,
            outboxPolicyMap,
            httpHeaders,
            deliveryDocument);
        rdcReceivingUtils.buildOutboxPolicyForEI(
            consolidatedContainer, outboxPolicyMap, httpHeaders);
        outboxEvents =
            rdcReceivingUtils.buildOutboxEvent(
                consolidatedContainer.getTrackingId(), outboxPolicyMap);
      }
    }
    LOGGER.info(
        "Executed outbox events for Offline flow for delivery Nbr : {} : ",
        receivedContainers.get(0).getDeliveryNumber());
    return outboxEvents;
  }

  /**
   * post receiving updates for offline receiving
   *
   * @param instruction
   * @param deliveryDocumentMap
   * @param httpHeaders
   * @param isAtlasConvertedItem
   * @param receivedContainers
   * @throws ReceivingException
   */
  public void postReceivingUpdatesForOffline(
      Instruction instruction,
      Map<String, DeliveryDocument> deliveryDocumentMap,
      HttpHeaders httpHeaders,
      boolean isAtlasConvertedItem,
      List<ReceivedContainer> receivedContainers,
      List<Container> consolidatedContainerList)
      throws ReceivingException {
    if (isAtlasConvertedItem) {
      Map<String, Container> consolidatedContainerMap =
          consolidatedContainerList
              .stream()
              .collect(
                  Collectors.toMap(
                      container ->
                          ReceivingConstants.PALLET.equals(container.getContainerType())
                                  && !container.getChildContainers().isEmpty()
                              ? container.getChildContainers().iterator().next().getTrackingId()
                              : container.getTrackingId(),
                      Function.identity()));

      for (ReceivedContainer receivedContainer : receivedContainers) {
        DeliveryDocument deliveryDocument =
            deliveryDocumentMap.get(receivedContainer.getLabelTrackingId());
        Container consolidatedContainer = null;
        if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
          /*
          Checking if the feature flag is true and the containerMap contains the tracking ID - which should be ideally be present.
          If feature flag is true, fetching the consolidated container from the map instead of fetching from Persistence layer.
           */
          if (rdcManagedConfig.getEnableSingleTransactionForOffline()
              && (consolidatedContainerMap.containsKey(receivedContainer.getLabelTrackingId()))
              && (rdcManagedConfig
                      .getDcListEligibleForPrepareConsolidatedContainer()
                      .stream()
                      .filter(config -> StringUtils.isNotEmpty(config))
                      .collect(Collectors.toList())
                      .isEmpty()
                  || rdcManagedConfig
                      .getDcListEligibleForPrepareConsolidatedContainer()
                      .contains(
                          Objects.nonNull(
                                  consolidatedContainerList
                                      .get(0)
                                      .getContainerMiscInfo()
                                      .get(RdcConstants.ORIGIN_FACILITY_NUM))
                              ? String.valueOf(
                                  consolidatedContainerList
                                      .get(0)
                                      .getContainerMiscInfo()
                                      .get(RdcConstants.ORIGIN_FACILITY_NUM))
                              : StringUtils.EMPTY))) {
            consolidatedContainer =
                consolidatedContainerMap.get(receivedContainer.getLabelTrackingId());
            LOGGER.info(
                "Consolidate Container already prepared for tracking ID: {} and Delivery Number: {}",
                receivedContainer.getLabelTrackingId(),
                consolidatedContainer.getDeliveryNumber());
          } else {
            consolidatedContainer =
                containerPersisterService.getConsolidatedContainerForPublish(
                    receivedContainer.getLabelTrackingId());
          }
          if (Objects.nonNull(receivedContainer.getAsnNumber())) {
            consolidatedContainer.setAsnNumber(receivedContainer.getAsnNumber());
            consolidatedContainer.setDocumentType(DocumentType.ASN.getDocType());
          }
          consolidatedContainer.setOfflineRcv(true);

          TenantContext.get().setOfflinePublishPostReceivingTimeStart(System.currentTimeMillis());
          if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
              false)) {
            // publish consolidated containers to DcFin
            TenantContext.get()
                .setReceiveInstrPostDcFinReceiptsCallStart(System.currentTimeMillis());
            LOGGER.info(
                "Consolidated containers posting to DcFin for trackingId:{}",
                consolidatedContainer.getTrackingId());
            rdcContainerUtils.postReceiptsToDcFin(
                consolidatedContainer, deliveryDocument.getPurchaseReferenceLegacyType());
            TenantContext.get().setReceiveInstrPostDcFinReceiptsCallEnd(System.currentTimeMillis());
          }

          if (receivedContainer.isSorterDivertRequired()) {
            String labelType = null;
            labelType =
                getLabelTypeForOfflineRcv(
                    receivedContainer, consolidatedContainer, deliveryDocument);

            if (Objects.nonNull(consolidatedContainer.getDestination())
                && Objects.nonNull(
                    consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
              // publish sorter divert message to Athena
              TenantContext.get().setDaCaseReceivingSorterPublishStart(System.currentTimeMillis());
              if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                  TenantContext.getFacilityNum().toString(),
                  RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
                  false)) {
                kafkaAthenaPublisher.publishLabelToSorter(consolidatedContainer, labelType);
              } else {
                jmsSorterPublisher.publishLabelToSorter(consolidatedContainer, labelType);
              }
              TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
            }
          }

          // publish consolidated or parent containers to Inventory
          TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
          rdcContainerUtils.publishContainersToInventory(consolidatedContainer);
          TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());

          // publish putaway message
          TenantContext.get().setDaCaseReceivingPutawayPublishStart(System.currentTimeMillis());
          if (receivedContainer.isRoutingLabel()
              || (isOfflineSymLabelType(receivedContainer, deliveryDocument)
                  && putawayEnabledForBreakPackItems(receivedContainer, deliveryDocument))) {
            symboticPutawayPublishHelper.publishPutawayAddMessage(
                receivedContainer,
                deliveryDocument,
                instruction,
                SymFreightType.XDOCK,
                httpHeaders);
          }
          TenantContext.get().setOfflinePublishPostReceivingTimeEnd(System.currentTimeMillis());
          TenantContext.get().setDaCaseReceivingPutawayPublishEnd(System.currentTimeMillis());

          LOGGER.info(
              "Overall time taken to publish in all components for tracking ID: {} is : {}",
              receivedContainer.getLabelTrackingId(),
              getTimeDifferenceInMillis(
                  TenantContext.get().getOfflinePublishPostReceivingTimeStart(),
                  TenantContext.get().getOfflinePublishPostReceivingTimeEnd()));

          if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())
              && Objects.nonNull(deliveryDocument.getOriginFacilityNum())
              && (rdcManagedConfig
                      .getWpmSites()
                      .contains(deliveryDocument.getOriginFacilityNum().toString())
                  || rdcManagedConfig
                      .getRdc2rdcSites()
                      .contains(deliveryDocument.getOriginFacilityNum().toString()))
              && tenantSpecificConfigReader.getConfiguredFeatureFlag(
                  TenantContext.getFacilityNum().toString(),
                  RdcConstants.IS_EI_INTEGRATION_ENABLED,
                  false)) {
            /** Send DC_PICKS event to EI when it is a configured WPM delivery */
            TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
            if (consolidatedContainer.getInventoryStatus().equals(InventoryStatus.PICKED.name())) {
              String[] eiEvents = ReceivingConstants.EI_DC_PICKED_EVENT;
              rdcContainerUtils.publishContainerToEI(consolidatedContainer, eiEvents);
            }
            TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
          }
        }
        TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
      }
    }
  }

  /**
   * Get label type for sorter divert flow - for offline receiving
   *
   * @param receivedContainer
   * @param container
   * @param deliveryDocument
   * @return
   */
  private String getLabelTypeForOfflineRcv(
      ReceivedContainer receivedContainer, Container container, DeliveryDocument deliveryDocument) {
    if (isOfflineWpmContainer(container)) {
      return getSorterLabelForWpmFreight(receivedContainer, container, deliveryDocument);
    } else if (com.walmart.move.nim.receiving.core.model.sorter.LabelType.XDK1
        .name()
        .equals(receivedContainer.getLabelType())) {
      // XDK1 = PUT (for CC/Imports)
      return com.walmart.move.nim.receiving.core.model.sorter.LabelType.PUT.name();
    } else {
      return getSorterLabelForXDK2(receivedContainer, deliveryDocument);
    }
  }

  /**
   * XDK2 + Item handling code other than E + WPM site + VENDOR PACK container (check child as
   * VENDOR PACK, if container is PALLET) + SYM2/SYM2_5/SYM3 Store Alignment = SYM2/SYM2_5/SYM3
   * Else, STORE
   *
   * @param receivedContainer
   * @param container
   * @param deliveryDocument
   * @return
   */
  private String getSorterLabelForWpmFreight(
      ReceivedContainer receivedContainer, Container container, DeliveryDocument deliveryDocument) {
    if (LabelType.XDK2.name().equals(receivedContainer.getLabelType())
        && isOfflineSymLabelType(receivedContainer, deliveryDocument)
        && !ReceivingConstants.REPACK.equalsIgnoreCase(container.getContainerType())
        && evaluateIfPalletHasCaseContainerForSorter(container)
        && Objects.nonNull(receivedContainer.getStoreAlignment())
        && !RdcConstants.MANUAL.equalsIgnoreCase(receivedContainer.getStoreAlignment())) {
      return SymAsrsSorterMapping.valueOf(receivedContainer.getStoreAlignment()).getSymLabelType();
    } else {
      return LabelType.STORE.name();
    }
  }

  /**
   * If it is a pallet then check for the first child if it is a CASE or REPACK If CASE - can go to
   * SYM and sorter label type will be SYM2/SYM2_5/SYM3 If REPACK - sorter label type will be STORE
   *
   * @param container
   * @return
   */
  private boolean evaluateIfPalletHasCaseContainerForSorter(Container container) {
    boolean isPallet = ReceivingConstants.PALLET.equalsIgnoreCase(container.getContainerType());
    boolean hasCase =
        Objects.nonNull(container.getChildContainers())
            && container
                .getChildContainers()
                .stream()
                .filter(this::isCaseContainerType)
                .findFirst()
                .isPresent();
    return isPallet && hasCase;
  }

  /**
   * Checks if the container has ctrType as CASE
   *
   * @param c
   * @return
   */
  private boolean isCaseContainerType(Container c) {
    return ReceivingConstants.CASE.equalsIgnoreCase(c.getContainerType());
  }

  /**
   * XDK2 + MANUAL store alignment = STORE XDK2 + Item handling code other than E = SYM2/SYM2_5/SYM3
   * XDK2 + Item handling code E = STORE
   *
   * @param receivedContainer
   * @param deliveryDocument
   * @return
   */
  private String getSorterLabelForXDK2(
      ReceivedContainer receivedContainer, DeliveryDocument deliveryDocument) {
    if (LabelType.XDK2.name().equals(receivedContainer.getLabelType())
        && RdcConstants.MANUAL.equalsIgnoreCase(receivedContainer.getStoreAlignment()))
      return LabelType.STORE.name();
    return LabelType.XDK2.name().equals(receivedContainer.getLabelType())
            && isOfflineSymLabelType(receivedContainer, deliveryDocument)
        ? SymAsrsSorterMapping.valueOf(receivedContainer.getStoreAlignment()).getSymLabelType()
        : LabelType.STORE.name();
  }

  /**
   * Validates if it is an offline label type and sym aligned item
   *
   * @param receivedContainer
   * @param deliveryDocument
   * @return
   */
  private boolean isOfflineSymLabelType(
      ReceivedContainer receivedContainer, DeliveryDocument deliveryDocument) {
    String itemHandlingCode =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getItemHandlingMethod();

    if (Objects.nonNull(itemHandlingCode)
        && (Objects.nonNull(receivedContainer.getLabelType())
            && RdcConstants.OFFLINE_LABEL_TYPE.contains(receivedContainer.getLabelType()))) {
      boolean isEligibleItemHandlingCode =
          !CollectionUtils.isEmpty(rdcManagedConfig.getOfflineEligibleItemHandlingCodes())
              ? rdcManagedConfig.getOfflineEligibleItemHandlingCodes().contains(itemHandlingCode)
              : RdcConstants.SYM_ELIGIBLE_CON_ITEM_HANDLING_CODES.contains(itemHandlingCode);

      return isEligibleItemHandlingCode;
    } else return false;
  }

  /**
   * Validate that if offline label type & break pack & repack than putaway should not be send
   *
   * @param receivedContainer
   * @param deliveryDocument
   * @return
   */
  private boolean putawayEnabledForBreakPackItems(
      ReceivedContainer receivedContainer, DeliveryDocument deliveryDocument) {
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_PUTAWAY_ENABLED_FOR_BREAKPACK_ITEMS,
        false)) {

      return !(InventoryLabelType.XDK1.getType().equals(receivedContainer.getLabelType())
          || ReceivingConstants.REPACK.equals(deliveryDocument.getCtrType()));
    } else return true;
  }

  /**
   * Checks CCM configuration if the container is from a configured WPM source Number, if yes, it
   * marks the Label type as STORE
   *
   * @param container
   * @return
   */
  protected boolean isOfflineWpmContainer(Container container) {
    if (Objects.nonNull(container.getContainerMiscInfo())
        && container.getContainerMiscInfo().containsKey(ReceivingConstants.ORIGIN_FACILITY_NUMBER)
        && container.getContainerMiscInfo().get(ReceivingConstants.ORIGIN_FACILITY_NUMBER)
            != null) {
      return rdcManagedConfig
              .getWpmSites()
              .contains(
                  String.valueOf(
                      container
                          .getContainerMiscInfo()
                          .get(ReceivingConstants.ORIGIN_FACILITY_NUMBER)))
          || rdcManagedConfig
              .getRdc2rdcSites()
              .contains(
                  String.valueOf(
                      container
                          .getContainerMiscInfo()
                          .get(ReceivingConstants.ORIGIN_FACILITY_NUMBER)));
    }
    return false;
  }
}
