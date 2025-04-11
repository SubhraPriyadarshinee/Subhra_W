package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.POCON_ACTIVITY_NAME;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Primary
@Component(value = ReceivingConstants.DEFAULT_REFRESH_INSTRUCTION_HANDLER)
public class DefaultRefreshInstructionHandler implements RefreshInstructionHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(DefaultRefreshInstructionHandler.class);

  @Autowired private Gson gson;
  @Autowired private ReceiptService receiptService;
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  /**
   * @param instructionId
   * @param httpHeaders
   * @return
   */
  @Override
  public InstructionResponse refreshInstruction(Instruction instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    logger.info(
        "Default implementation of refresh instruction for instructionId {}", instructionId);

    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);

    if (isKotlinEnabled) {
      logger.info(
          "Enter DefaultRefreshInstructionHandler with instructionId: {}", instructionId.getId());

      InstructionResponse refreshInstructionResponse = new InstructionResponseImplNew();

      if (!(instructionId.getActivityName().equals("Dock Tag")
          && instructionId.getInstructionCode().equals("Dock Tag"))) {
        DeliveryDocument deliveryDocument =
            gson.fromJson(instructionId.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine =
            deliveryDocument.getDeliveryDocumentLines().get(0);

        // Refresh totalReceivedQty
        ImmutablePair<Long, Long> openQtyReceivedQtyPair =
            getOpenQtyReceivedQtyPair(instructionId, deliveryDocument, deliveryDocumentLine);
        Long totalReceivedQty = openQtyReceivedQtyPair.getRight();
        deliveryDocumentLine.setTotalReceivedQty(Math.toIntExact(totalReceivedQty));

        // Refresh openQty
        deliveryDocumentLine.setOpenQty((int) Math.max(openQtyReceivedQtyPair.getLeft(), 0));

        // Set updated deliveryDocument
        instructionId.setDeliveryDocument(gson.toJson(deliveryDocument));

        refreshInstructionResponse.setInstruction(instructionId);
        refreshInstructionResponse.setDeliveryDocuments(
            Collections.singletonList(deliveryDocument));
      }

      return refreshInstructionResponse;
    } else return null;
  }

  private ImmutablePair<Long, Long> getOpenReceivedQtyPairForPOCON(
      DeliveryDocument deliveryDocument, DeliveryDocumentLine deliveryDocumentLine) {
    Long totalReceivedQty =
        receiptService.getReceivedQtyByPoAndDeliveryNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocument.getDeliveryNumber());
    Integer maxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty()
            + Optional.ofNullable(deliveryDocumentLine.getOverageQtyLimit()).orElse(0);
    Long openQty = maxReceiveQty - totalReceivedQty;
    return ImmutablePair.of(openQty, totalReceivedQty);
  }

  private ImmutablePair<Long, Long> getOpenQtyReceivedQtyPair(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    OpenQtyResult openQtyResult;
    OpenQtyCalculator qtyCalculator =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.OPEN_QTY_CALCULATOR,
            ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR,
            OpenQtyCalculator.class);
    if (instruction.getActivityName().equals(POCON_ACTIVITY_NAME)) {
      return getOpenReceivedQtyPairForPOCON(deliveryDocument, deliveryDocumentLine);
    } else if (!StringUtils.isEmpty(instruction.getProblemTagId())) {
      openQtyResult =
          qtyCalculator.calculate(
              instruction.getProblemTagId(),
              deliveryDocument.getDeliveryNumber(),
              deliveryDocument,
              deliveryDocumentLine);
    } else {
      openQtyResult =
          qtyCalculator.calculate(
              deliveryDocument.getDeliveryNumber(), deliveryDocument, deliveryDocumentLine);
    }
    return ImmutablePair.of(
        openQtyResult.getOpenQty(), Long.valueOf(openQtyResult.getTotalReceivedQty()));
  }
}
