package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static io.strati.libs.logging.commons.lang3.StringUtils.isBlank;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.RefreshInstructionHandler;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class GdcRefreshInstructionHandler implements RefreshInstructionHandler {
  private static final Logger logger = LoggerFactory.getLogger(GdcRefreshInstructionHandler.class);

  @Autowired private Gson gson;
  @Autowired private ReceiptService receiptService;

  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;

  @Autowired private InstructionRepository instructionRepository;

  @Autowired private TenantSpecificConfigReader configUtils;

  /**
   * @param instruction
   * @param httpHeaders
   * @return
   */
  @Override
  public InstructionResponse refreshInstruction(Instruction instruction, HttpHeaders httpHeaders)
      throws ReceivingException {
    logger.info("Enter GdcRefreshInstructionHandler with instructionId: {}", instruction.getId());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    InstructionResponse refreshInstructionResponse = new InstructionResponseImplNew();

    // Enrich the PalletTi and High from local DB if it's available.
    AtomicReference<Boolean> hasItemOverride = new AtomicReference<>(Boolean.FALSE);
    if (configUtils.isDeliveryItemOverrideEnabled(getFacilityNum())) {
      deliveryItemOverrideService
          .findByDeliveryNumberAndItemNumber(
              deliveryDocument.getDeliveryNumber(), deliveryDocumentLine.getItemNbr())
          .ifPresent(
              deliveryItemOverride -> {
                deliveryDocumentLine.setPalletTiHiVersion(deliveryItemOverride.getVersion());
                deliveryDocumentLine.setPalletTie(deliveryItemOverride.getTempPalletTi());
                // For backward compatibility
                if (Objects.nonNull(deliveryItemOverride.getTempPalletHi())) {
                  deliveryDocumentLine.setPalletHigh(deliveryItemOverride.getTempPalletHi());
                }
                hasItemOverride.set(Boolean.TRUE);
              });
    }

    // Save instruction with override projected qty
    if (hasItemOverride.get() && isBlank(instruction.getProblemTagId())) {
      instruction.setDeliveryDocument(gson.toJson(deliveryDocument, DeliveryDocument.class));
      instruction.setProjectedReceiveQty(
          deliveryDocumentLine.getPalletHigh() * deliveryDocumentLine.getPalletTie());
      instruction = instructionRepository.save(instruction);
    }

    // Refresh totalReceivedQty
    long totalReceivedQty =
        receiptService.getReceivedQtyByPoAndPoLine(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
    deliveryDocumentLine.setTotalReceivedQty((int) totalReceivedQty);

    // Refresh openQty
    int openQty = deliveryDocumentLine.getTotalOrderQty() - (int) totalReceivedQty;
    deliveryDocumentLine.setOpenQty(openQty < 0 ? 0 : openQty);

    // Set updated deliveryDocument
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    refreshInstructionResponse.setInstruction(instruction);
    refreshInstructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));

    return refreshInstructionResponse;
  }
}
