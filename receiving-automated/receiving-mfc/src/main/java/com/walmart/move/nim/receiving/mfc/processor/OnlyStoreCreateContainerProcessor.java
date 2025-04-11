package com.walmart.move.nim.receiving.mfc.processor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import java.util.Date;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnlyStoreCreateContainerProcessor extends StoreInboundCreateContainerProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OnlyStoreCreateContainerProcessor.class);

  private Gson gson;

  public OnlyStoreCreateContainerProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public String createContainer(ContainerScanRequest containerScanRequest) {

    // Check is delivery complete
    super.isDeliveryComplete(containerScanRequest);

    // Check is container already exist
    super.isContainerAlreadyExist(containerScanRequest);

    ASNDocument asnDocument = super.getASNDocument(containerScanRequest);

    if (Objects.nonNull(asnDocument)
        && Objects.nonNull(asnDocument.getShipments())
        && asnDocument.getShipments().size() > 1) {
      LOGGER.info(
          "Got multiple ASN Document for payload= {} and hence, sending back for user selection.",
          containerScanRequest);
      return gson.toJson(asnDocument);
    }

    validateNonMFCPacks(asnDocument, containerScanRequest);

    Container container =
        mfcContainerService.createTransientContainer(containerScanRequest, asnDocument);
    ContainerDTO containerDTO = containerCreation(containerScanRequest, container, asnDocument);
    mfcContainerService.publishContainer(containerDTO);
    return gson.toJson(containerDTO);
  }

  private void validateNonMFCPacks(ASNDocument asnDoc, ContainerScanRequest containerScanRequest) {
    // validation: MFC pallet is not allowed in store site only.
    ASNDocument asnDocument =
        Objects.isNull(asnDoc) ? containerScanRequest.getAsnDocument() : asnDoc;
    if (PalletType.MFC.equalsType(MFCUtils.getPalletType(asnDocument.getPacks()))) {
      LOGGER.info(
          "Store Inbound: MFC pallet not allowed, rejecting pallet {} for delivery {}",
          containerScanRequest.getTrackingId(),
          containerScanRequest.getDeliveryNumber());
      throw new ReceivingConflictException(
          ExceptionCodes.INVALID_PALLET,
          String.format("MFC pallet is not allowed in Only Store site."));
    }
  }
}
