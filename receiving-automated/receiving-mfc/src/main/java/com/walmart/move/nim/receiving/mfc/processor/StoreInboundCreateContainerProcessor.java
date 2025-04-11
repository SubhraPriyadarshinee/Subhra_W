package com.walmart.move.nim.receiving.mfc.processor;

import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.service.v2.CreateContainerProcessor;
import com.walmart.move.nim.receiving.mfc.model.gdm.ScanPalletRequest;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class StoreInboundCreateContainerProcessor implements CreateContainerProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreInboundCreateContainerProcessor.class);

  @Autowired private MFCDeliveryService deliveryService;

  @Autowired protected MFCContainerService mfcContainerService;

  @Autowired protected MFCDeliveryMetadataService mfcDeliveryMetadataService;

  private Gson gson;

  public StoreInboundCreateContainerProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public String createContainer(ContainerScanRequest containerScanRequest) {

    // Check is delivery complete
    isDeliveryComplete(containerScanRequest);

    // Check is container already exist
    isContainerAlreadyExist(containerScanRequest);

    ASNDocument asnDocument = getASNDocument(containerScanRequest);

    if (nonNull(asnDocument)
        && nonNull(asnDocument.getShipments())
        && asnDocument.getShipments().size() > 1) {
      LOGGER.info(
          "Got multiple ASN Document for payload= {} and hence, sending back for user selection.",
          containerScanRequest);
      return gson.toJson(asnDocument);
    }
    Container container =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    ContainerDTO containerDTO = containerCreation(containerScanRequest, container, asnDocument);
    mfcContainerService.publishContainer(containerDTO);
    return gson.toJson(containerDTO);
  }

  public ASNDocument getASNDocument(ContainerScanRequest containerScanRequest) {

    ASNDocument asnDocument = getAsnDocument(containerScanRequest, false);
    return asnDocument;
  }

  public ASNDocument getAsnDocument(
      ContainerScanRequest containerScanRequest, boolean includePalletRelations) {
    ASNDocument asnDocument = containerScanRequest.getAsnDocument();

    if (Objects.isNull(asnDocument)) {
      // Needed for overage pallet from another delivery
      Long deliveryNumber =
          Objects.isNull(containerScanRequest.getOriginalDeliveryNumber())
              ? containerScanRequest.getDeliveryNumber()
              : containerScanRequest.getOriginalDeliveryNumber();

      ScanPalletRequest scanPalletRequest =
          ScanPalletRequest.builder()
              .palletNumber(containerScanRequest.getTrackingId())
              .deliveryNumber(deliveryNumber)
              .build();

      LOGGER.info("Going to download ASN with payload : {}", gson.toJson(deliveryNumber));

      asnDocument =
          deliveryService.findDeliveryDocumentByPalletAndDelivery(
              scanPalletRequest, includePalletRelations);

      // Setting the current delivery context to receive
      asnDocument.getDelivery().setDeliveryNumber(containerScanRequest.getDeliveryNumber());
    }
    return asnDocument;
  }

  protected void isDeliveryComplete(ContainerScanRequest containerScanRequest) {
    Optional<DeliveryMetaData> deliveryMetaData =
        mfcDeliveryMetadataService.findByDeliveryNumber(
            String.valueOf(containerScanRequest.getDeliveryNumber()));

    if (deliveryMetaData.isPresent()
        && deliveryMetaData.get().getDeliveryStatus().equals(DeliveryStatus.COMPLETE)) {
      throw new ReceivingConflictException(
          ExceptionCodes.UNABLE_TO_RECEIVE,
          String.format(
              "Delivery %d is in complete stage.", containerScanRequest.getDeliveryNumber()));
    }
  }

  protected void isContainerAlreadyExist(ContainerScanRequest containerScanRequest) {
    Container container =
        mfcContainerService.findTopBySsccNumberAndDeliveryNumber(
            containerScanRequest.getTrackingId(), containerScanRequest.getDeliveryNumber());
    if (nonNull(container))
      throw new ReceivingConflictException(
          ExceptionCodes.CONTAINER_ALREADY_EXISTS,
          String.format(
              "Container with pallet number %s and delivery %d already exists",
              containerScanRequest.getTrackingId(), containerScanRequest.getDeliveryNumber()));
  }

  public ContainerDTO containerCreation(
      ContainerScanRequest containerScanRequest, Container container, ASNDocument asnDoc) {
    ASNDocument asnDocument =
        Objects.isNull(asnDoc) ? containerScanRequest.getAsnDocument() : asnDoc;
    if (Objects.isNull(asnDocument.getShipment()) && !asnDocument.getShipments().isEmpty()) {
      asnDocument.setShipment(asnDocument.getShipments().get(0));
    }
    LOGGER.info(
        "Going to filter packs for the selected container with trackingId = {}",
        containerScanRequest.getTrackingId());
    if (nonNull(containerScanRequest.getOverageType())) {
      asnDocument.setOverage(Boolean.TRUE);
    }
    try {
      return mfcContainerService.createContainer(container, asnDocument);

    } catch (ReceivingConflictException | ReceivingInternalException exception) {
      throw exception;
    } catch (Exception e) {
      LOGGER.error(
          "Error while processing pallet {} delivery {}",
          containerScanRequest.getTrackingId(),
          containerScanRequest.getDeliveryNumber(),
          e);
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_RECEIVE,
          String.format("Error occurred while receiving pallet."));
    }
  }

  public Gson getGson() {
    return gson;
  }

  public MFCDeliveryService getDeliveryService() {
    return deliveryService;
  }

  public MFCContainerService getMfcContainerService() {
    return mfcContainerService;
  }

  public MFCDeliveryMetadataService getMfcDeliveryMetadataService() {
    return mfcDeliveryMetadataService;
  }
}
