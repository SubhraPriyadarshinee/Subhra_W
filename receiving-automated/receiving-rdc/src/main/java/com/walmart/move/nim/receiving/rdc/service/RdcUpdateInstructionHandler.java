package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(RdcConstants.RDC_UPDATE_INSTRUCTION_HANDLER)
public class RdcUpdateInstructionHandler implements UpdateInstructionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RdcUpdateInstructionHandler.class);

  @Autowired private Gson gson;

  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private ReceiptService receiptService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private ContainerService containerService;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Autowired private RdcInstructionHelper rdcInstructionHelper;

  @Override
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest updateInstructionRequest,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    LOG.info("Update Instruction for instruction id:{}", instructionId);

    Instruction instruction4mDB = instructionPersisterService.getInstructionById(instructionId);
    instructionStateValidator.validate(instruction4mDB);

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivingUtils.verifyUser(instruction4mDB, userId, RequestType.COMPLETE);

    DeliveryDocument deliveryDocument4mDB =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);

    int receivedQty = updateInstructionRequest.getDeliveryDocumentLines().get(0).getQuantity();

    rdcInstructionUtils.verifyAndPopulateProDateInfo(
        deliveryDocument4mDB, instruction4mDB, httpHeaders);

    rdcInstructionUtils.validateOverage(
        deliveryDocument4mDB.getDeliveryDocumentLines(), receivedQty, instruction4mDB, httpHeaders);
    Optional<Container> containerByInstructionId =
        containerService.findOneContainerByInstructionId(instruction4mDB.getId());
    Container container = null;
    List<Receipt> receipts = Collections.emptyList();
    int receiveQtyDelta = receivedQty - instruction4mDB.getReceivedQuantity();
    if (!containerByInstructionId.isPresent()) {
      String generatedTrackingId = generateTrackingId(httpHeaders);
      instruction4mDB.setContainer(buildContainerDetails(generatedTrackingId));
      receipts =
          buildReceipts(
              receipts,
              deliveryDocument4mDB,
              updateInstructionRequest.getDoorNumber(),
              instruction4mDB,
              userId,
              receivedQty);
      container =
          rdcInstructionHelper.buildContainerAndContainerItem(
              instruction4mDB,
              deliveryDocument4mDB,
              updateInstructionRequest,
              receivedQty,
              userId,
              generatedTrackingId);
    } else if (containerByInstructionId.isPresent() && receiveQtyDelta != 0) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument4mDB.getDeliveryDocumentLines().get(0);

      // update existing receipts
      receipts =
          buildReceipts(
              receipts,
              deliveryDocument4mDB,
              updateInstructionRequest.getDoorNumber(),
              instruction4mDB,
              userId,
              receivedQty);
      // update existing containers
      container = containerByInstructionId.get();
      List<ContainerItem> containerItems = container.getContainerItems();
      ContainerItem containerItem = containerItems.get(0);
      Integer receivedQtyInEaches =
          ReceivingUtils.conversionToEaches(
              receivedQty,
              ReceivingConstants.Uom.VNPK,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
      containerItem.setQuantity(receivedQtyInEaches);
      containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
      container.setContainerItems(Arrays.asList(containerItem));
    }

    LinkedTreeMap<String, Object> moveTreeMap = instruction4mDB.getMove();
    if (Objects.nonNull(moveTreeMap) && !moveTreeMap.isEmpty()) {
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_BY, userId);
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    }
    instruction4mDB.setMove(moveTreeMap);
    instruction4mDB.setReceivedQuantity(receivedQty);
    instruction4mDB.setLastChangeUserId(userId);

    rdcInstructionHelper.persistForUpdateInstruction(instruction4mDB, container, receipts);

    return new InstructionResponseImplNew(null, null, instruction4mDB, null);
  }

  private String generateTrackingId(HttpHeaders httpHeaders) {
    String trackingId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    if (StringUtils.isBlank(trackingId)) {
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_LPN, RdcConstants.INVALID_LPN);
    }
    return trackingId;
  }

  public static ContainerDetails buildContainerDetails(String labelTrackingId) {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(labelTrackingId);
    containerDetails.setCtrType(ContainerType.PALLET.getText());
    containerDetails.setInventoryStatus(InventoryStatus.PICKED.name());
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrStatus(ReceivingConstants.STATUS_COMPLETE);
    containerDetails.setOutboundChannelMethod(RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU);
    return containerDetails;
  }

  private List<Receipt> buildReceipts(
      List<Receipt> receipts,
      DeliveryDocument deliveryDocument,
      String doorNumber,
      Instruction instruction4mDB,
      String userId,
      int receiveQtyDelta) {
    if (rdcInstructionUtils.isAtlasConvertedInstruction(instruction4mDB)) {
      receipts =
          receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
              deliveryDocument, doorNumber, null, userId, receiveQtyDelta);
    }
    return receipts;
  }
}
