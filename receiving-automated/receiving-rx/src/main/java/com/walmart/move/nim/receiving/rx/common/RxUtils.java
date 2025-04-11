package com.walmart.move.nim.receiving.rx.common;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.EXEMPTED_DEPT_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FIXIT_ISSUE_TYPE_DI;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import com.google.gson.Gson;
import com.walmart.move.nim.atlas.platform.policy.commons.Message;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.Resolution;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.utils.constants.ProblemResolutionType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

public class RxUtils {

  private static final List<Integer> transferredPoTypeCodes = Arrays.asList(28, 29);
  private static final Logger LOG = LoggerFactory.getLogger(RxUtils.class);
  private static final List<String> partialInstructionCodes =
      Arrays.asList(
          RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType(),
          RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType(),
          RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());

  private RxUtils() {}

  public static Map<String, ScannedData> scannedDataMap(List<ScannedData> scannedDataList) {
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    if (Objects.nonNull(scannedDataList)) {
      scannedDataList.forEach(
          scannedData -> {
            scannedDataMap.put(scannedData.getKey(), scannedData);
          });
    }
    return scannedDataMap;
  }

  public static int expectedQuantityInPallet(
      List<ManufactureDetail> manufactureDetails, Integer vnpkQty, Integer whpkQty) {
    return manufactureDetails
        .stream()
        .mapToInt(
            manufactureDetail ->
                ReceivingUtils.conversionToVendorPack(
                    manufactureDetail.getQty(),
                    manufactureDetail.getReportedUom(),
                    vnpkQty,
                    whpkQty))
        .sum();
  }

  public static ReceivingException convertToReceivingException(
      ReceivingBadDataException receivingBadDataException) {
    return new ReceivingException(
        receivingBadDataException.getDescription(),
        HttpStatus.BAD_REQUEST,
        receivingBadDataException.getErrorCode());
  }

  public static ReceivingBadDataException convertToReceivingBadDataException(
      ReceivingException receivingException) {
    String errorCode =
        StringUtils.isNotEmpty(receivingException.getErrorResponse().getErrorCode())
            ? receivingException.getErrorResponse().getErrorCode()
            : ExceptionCodes.RECEIVING_INTERNAL_ERROR;
    return new ReceivingBadDataException(
        errorCode, receivingException.getErrorResponse().getErrorMessage().toString());
  }

  public static String formatDate(String fromDate) throws ParseException {
    DateFormat formatter;
    formatter = new SimpleDateFormat(ReceivingConstants.SIMPLE_DATE);
    Date simpleFormatDate = formatter.parse(fromDate);
    formatter = new SimpleDateFormat(ReceivingConstants.EXPIRY_DATE_FORMAT);
    return formatter.format(simpleFormatDate);
  }

  public static boolean isDscsaExemptionIndEnabled(
      @NotNull DeliveryDocumentLine deliveryDocumentLine, boolean enableDepartmentCheck) {

    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    String deptNumber = deliveryDocumentLine.getDepartment();
    if (enableDepartmentCheck && EXEMPTED_DEPT_TYPE.equals(deptNumber)) {
      return true;
    } else if (Objects.nonNull(additionalInfo)) {
      Boolean isDscsaExemptionInd = additionalInfo.getIsDscsaExemptionInd();
      return Boolean.TRUE.equals(isDscsaExemptionInd);
    }
    return false;
  }

  public static boolean isTransferredPo(Integer poTypeCode) {
    return transferredPoTypeCodes.contains(poTypeCode);
  }

  public static boolean isDscsaNonExempted(
      @NotNull DeliveryDocumentLine deliveryDocumentLine, boolean enableDepartmentCheck) {
    return !isDscsaExemptionIndEnabled(deliveryDocumentLine, enableDepartmentCheck);
  }

  public static boolean isMultiSKUPallet(List<DeliveryDocument> deliveryDocuments) {
    if (!CollectionUtils.isEmpty(deliveryDocuments)
        && !CollectionUtils.isEmpty(deliveryDocuments.get(0).getDeliveryDocumentLines())) {
      Map<String, List<DeliveryDocumentLine>> distinctItems =
          deliveryDocuments
              .get(0)
              .getDeliveryDocumentLines()
              .stream()
              .collect(groupingBy(DeliveryDocumentLine::getGtin));

      return (distinctItems.size() > 1);
    }
    return false;
  }

