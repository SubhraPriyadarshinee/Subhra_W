package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;

import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloadingProcessor;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.OperationType;
import com.walmart.move.nim.receiving.mfc.common.StoreDeliveryStatus;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreDeliveryUnloadingProcessor
    implements DeliveryUnloadingProcessor, ProcessExecutor {

  private static Logger LOGGER = LoggerFactory.getLogger(StoreDeliveryUnloadingProcessor.class);

  @Autowired private ProcessInitiator processInitiator;

  @Resource(name = MFCConstant.MFC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private MFCDeliveryService mfcDeliveryService;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    DeliveryInfo deliveryInfo =
        JacksonParser.convertJsonToObject(receivingEvent.getPayload(), DeliveryInfo.class);
    this.doProcess(deliveryInfo);
  }

  @Override
  public boolean isAsync() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        MFCConstant.DELIVERY_UNLOAD_PROCESSING_IN_ASYNC_MODE,
        Boolean.FALSE);
  }

  @Override
  public void doProcess(DeliveryInfo deliveryInfo) {

    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(deliveryInfo.getDeliveryNumber()))
            .orElse(
                DeliveryMetaData.builder()
                    .deliveryNumber(String.valueOf(deliveryInfo.getDeliveryNumber()))
                    .unloadingCompleteDate(new Date())
                    .build());

    if (Objects.nonNull(deliveryMetaData.getDeliveryStatus())
        && !StoreDeliveryStatus.isValidDeliveryStatusForUpdate(
            StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()),
            StoreDeliveryStatus.UNLOADING_COMPLETE)) {
      LOGGER.info(
          "Delivery {} is already in Unload Complete status hence ignoring the flow",
          deliveryInfo.getDeliveryNumber());
      return;
    }

    deliveryMetaData.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE);
    deliveryMetaData.setUnloadingCompleteDate(new Date());
    deliveryMetaDataService.save(deliveryMetaData);
    LOGGER.info(
        "Delivery Metadata for unloading ts details are updated for deliveryNumber={}",
        deliveryInfo.getDeliveryNumber());

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

    ASNDocument asnDocument =
        mfcDeliveryService.getShipmentDataFromGDM(deliveryInfo.getDeliveryNumber(), null);

    if (MFCUtils.isDSDShipment(asnDocument.getShipment())) {
      LOGGER.info("Ignoring unload complete processing flow for DSD delivery.");
      return;
    }
    Map<String, Object> additionalAttribute = new HashMap<>();

    // Manual Finalization for all delivery
    if (MANUAL_FINALISE_DELIVERY.equals(deliveryInfo.getAction())) {
      LOGGER.info(
          "Unload complete for manually finalized delivery {}", deliveryInfo.getDeliveryNumber());
      additionalAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, CONTAINER_FILTER_TYPE_CASE);
      additionalAttribute.put(MFCConstant.OPERATION_TYPE, OperationType.MANUAL_FINALISE);
      // SIB-Phase-II : If receiving is down for days and they have received it manually.
      // ManualFinalization will help to create the container . Hence, both the flag were true
      // SIB-Phase-III : Now user will receive everything and then click on manual finalize to make
      // it available on EI with in a configurable time
      additionalAttribute.put(MFC_PALLET_INCLUDED, Boolean.FALSE);
      additionalAttribute.put(STORE_PALLET_INCLUDED, Boolean.FALSE);
    }
    if (mfcManagedConfig
        .getStorePalletCreateEnabledFacilities()
        .contains(asnDocument.getShipment().getSource().getNumber())) {
      // Manual Finalization for specific irrespective store pallet are received or not , it should
      // get received and MFC Pallet should not get received in bulk processing
      LOGGER.info(
          "Unload complete for source dc that need auto pallet creation {} for delivery number {}",
          asnDocument.getShipment().getSource().getNumber(),
          deliveryInfo.getDeliveryNumber());
      deliveryInfo.setAction(MANUAL_FINALISE_DELIVERY);
      additionalAttribute.put(MFCConstant.CONTAINER_FILTER_TYPE, CONTAINER_FILTER_TYPE_MFC_PALLET);
      additionalAttribute.put(MFCConstant.OPERATION_TYPE, OperationType.NORMAL);
      // mfc pallet should not get received . however, it should get into exception flow
      additionalAttribute.put(MFC_PALLET_INCLUDED, Boolean.FALSE);
      additionalAttribute.put(STORE_PALLET_INCLUDED, Boolean.TRUE);
    }
    LOGGER.info(
        "Unloading complete for delivery={} with additionalAttributes={}",
        deliveryInfo.getDeliveryNumber(),
        additionalAttribute);

    // Action for Performing manual finzation
    if (MANUAL_FINALISE_DELIVERY.equals(deliveryInfo.getAction())) {
      ReceivingEvent receivingEvent =
          ReceivingEvent.builder()
              .payload(JacksonParser.writeValueAsString(asnDocument))
              .name(STORE_BULK_RECEIVING_PROCESSOR)
              .additionalAttributes(additionalAttribute)
              .processor(STORE_BULK_RECEIVING_PROCESSOR)
              .build();
      LOGGER.info("Going to initiate the process for manual finalisation");
      processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
      LOGGER.info("Completed the process for manual finalisation");
      return;
    }

    // If Store pallet shortages are created earlier, then filter out store pallets and only
    // create shortages for MFC pallets based on "problemCreationOnUnloadFor" tenant flag
    Map<String, String> palletTypeMap = MFCUtils.getPalletTypeMap(asnDocument.getPacks());

    asnDocument.setPacks(
        asnDocument
            .getPacks()
            .stream()
            .filter(
                pack ->
                    getProblemCreationOnUnloadForTypes()
                        .contains(palletTypeMap.get(pack.getPalletNumber())))
            .collect(Collectors.toList()));

    // Action for overage / shortage handling .
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(asnDocument))
            .name(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .additionalAttributes(forwardableHeaders)
            .processor(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
            .build();
    LOGGER.info(
        "Going to initiate the process for unloading processing for delivery Number {}",
        deliveryInfo.getDeliveryNumber());
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    LOGGER.info("Completed the process for unloading processing");
  }

  private List<String> getProblemCreationOnUnloadForTypes() {
    JsonArray palletTypes =
        tenantSpecificConfigReader
            .getCcmConfigValueAsJson(
                TenantContext.getFacilityNum().toString(), PROBLEM_CREATION_ON_UNLOAD_FOR)
            .getAsJsonArray();
    List<String> problemCreationOnUnloadForTypes = new ArrayList<>();
    palletTypes.forEach(
        palletType -> problemCreationOnUnloadForTypes.add(palletType.getAsString()));
    return problemCreationOnUnloadForTypes;
  }
}
