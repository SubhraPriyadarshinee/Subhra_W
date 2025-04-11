package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DefaultDeliveryDocumentSelector implements DeliveryDocumentSelector {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultDeliveryDocumentSelector.class);

  @Autowired ReceiptService receiptService;

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private InstructionUtils instructionUtils;
  private InstructionError instructionError;

  @Override
  public Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDeliveryDocumentLine(
      List<DeliveryDocument> deliveryDocuments) {
    // Prepare the list of PO/LINE
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      poNumberList.add(deliveryDocument.getPurchaseReferenceNumber());
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        poLineNumberSet.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    }
    ReceiptsAggregator receivedQtyByPoPol =
        getReceivedQtyByPoPol(deliveryDocuments, poNumberList, poLineNumberSet);
    // Sorting the delivery document lines based on the purchase reference line number
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          deliveryDocument.getDeliveryDocumentLines();
      if (!CollectionUtils.isEmpty(deliveryDocumentLines)) {
        deliveryDocumentLines =
            deliveryDocumentLines
                .stream()
                .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
                .collect(Collectors.toList());
        deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
      }
    }

    // Sort the delivery documents by MABD
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());

    // Pick the line based on totalOrderQty
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        autoSelectDeliveryDocumentLine(deliveryDocuments, receivedQtyByPoPol, Boolean.FALSE);

    if (selectedLine != null) {
      return selectedLine;
    }

    // Pick the line based on max limit(totalOrderQty + overageQtyLimit)
    // TODO: check if allowed overage qty is there or not,
    return autoSelectDeliveryDocumentLine(deliveryDocuments, receivedQtyByPoPol, Boolean.TRUE);
  }

  public ReceiptsAggregator getReceivedQtyByPoPol(
      List<DeliveryDocument> deliveryDocuments,
      List<String> poNumberList,
      Set<Integer> poLineNumberSet) {
    List<ReceiptSummaryEachesResponse> poLineReceipts =
        receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    ReceiptsAggregator receiptsAggregator = ReceiptsAggregator.fromPOLReceipts(poLineReceipts);
    return receiptsAggregator;
  }

  public ImmutablePair<Long, Long> getOpenQtyTotalReceivedQtyForLineSelection(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine,
      ReceiptsAggregator receiptsAggregator,
      Boolean includeOverage) {
    // Calculate open qty on po fbq
    Integer overageQuantity =
        includeOverage ? Optional.ofNullable(documentLine.getOverageQtyLimit()).orElse(0) : 0;
    Integer maxReceiveQtyPOL = documentLine.getTotalOrderQty() + overageQuantity;
    Integer maxReceiveQtyPOLInEA =
        ReceivingUtils.conversionToEaches(
            maxReceiveQtyPOL,
            documentLine.getQtyUOM(),
            documentLine.getVendorPack(),
            documentLine.getWarehousePack());
    Long receivedQtyOnPOLInEA =
        receiptsAggregator.getByPoPol(
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    long openQtyOnPOLInEA = maxReceiveQtyPOLInEA - receivedQtyOnPOLInEA;
    LOGGER.info(
        "For Delivery: {} PO: {} POL: {}, "
            + " POL maxReceiveQty (EA): {} POL Receipt (EA): {} POL openQty (EA): {}",
        deliveryDocument.getDeliveryNumber(),
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceLineNumber(),
        maxReceiveQtyPOLInEA,
        receivedQtyOnPOLInEA,
        openQtyOnPOLInEA);

    return ImmutablePair.of(openQtyOnPOLInEA, receivedQtyOnPOLInEA);
  }

  private Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDeliveryDocumentLine(
      List<DeliveryDocument> deliveryDocuments,
      ReceiptsAggregator receiptsAggregator,
      Boolean includeOverageQtyLimit) {
    LOGGER.info("Selecting line with includeOverageQtyLimit: {}", includeOverageQtyLimit);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine = null;
    List<DeliveryDocument> openQtyPos = new ArrayList<>();
    List<DeliveryDocument> noqtyleftPos = new ArrayList<>();
    boolean isOpenQtyPo = false;
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          deliveryDocument.getDeliveryDocumentLines();
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        ImmutablePair<Long, Long> openQtyReceivedQtyPair =
            getOpenQtyTotalReceivedQtyForLineSelection(
                deliveryDocument, deliveryDocumentLine, receiptsAggregator, includeOverageQtyLimit);
        setFieldsInDocumentLine(deliveryDocumentLine, openQtyReceivedQtyPair);
        if (openQtyReceivedQtyPair.getLeft() > 0) {
          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.IS_MULTI_USER_RECEIVE_ENABLED)) {
            openQtyPos.add(deliveryDocument);
            Boolean isQtyAvailable =
                instructionUtils.checkIfNewInstructionCanBeCreated(
                    deliveryDocumentLine, deliveryDocument, openQtyReceivedQtyPair);
            if (isQtyAvailable) {
              selectedLine = new Pair<>(deliveryDocument, deliveryDocumentLine);
              isOpenQtyPo = true;
              break;
            }
            if (!isOpenQtyPo) {
              noqtyleftPos.add(deliveryDocument);
            }
          } else {
            selectedLine = new Pair<>(deliveryDocument, deliveryDocumentLine);
            break;
          }
        }
      }
      if (!Objects.isNull(selectedLine)) {
        break;
      }
    }
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MULTI_USER_RECEIVE_ENABLED)
        && openQtyPos.size() >= 1) {
      if (openQtyPos.size() == noqtyleftPos.size()) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
        ErrorResponse errorResponse;
        errorResponse =
            ErrorResponse.builder()
                .errorMessage(instructionError.getErrorMessage())
                .errorCode(REQUEST_TRANSFTER_INSTR_ERROR_CODE)
                .errorHeader(instructionError.getErrorHeader())
                .errorKey(ExceptionCodes.MULTI_USER_ERROR)
                .build();
        try {
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        } catch (ReceivingException e) {
          throw new RuntimeException(e);
        }
      }
    }
    LOGGER.info("Exit autoSelectDeliveryDocumentLine with selectedLine: {}", selectedLine);
    return selectedLine;
  }

  public static void setFieldsInDocumentLine(
      DeliveryDocumentLine deliveryDocumentLine, ImmutablePair<Long, Long> openQtyReceivedQtyPair) {
    // TODO from effective calc method return in ZA instead of EA
    deliveryDocumentLine.setTotalReceivedQty(
        ReceivingUtils.conversionToVendorPack(
            Math.toIntExact(openQtyReceivedQtyPair.getRight()),
            ReceivingConstants.Uom.EACHES,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
    deliveryDocumentLine.setOpenQty(
        ReceivingUtils.conversionToVendorPack(
            Math.toIntExact(openQtyReceivedQtyPair.getLeft()),
            ReceivingConstants.Uom.EACHES,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
  }
}