  public static DeliveryDocumentLine getDeliveryDocumentLine(Instruction instruction) {
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);
    if (Objects.nonNull(deliveryDocumentLine)) {
      return deliveryDocumentLine;
    } else {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(
              ReceivingException.PO_POLINE_NOT_FOUND,
              instruction.getGtin(),
              instruction.getDeliveryNumber()));
    }
  }

  public static String getSSCCFromInstruction(Instruction instruction) {

    DeliveryDocumentLine deliveryDocumentLine = getDeliveryDocumentLine(instruction);
    return StringUtils.isBlank(deliveryDocumentLine.getPalletSSCC())
        ? deliveryDocumentLine.getPackSSCC()
        : deliveryDocumentLine.getPalletSSCC();
  }

  public static Pair<Boolean, Integer> isPartialCaseAndReportedQty(
      List<ManufactureDetail> manufactureDetails, Integer vendorPack, Integer warehousePack) {
    int vendorCaseCount = 0;
    Integer eachesCount = 0;
    for (ManufactureDetail manufactureDetail : manufactureDetails) {
      eachesCount +=
          ReceivingUtils.conversionToEaches(
              manufactureDetail.getQty(),
              manufactureDetail.getReportedUom(),
              vendorPack,
              warehousePack);
    }
    vendorCaseCount =
        ReceivingUtils.conversionToVendorPack(
            eachesCount, ReceivingConstants.Uom.EACHES, vendorPack, warehousePack);
    Boolean isPartialCase = vendorCaseCount <= 0;
    return new Pair<>(isPartialCase, eachesCount);
  }

  public static boolean isPartialCase(
      List<ManufactureDetail> manufactureDetails, Integer vendorPack, Integer warehousePack) {
    int vendorCaseCount = 0;
    for (ManufactureDetail manufactureDetail : manufactureDetails) {
      vendorCaseCount +=
          ReceivingUtils.conversionToVendorPack(
              manufactureDetail.getQty(),
              manufactureDetail.getReportedUom(),
              vendorPack,
              warehousePack);
    }
    return vendorCaseCount <= 0;
  }

  public static boolean is2DScanInstructionRequest(List<ScannedData> scannedDataList) {
    Map<String, ScannedData> scannedDataMap = scannedDataMap(scannedDataList);
    return Objects.nonNull(scannedDataMap.get(ApplicationIdentifier.LOT.getKey()));
  }

  public static boolean isParentContainer(Container container) {
    return Objects.isNull(container.getParentTrackingId());
  }

  public static int getProjectedReceivedQty(
      DeliveryDocumentLine deliveryDocumentLine, long totalReceiptQtyByDeliveryPoPoLine) {

    // min((poQty - receiptQty), shippedQty)
    int poQty = deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    int receiptQty = (int) totalReceiptQtyByDeliveryPoPoLine;
    int shippedQty =
        ReceivingUtils.conversionToVendorPack(
            deliveryDocumentLine.getShippedQty(),
            deliveryDocumentLine.getShippedQtyUom(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());

    return Math.min((poQty - receiptQty), shippedQty);
  }

  public static int getProjectedReceivedQtyInEaches(
      DeliveryDocumentLine deliveryDocumentLine, long totalReceiptQtyByDeliveryPoPoLineInEaches) {

    // min((poQty - receiptQty), shippedQty)
    int poQtyInEaches =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    int receiptQtyInEaches = (int) totalReceiptQtyByDeliveryPoPoLineInEaches;
    int shippedQtyInEaches =
        ReceivingUtils.conversionToEaches(
            deliveryDocumentLine.getShippedQty(),
            deliveryDocumentLine.getShippedQtyUom(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());

    return Math.min((poQtyInEaches - receiptQtyInEaches), shippedQtyInEaches);
  }

  public static int deriveProjectedReceiveQtyInEaches(
      DeliveryDocumentLine deliveryDocumentLine,
      long totalReceiptQtyByDeliveryPoPoLineInEaches,
      int selectedShipmentReceivedQty) {

    // min((poQty - receiptQty), shippedQty)
    int poQtyInEaches =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    int receiptQtyInEaches = (int) totalReceiptQtyByDeliveryPoPoLineInEaches;
    int shippedQtyInEaches =
        ReceivingUtils.conversionToEaches(
            deliveryDocumentLine.getShippedQty(),
            deliveryDocumentLine.getShippedQtyUom(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());

    return Math.min(
        (poQtyInEaches - receiptQtyInEaches), (shippedQtyInEaches - selectedShipmentReceivedQty));
  }

  public static int deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow(
      DeliveryDocumentLine deliveryDocumentLine,
      long totalReceiptQtyByDeliveryPoPoLineInEaches,
      int attpQtyInEaches) {

    // min((poQty - receiptQty), shippedQty)
    int poQtyInEaches =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    int receiptQtyInEaches = (int) totalReceiptQtyByDeliveryPoPoLineInEaches;
    // todo rethink the logic
    return Math.min((poQtyInEaches - receiptQtyInEaches), attpQtyInEaches);
  }

  public static int getProjectedReceivedQtyForProblem(
      DeliveryDocumentLine deliveryDocumentLine,
      long totalReceiptQtyByDeliveryPoPoLine,
      int problemQuantity) {

    int minProjReceivedQtyBtwPoAndShipment =
        getProjectedReceivedQty(deliveryDocumentLine, totalReceiptQtyByDeliveryPoPoLine);
    return Math.min(minProjReceivedQtyBtwPoAndShipment, problemQuantity);
  }

  public static int getProjectedReceivedQtyInEaForProblem(
      DeliveryDocumentLine deliveryDocumentLine,
      long totalReceiptQtyByDeliveryPoPoLine,
      int problemQuantity) {

    int minProjReceivedQtyBtwPoAndShipment =
        getProjectedReceivedQtyInEaches(deliveryDocumentLine, totalReceiptQtyByDeliveryPoPoLine);
    return Math.min(minProjReceivedQtyBtwPoAndShipment, problemQuantity);
  }

  public static void validateScannedDataForUpcAndLotNumber(
      Map<String, ScannedData> scannedDataMap) {
    if (MapUtils.isEmpty(scannedDataMap)) {

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA);
    }

    ScannedData gtinScannedData = scannedDataMap.get(ReceivingConstants.KEY_GTIN);
    if (Objects.isNull(gtinScannedData) || StringUtils.isBlank(gtinScannedData.getValue())) {

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA_GTIN, RxConstants.INVALID_SCANNED_DATA_GTIN);
    }

    ScannedData lotScannedData = scannedDataMap.get(ReceivingConstants.KEY_LOT);
    if (Objects.isNull(lotScannedData) || StringUtils.isBlank(lotScannedData.getValue())) {

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA_LOT, RxConstants.INVALID_SCANNED_DATA_LOT);
    }

    ScannedData keyScannedData = scannedDataMap.get(ReceivingConstants.KEY_SERIAL);
    if (Objects.isNull(keyScannedData)
        || StringUtils.isBlank(keyScannedData.getValue())
        || keyScannedData.getValue().length() > 20) {

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA_SERIAL, RxConstants.INVALID_SCANNED_DATA_SERIAL);
    }

    ScannedData expDateScannedData = scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE);
    if (Objects.isNull(expDateScannedData) || StringUtils.isBlank(expDateScannedData.getValue())) {

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA_EXPIRY_DATE,
          RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
    }
  }

  public static void checkIfContainerIsCloseDated(
      Map<String, ScannedData> scannedDataMap,
      int closeDateLimitDays,
      String problemTagId,
      FitProblemTagResponse problemLabelByProblemTagId) {
    String problemType = "";
    if (Objects.nonNull(problemLabelByProblemTagId)
        && Objects.nonNull(problemLabelByProblemTagId.getIssue())) {
      problemType = problemLabelByProblemTagId.getIssue().getType();
    }
    if (StringUtils.isBlank(problemTagId) || !FIXIT_ISSUE_TYPE_DI.equalsIgnoreCase(problemType)) {
      if (checkIfSellByDateIsCloseDated(scannedDataMap, closeDateLimitDays)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.CLOSE_DATED_ITEM, RxConstants.CLOSE_DATED_ITEM);
      }
    } else {
      if (checkIfSellByDateIsCloseDated(scannedDataMap, 0)) { // If already expired date.
        throw new ReceivingBadDataException(ExceptionCodes.EXPIRED_ITEM, RxConstants.EXPIRED_ITEM);
      }
      LOG.info(
          "Close Dated item is being received for Problem Ticket {}, problemType {}",
          problemTagId,
          problemType);
    }
  }

  private static boolean checkIfSellByDateIsCloseDated(
      Map<String, ScannedData> scannedDataMap, int closeDateLimitDays) {
    Date thresholdDate =
        Date.from(
            Instant.now().plus(closeDateLimitDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    try {
      Date expDate =
          DateUtils.parseDate(
              scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE).getValue(),
              ReceivingConstants.EXPIRY_DATE_FORMAT);
      return expDate.before(thresholdDate);
    } catch (ParseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
    }
  }

  public static int findEachQtySummary(Instruction instruction) {
    int receivedQtyInEA = 0;
    String trackingId = instruction.getContainer().getTrackingId();
    List<ContainerDetails> childContainers = instruction.getChildContainers();
    if (!CollectionUtils.isEmpty(childContainers)) {
      for (ContainerDetails containerDetails : childContainers) {
        if (trackingId.equals(containerDetails.getParentTrackingId())) {
          receivedQtyInEA += containerDetails.getContents().get(0).getQty();
        }
      }
    }
    return receivedQtyInEA;
  }

  public static void verify2DBarcodeLotWithShipmentLot(
      boolean is2dBarcodeRequest,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      Map<String, ScannedData> scannedDataMap,
      boolean isVendorWholesaler) {
    if (is2dBarcodeRequest && !isVendorWholesaler) {
      List<ManufactureDetail> manufactureDetailsList =
          emptyIfNull(deliveryDocumentLine.getManufactureDetails());
      Set<String> lotDetails =
          manufactureDetailsList
              .stream()
              .map(ManufactureDetail::getLot)
              .map(StringUtils::upperCase)
              .collect(Collectors.toSet());
      String scannedDataLot =
          StringUtils.upperCase(scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue());
      if (!lotDetails.contains(scannedDataLot)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH, RxConstants.SCANNED_DETAILS_DO_NOT_MATCH);
      }
    }
  }

  public static boolean isUpcReceivingInstruction(String instructionCode) {
    return instructionCode.equalsIgnoreCase(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType())
        || instructionCode.equalsIgnoreCase(
            RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType());
  }

  public static boolean isInstructionRequestPartial(InstructionRequest instructionRequest) {
    Optional<RxReceivingType> rxReceivingTypeOptional =
        RxReceivingType.fromString(instructionRequest.getReceivingType());
    return rxReceivingTypeOptional.isPresent()
        && rxReceivingTypeOptional.get().isPartialInstructionGroup();
  }

  public static boolean isPartialInstruction(String instructionCode) {
    return partialInstructionCodes.contains(instructionCode);
  }

  public static Instruction convertQuantityToVnpkOrWhpkBasedOnInstructionType(
      Instruction existingInstruction) {
    DeliveryDocument deliveryDocument =
        new Gson().fromJson(existingInstruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (RxUtils.isPartialInstruction(existingInstruction.getInstructionCode())) {
      existingInstruction.setProjectedReceiveQty(
          ReceivingUtils.conversionToWareHousePack(
              existingInstruction.getProjectedReceiveQty(),
              existingInstruction.getProjectedReceiveQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      existingInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.WHPK);

      existingInstruction.setReceivedQuantity(
          ReceivingUtils.conversionToWareHousePack(
              existingInstruction.getReceivedQuantity(),
              existingInstruction.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      existingInstruction.setReceivedQuantityUOM(ReceivingConstants.Uom.WHPK);

    } else {
      existingInstruction.setProjectedReceiveQty(
          ReceivingUtils.conversionToVendorPack(
              existingInstruction.getProjectedReceiveQty(),
              existingInstruction.getProjectedReceiveQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      existingInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
      existingInstruction.setReceivedQuantity(
          ReceivingUtils.conversionToVendorPack(
              existingInstruction.getReceivedQuantity(),
              existingInstruction.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      existingInstruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    }
    return existingInstruction;
  }

  public static List<InstructionSummary> updateProjectedQuantyInInstructionSummary(
      List<InstructionSummary> instructionSummaryList) {
    DeliveryDocument deliveryDocument;
    DeliveryDocumentLine deliveryDocumentLine;
    for (InstructionSummary instructionSummary : instructionSummaryList) {
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
          instructionSummary.getInstructionSet())) {
        for (InstructionSummary instructionlinkedToInstructionSet :
            instructionSummary.getInstructionSet()) {
          deliveryDocument =
              new Gson()
                  .fromJson(
                      instructionlinkedToInstructionSet.getDeliveryDocument(),
                      DeliveryDocument.class);
          deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
          updateProjectedAndReceivedQuantity(
              instructionlinkedToInstructionSet, deliveryDocumentLine);
        }
      } else {
        deliveryDocument =
            new Gson().fromJson(instructionSummary.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        updateProjectedAndReceivedQuantity(instructionSummary, deliveryDocumentLine);
      }
    }
    return instructionSummaryList;
  }

  private static void updateProjectedAndReceivedQuantity(
      InstructionSummary instructionSummary, DeliveryDocumentLine deliveryDocumentLine) {
    if (RxUtils.isPartialInstruction(instructionSummary.getInstructionCode())) {
      instructionSummary.setProjectedReceiveQty(
          ReceivingUtils.conversionToWareHousePack(
              instructionSummary.getProjectedReceiveQty(),
              instructionSummary.getProjectedReceiveQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      instructionSummary.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.WHPK);

      instructionSummary.setReceivedQuantity(
          ReceivingUtils.conversionToWareHousePack(
              instructionSummary.getReceivedQuantity(),
              instructionSummary.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      instructionSummary.setReceivedQuantityUOM(ReceivingConstants.Uom.WHPK);

    } else {
      instructionSummary.setProjectedReceiveQty(
          ReceivingUtils.conversionToVendorPack(
              instructionSummary.getProjectedReceiveQty(),
              instructionSummary.getProjectedReceiveQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      instructionSummary.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
      instructionSummary.setReceivedQuantity(
          ReceivingUtils.conversionToVendorPack(
              instructionSummary.getReceivedQuantity(),
              instructionSummary.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      instructionSummary.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    }
  }

  public static boolean isUserEligibleToReceive(String requestUserId, Instruction instruction) {
    if (org.apache.commons.lang.StringUtils.isNotBlank(instruction.getLastChangeUserId())) {
      return requestUserId.equals(instruction.getLastChangeUserId());
    } else {
      return requestUserId.equals(instruction.getCreateUserId());
    }
  }

  public static boolean isSplitPalletInstructionRequest(InstructionRequest instructionRequest) {
    Optional<RxReceivingType> rxReceivingTypeOptional =
        RxReceivingType.fromString(instructionRequest.getReceivingType());
    return (rxReceivingTypeOptional.isPresent()
        && rxReceivingTypeOptional.get().isSplitPalletGroup());
  }

  public static Set<String> getAllAvailableGtins(Instruction instruction) {
    Set<String> gtins = new HashSet<>();
    DeliveryDocumentLine deliveryDocumentLine = getDeliveryDocumentLine(instruction);
    if (Objects.nonNull(deliveryDocumentLine)) {
      gtins.add(deliveryDocumentLine.getGtin());
      gtins.add(deliveryDocumentLine.getItemUpc());
      gtins.add(deliveryDocumentLine.getCaseUpc());
      List<GtinHierarchy> gtinHierarchyList = deliveryDocumentLine.getGtinHierarchy();
      if (!CollectionUtils.isEmpty(gtinHierarchyList)) {
        for (GtinHierarchy gtinHierarchy : gtinHierarchyList) {
          if (StringUtils.isNotBlank(gtinHierarchy.getGtin())) {
            gtins.add(gtinHierarchy.getGtin());
          }
        }
      }
    }
    return gtins;
  }

  public static List<Instruction> filterInstructionMatchingGtin(
      List<Instruction> existingInstructionsList, String upcNumber) {
    if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(existingInstructionsList)) {
      for (Instruction instruction : existingInstructionsList) {
        if (RxUtils.getAllAvailableGtins(instruction).contains(upcNumber)) {
          return Arrays.asList(instruction);
        }
      }
    }
    return null;
  }

  public static Map<String, ShipmentDetails> convertToMap(List<ShipmentDetails> shipmentDetails) {
    if (CollectionUtils.isEmpty(shipmentDetails)) {
      return Collections.emptyMap();
    }
    return shipmentDetails
        .stream()
        .collect(Collectors.toMap(ShipmentDetails::getInboundShipmentDocId, Function.identity()));
  }

  public static Receipt resetRecieptQty(Container container) {
    ContainerItem containerItem = container.getContainerItems().get(0);

    int finalVnpkQty =
        ReceivingUtils.conversionToVendorPack(
            containerItem.getQuantity(),
            containerItem.getQuantityUOM(),
            containerItem.getVnpkQty(),
            containerItem.getWhpkQty());
    // In Rx we are considering < less than a case as 1 case
    if (finalVnpkQty == 0) {
      finalVnpkQty = -1;
    } else {
      // Since this is cancel label, we need to store receipt value in -ve
      finalVnpkQty = finalVnpkQty * -1;
    }

    // Since this is cancel label, we need to store receipt value in -ve
    int finalEachQty = containerItem.getQuantity() * -1;

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
    receipt.setQuantity(finalVnpkQty);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setEachQty(finalEachQty);
    receipt.setCreateUserId(container.getCreateUser());

    return receipt;
  }

  public static boolean isVendorWholesaler(
      DeliveryDocument deliveryDocument, String vendorIds, boolean isWholesalerLotCheckEnabled) {
    boolean isVendorWholesaler = false;
    if (isWholesalerLotCheckEnabled) {
      String[] wholesalerVendors = org.apache.commons.lang.StringUtils.split(vendorIds, ",");
      isVendorWholesaler =
          Arrays.asList(wholesalerVendors)
              .contains(String.valueOf(deliveryDocument.getVendorNumber()));
    }
    return isVendorWholesaler;
  }

  public static ManufactureDetail convertScannedDataToManufactureDetail(
      Map<String, ScannedData> scannedDataMap) {

    if (MapUtils.isEmpty(scannedDataMap)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA);
    }
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    ScannedData gtinScannedData = scannedDataMap.get(ReceivingConstants.KEY_GTIN);
    if (null != gtinScannedData && StringUtils.isNotBlank(gtinScannedData.getValue())) {
      manufactureDetail.setGtin(gtinScannedData.getValue());
    }

    ScannedData lotScannedData = scannedDataMap.get(ReceivingConstants.KEY_LOT);
    if (null != lotScannedData && StringUtils.isNotBlank(lotScannedData.getValue())) {
      manufactureDetail.setLot(lotScannedData.getValue());
    }

    ScannedData keyScannedData = scannedDataMap.get(ReceivingConstants.KEY_SERIAL);
    if (null != keyScannedData && StringUtils.isNotBlank(keyScannedData.getValue())) {
      manufactureDetail.setSerial(keyScannedData.getValue());
    }

    ScannedData expDateScannedData = scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE);
    if (null != expDateScannedData && StringUtils.isNotBlank(expDateScannedData.getValue())) {
      try {
        Date expDate =
            DateUtils.parseDate(
                expDateScannedData.getValue(), ReceivingConstants.EXPIRY_DATE_FORMAT);
        manufactureDetail.setExpiryDate(
            new SimpleDateFormat(ReceivingConstants.SIMPLE_DATE).format(expDate));
      } catch (ParseException e) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
      }
    }
    return manufactureDetail;
  }

  public static void verifyScanned2DWithSerializedInfo(
      DeliveryDocumentLine deliveryDocumentLine, Map<String, ScannedData> scannedDataMap) {

    ItemData additionalInfoDetails = deliveryDocumentLine.getAdditionalInfo();
    boolean isValidationSuccess = false;
    if (isEpcisEnabledVendor(additionalInfoDetails)) {
      ManufactureDetail scannedDetails =
          RxUtils.convertScannedDataToManufactureDetail(scannedDataMap);
      List<ManufactureDetail> serializedCaseInfoList = additionalInfoDetails.getSerializedInfo();

      for (ManufactureDetail serializedCaseInfo : serializedCaseInfoList) {
        if (serializedCaseInfo.getGtin().equalsIgnoreCase(scannedDetails.getGtin())
            && serializedCaseInfo.getExpiryDate().equalsIgnoreCase(scannedDetails.getExpiryDate())
            && serializedCaseInfo.getLot().equalsIgnoreCase(scannedDetails.getLot())
            && serializedCaseInfo.getSerial().equalsIgnoreCase(scannedDetails.getSerial())) {

          isValidationSuccess = true;
          break;
        }
      }
      if (!isValidationSuccess) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH, RxConstants.SCANNED_DETAILS_DO_NOT_MATCH);
      }
    }
  }

  public static boolean isEpcisEnabledVendor(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine)
        && Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
        && Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getIsEpcisEnabledVendor())
        && deliveryDocumentLine.getAdditionalInfo().getIsEpcisEnabledVendor().equals(Boolean.TRUE);
  }

  public static boolean isEpcisEnabledVendor(ItemData itemData) {
    return Objects.nonNull(itemData)
        && Objects.nonNull(itemData.getIsEpcisEnabledVendor())
        && itemData.getIsEpcisEnabledVendor().equals(Boolean.TRUE);
  }

  public static ManufactureDetail getManufactureDetailByPackItem(Item packItem, Pack packInfo) {
    if (Objects.isNull(packItem.getManufactureDetails())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_SERIALIZE_INFO,
          RxConstants.NO_MANUFACTURE_DETAILS_EPCIS);
    }

    ManufactureDetail unitSerializedData = new ManufactureDetail();
    unitSerializedData.setExpiryDate(packItem.getManufactureDetails().get(0).getExpirationDate());
    unitSerializedData.setGtin(packItem.getGtin());
    unitSerializedData.setLot(packItem.getManufactureDetails().get(0).getLotNumber());
    unitSerializedData.setReportedUom(packItem.getUom());
    unitSerializedData.setTrackingStatus(packItem.getTrackingStatus());
    unitSerializedData.setSerial(packItem.getSerial());
    if (null != packInfo) {
      if (StringUtils.isNotBlank(packInfo.getDocumentPackId())) {
        unitSerializedData.setDocumentPackId(packInfo.getDocumentPackId());
      }
      if (StringUtils.isNotBlank(packInfo.getDocumentId())) {
        unitSerializedData.setDocumentId(packInfo.getDocumentId());
      }
      if (StringUtils.isNotBlank(packInfo.getShipmentNumber())) {
        unitSerializedData.setShipmentNumber(packInfo.getShipmentNumber());
      }
    }
    return unitSerializedData;
  }

  public static ManufactureDetail getManufactureDetailByPack(Pack pack) {
    ManufactureDetail packSerializedData = new ManufactureDetail();
    if (StringUtils.isAllBlank(
        pack.getExpiryDate(),
        pack.getGtin(),
        pack.getLotNumber(),
        pack.getSerial(),
        pack.getPackNumber())) {
      return null;
    }
    if (!StringUtils.isAnyBlank(
        pack.getExpiryDate(), pack.getGtin(), pack.getLotNumber(), pack.getSerial())) {
      packSerializedData.setExpiryDate(StringUtils.trim(pack.getExpiryDate()));
      packSerializedData.setGtin(StringUtils.trim(pack.getGtin()));
      packSerializedData.setLot(StringUtils.trim(pack.getLotNumber()));
      packSerializedData.setSerial(StringUtils.trim(pack.getSerial()));
    }
    if (!StringUtils.isAnyBlank(pack.getPackNumber())) {
      packSerializedData.setSscc(StringUtils.trim(pack.getPackNumber()));
    }
    packSerializedData.setTrackingStatus(StringUtils.trim(pack.getTrackingStatus()));
    packSerializedData.setReportedUom(
        StringUtils.equalsAnyIgnoreCase(
                pack.getUom(), ReceivingConstants.Uom.VNPK, ReceivingConstants.Uom.CA)
            ? ReceivingConstants.Uom.VNPK
            : StringUtils.trim(pack.getUom()));
    packSerializedData.setDocumentPackId(pack.getDocumentPackId());
    packSerializedData.setDocumentId(pack.getDocumentId());
    packSerializedData.setShipmentNumber(pack.getShipmentNumber());
    return packSerializedData;
  }

  public static OutboxEvent buildOutboxEvent(
      Map<String, Object> headers,
      String body,
      String eventIdentifier,
      MetaData metadata,
      String publisherPolicyId,
      Instant executionTs) {
    Message message = Message.builder().headers(headers).body(body).build();
    PayloadRef payloadRef = PayloadRef.builder().data(message).build();
    return OutboxEvent.builder()
        .eventIdentifier(eventIdentifier)
        .payloadRef(payloadRef)
        .metaData(metadata)
        .publisherPolicyId(publisherPolicyId)
        .executionTs(executionTs)
        .build();
  }

  public static boolean isASNReceivingOverrideEligible(
      FitProblemTagResponse fitProblemTagResponse) {
    Resolution activeResolution = getActiveResolution(fitProblemTagResponse);
    return Objects.nonNull(activeResolution)
        && ProblemResolutionType.RECEIVE_USING_ASN_FLOW
            .toString()
            .equalsIgnoreCase(activeResolution.getType());
  }

  public static Resolution getActiveResolution(FitProblemTagResponse fitProblemTagResponse) {
    if (Objects.nonNull(fitProblemTagResponse)
        && !CollectionUtils.isEmpty(fitProblemTagResponse.getResolutions())) {
      List<Resolution> allResolutions = fitProblemTagResponse.getResolutions();
      for (Resolution resolution : allResolutions) {
        if (RxConstants.OPEN.equals(resolution.getState())
            || RxConstants.PARTIAL.equals(resolution.getState())) return resolution;
      }
    }
    return null;
  }

  public static void checkReceivingStatusReceivable(String receivingStatus) {
    if (!RxConstants.OPEN_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(receivingStatus)
        && !RxConstants.PARTIALLY_RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
            receivingStatus)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.BARCODE_NOT_IN_RECEIVABLE_STATUS,
          String.format(
              ExceptionDescriptionConstants.BARCODE_NOT_IN_RECEIVABLE_STATUS, receivingStatus));
    }
  }

  /**
   * utility to check whether an item handling code belongs to x blocked list
   *
   * @param deliveryDocumentLine delivery document line
   * @return is item x blocked
   */
  public static boolean isItemXBlocked(DeliveryDocumentLine deliveryDocumentLine) {
    List<String> xBlockHandlingCodes =
        Arrays.asList(ReceivingConstants.X_BLOCK_ITEM_HANDLING_CODES);
    return xBlockHandlingCodes.contains(deliveryDocumentLine.getAdditionalInfo().getHandlingCode())
        || xBlockHandlingCodes.contains(deliveryDocumentLine.getHandlingCode());
  }

  /**
   * This method determines whether the given item is controlledItem
   * @param deliveryDocumentLine delivery document line
   * @return boolean -true or false
   */
  public static boolean isControlledSubstance(DeliveryDocumentLine deliveryDocumentLine) {
    return deliveryDocumentLine.getAdditionalInfo().getIsControlledSubstance() ||
            deliveryDocumentLine.getIsControlledSubstance();
  }

}
