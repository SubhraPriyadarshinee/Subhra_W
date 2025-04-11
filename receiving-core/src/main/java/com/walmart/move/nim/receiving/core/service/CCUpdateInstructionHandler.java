package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.validators.WeightThresholdValidator;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.StringUtils;
import io.strati.libs.logging.commons.lang3.tuple.Pair;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(value = ReceivingConstants.CC_UPDATE_INSTRUCTION_HANDLER)
public class CCUpdateInstructionHandler extends DefaultUpdateInstructionHandler {

  @Autowired WeightThresholdValidator weightThresholdValidator;
  private static final Logger log = LoggerFactory.getLogger(CCUpdateInstructionHandler.class);

  @Override
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    Instruction instruction4mDb = instructionPersisterService.getInstructionById(instructionId);
    DocumentLine documentLine4mRequest =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);

    Optional<DeliveryDocument> deliveryDocument4mDbOptional =
        Optional.ofNullable(InstructionUtils.getDeliveryDocument(instruction4mDb));
    Optional<DeliveryDocumentLine> documentLine4mDbOptional =
        Optional.ofNullable(InstructionUtils.getDeliveryDocumentLine(instruction4mDb));

    if (deliveryDocument4mDbOptional.isPresent() && documentLine4mDbOptional.isPresent()) {
      // In this flow, enrich request documentLine with information from document line present in
      // instruction table. quantity, quantityUOM, and rotateDate (not currently used for CCs) are
      // all taken from the request document line
      OpenQtyResult openQtyResult;
      OpenQtyCalculator qtyCalculator =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.OPEN_QTY_CALCULATOR,
              OpenQtyCalculator.class);

      if (!StringUtils.isEmpty(instructionUpdateRequestFromClient.getProblemTagId())) {
        openQtyResult =
            qtyCalculator.calculate(
                instructionUpdateRequestFromClient.getProblemTagId(),
                deliveryDocument4mDbOptional.get().getDeliveryNumber(),
                deliveryDocument4mDbOptional.get(),
                documentLine4mDbOptional.get());
      } else {
        openQtyResult =
            qtyCalculator.calculate(
                deliveryDocument4mDbOptional.get().getDeliveryNumber(),
                deliveryDocument4mDbOptional.get(),
                documentLine4mDbOptional.get());
      }
      long maxReceiveQty = openQtyResult.getMaxReceiveQty();
      int totalReceivedQty = openQtyResult.getTotalReceivedQty();
      int openQty = Math.toIntExact(openQtyResult.getOpenQty());

      DocumentLine mappedDocumentLine =
          InstructionUtils.mapInstructionDeliveryDocumentToDeliveryDocumentLine(
              deliveryDocument4mDbOptional.get(),
              documentLine4mDbOptional.get(),
              documentLine4mRequest.getQuantity(),
              documentLine4mRequest.getQuantityUOM(),
              documentLine4mRequest.getRotateDate(),
              maxReceiveQty);

      // call default handler for taking forward existing flow of update instruction
      return processUpdateInstructionRequest(
          instructionUpdateRequestFromClient, httpHeaders, mappedDocumentLine, instruction4mDb);
    } else {
      // TODO: need to check error thrown for this
      // in this case po and po line not present in instruction table, log appropriate message
      log.error(
          "po or po line info not present in instruction table instructionId: {}, deliveryDoc: {}, deliveryDocLine: {}",
          instructionId,
          deliveryDocument4mDbOptional.get(),
          documentLine4mDbOptional.get());
      return new InstructionResponseImplNew();
    }
  }

  @Override
  public void updateTotalReceivedQtyInInstructionDeliveryDoc(
      Instruction instruction, DocumentLine mappedDocumentLine, Integer quantityToBeReceived) {
    Gson gson = new Gson();
    DeliveryDocument deliveryDocument4mDb =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine documentLine4mDb = InstructionUtils.getDeliveryDocumentLine(instruction);
    if (Objects.nonNull(deliveryDocument4mDb) && Objects.nonNull(documentLine4mDb)) {
      // total receivedQty needs to be the total received qty for this delivery, po, po line
      // combination. Using the value from instruction table delivery doc for this, so that this
      // context is preserved
      documentLine4mDb.setTotalReceivedQty(
          Optional.ofNullable(mappedDocumentLine.getTotalReceivedQty()).orElse(0)
              + quantityToBeReceived);

      // this total received qty from above should reflect everywhere across instructions for
      // particular delivery, po, pol
      Integer totalReceivedQty = documentLine4mDb.getTotalReceivedQty();
      Integer totalOrderQty = documentLine4mDb.getTotalOrderQty();
      if (totalReceivedQty >= totalOrderQty) {
        // in case of allowable overage exceeded, with current receipts, set max allowed included
        // flag
        log.info(
            "Received/ordered qty : {}/{} for Delivery {} PO {} POL {}, setting MaxAllowedOverageQtyIncluded flag for client",
            totalReceivedQty,
            totalOrderQty,
            deliveryDocument4mDb.getDeliveryNumber(),
            documentLine4mDb.getPurchaseReferenceNumber(),
            documentLine4mDb.getPurchaseReferenceLineNumber());
        documentLine4mDb.setMaxAllowedOverageQtyIncluded(Boolean.TRUE);
      }
      deliveryDocument4mDb.setDeliveryDocumentLines(Collections.singletonList(documentLine4mDb));
      instruction.setDeliveryDocument(gson.toJson(deliveryDocument4mDb));
    }
  }

  @Override
  public void validateWeightThreshold(
      DocumentLine documentLine,
      Integer quantityAlreadyReceived,
      Integer quantityToBeReceived,
      String quantityUom) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.TIHI_WEIGHT_THRESHOLD_VALIDATION_ENABLED)) {
      weightThresholdValidator.validate(
          documentLine, quantityAlreadyReceived, quantityToBeReceived, quantityUom);
    }
  }

  private DeliveryDocumentLine mapFromDocumentLine(DocumentLine documentLine) {
    // TODO map all fields and move to external mapper
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
    deliveryDocumentLine.setPurchaseReferenceLineNumber(
        documentLine.getPurchaseReferenceLineNumber());
    deliveryDocumentLine.setItemNbr(documentLine.getItemNumber());
    deliveryDocumentLine.setTotalOrderQty(
        Math.toIntExact(Optional.ofNullable(documentLine.getExpectedQty()).orElse(0L)));
    deliveryDocumentLine.setQtyUOM(documentLine.getQuantityUOM());
    deliveryDocumentLine.setFreightBillQty(documentLine.getFreightBillQty());
    deliveryDocumentLine.setOverageQtyLimit(
        Math.toIntExact(Optional.ofNullable(documentLine.getMaxOverageAcceptQty()).orElse(0L)));
    return deliveryDocumentLine;
  }

  @Override
  protected Pair<Long, Long> getReceivedAndMaxReceiveQty(
      String problemTagId, DeliveryDocument deliveryDocument, DocumentLine documentLine) {
    Long currentReceiveQuantity;
    Long maxReceiveQuantity;
    if (!StringUtils.isEmpty(problemTagId)) {
      currentReceiveQuantity = receiptService.getReceivedQtyByProblemId(problemTagId);
      maxReceiveQuantity = documentLine.getMaxReceiveQty();
    } else {
      OpenQtyCalculator qtyCalculator =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.OPEN_QTY_CALCULATOR,
              ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR,
              OpenQtyCalculator.class);

      OpenQtyResult openQtyResult =
          qtyCalculator.calculate(
              deliveryDocument.getDeliveryNumber(),
              deliveryDocument,
              mapFromDocumentLine(documentLine));

      currentReceiveQuantity = Long.valueOf(openQtyResult.getTotalReceivedQty());
      // TODO set this to fbq based on flag (or add method to open qty calculator)
      //      maxReceiveQuantity = documentLine.getExpectedQty();
      //      maxReceiveQuantity +=
      // Optional.ofNullable(documentLine.getMaxOverageAcceptQty()).orElse(0L);
      maxReceiveQuantity = openQtyResult.getMaxReceiveQty();
    }
    return Pair.of(currentReceiveQuantity, maxReceiveQuantity);
  }
}
