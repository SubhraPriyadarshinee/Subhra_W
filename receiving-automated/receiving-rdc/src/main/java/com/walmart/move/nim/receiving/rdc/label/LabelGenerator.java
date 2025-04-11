package com.walmart.move.nim.receiving.rdc.label;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.SLOTTING_DA_RECEIVING_METHOD;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionResponse;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.constants.PoCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;

public class LabelGenerator {

  private LabelGenerator() {}

  public static Pair<PrintLabelData, Map<String, PrintLabelRequest>> generateDockTagLabels(
      CreateDockTagRequest createDockTagRequest,
      List<DockTag> dockTagData,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      boolean isNewDockTagLabelFormatEnabled) {
    Map<String, PrintLabelRequest> dockTagPrintRequestMap = new HashMap<>();
    List<PrintLabelRequest> printRequests = new ArrayList<>();
    String finalFormatName =
        isNewDockTagLabelFormatEnabled
            ? LabelFormat.NEW_DOCKTAG.getFormat()
            : LabelFormat.DOCK_TAG.getFormat();

    dockTagData.forEach(
        dockTag -> {
          List<LabelData> datumList =
              getDockTagDatum(
                  createDockTagRequest,
                  dockTag,
                  httpHeaders,
                  zonedDateTime,
                  isNewDockTagLabelFormatEnabled);
          PrintLabelRequest printLabelRequest = new PrintLabelRequest();
          printLabelRequest.setData(datumList);
          printLabelRequest.setLabelIdentifier(dockTag.getDockTagId());
          printLabelRequest.setFormatName(finalFormatName);
          printLabelRequest.setTtlInHours(RdcConstants.LBL_TTL);
          printRequests.add(printLabelRequest);
          dockTagPrintRequestMap.put(dockTag.getDockTagId(), printLabelRequest);
        });

    PrintLabelData printLabelData = new PrintLabelData();
    printLabelData.setClientId(ReceivingConstants.ATLAS_RECEIVING);
    printLabelData.setPrintRequests(printRequests);
    printLabelData.setHeaders(ContainerUtils.getPrintRequestHeaders(httpHeaders));

    return new Pair<>(printLabelData, dockTagPrintRequestMap);
  }

  private static List<LabelData> getDockTagDatum(
      CreateDockTagRequest request,
      DockTag dockTagData,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      boolean isNewDockTagLabelFormatEnabled) {
    List<LabelData> data = new ArrayList<>();
    data.add(
        new LabelData(LabelConstants.LBL_CARRIER, RdcUtils.getStringValue(request.getCarrier())));
    data.add(new LabelData(LabelConstants.LBL_BAR, dockTagData.getDockTagId()));
    data.add(new LabelData(LabelConstants.LBL_DOORNBR, dockTagData.getScannedLocation()));
    data.add(
        new LabelData(
            LabelConstants.LBL_MANIFEST, String.valueOf(dockTagData.getDeliveryNumber())));
    data.add(
        new LabelData(
            LabelConstants.LBL_TRAILER, RdcUtils.getStringValue(request.getTrailerNumber())));
    data.add(
        new LabelData(
            LabelConstants.LBL_FULLUSERID,
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY)));
    data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));

    if (isNewDockTagLabelFormatEnabled) {
      String deliveryTypeCode =
          StringUtils.isNotBlank(request.getDeliveryTypeCode())
              ? request.getDeliveryTypeCode().trim().length()
                      > RdcConstants.DELIVERY_TYPE_CODE_LENGTH
                  ? request
                      .getDeliveryTypeCode()
                      .trim()
                      .substring(
                          RdcConstants.DOCKTAG_LABEL_INDEX_START_POSITION,
                          RdcConstants.DOCKTAG_LABEL_INDEX_END_POSITION)
                  : request.getDeliveryTypeCode().trim()
              : StringUtils.EMPTY;
      data.add(new LabelData(LabelConstants.LBL_DELIVERY_TYPE, deliveryTypeCode));

      String freightType =
          StringUtils.isNotBlank(request.getFreightType())
              ? request.getFreightType().trim().length() > RdcConstants.FREIGHT_TYPE_CODE_LENGTH
                  ? request
                      .getFreightType()
                      .trim()
                      .substring(
                          RdcConstants.DOCKTAG_LABEL_INDEX_START_POSITION,
                          RdcConstants.DOCKTAG_LABEL_INDEX_END_POSITION)
                  : request.getFreightType().trim()
              : StringUtils.EMPTY;
      data.add(new LabelData(LabelConstants.LBL_FREIGHT_TYPE, freightType));

      data.add(
          new LabelData(LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDate(zonedDateTime)));
      data.add(
          new LabelData(LabelConstants.LBL_LBLTIME, RdcUtils.getLabelFormatTime(zonedDateTime)));
    } else {
      data.add(
          new LabelData(
              LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
    }

    // Added for reprint dashboard to SCT
    data.add(new LabelData(LabelConstants.LBL_CONTAINER_ID, dockTagData.getDockTagId()));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));

    return data;
  }

  public static Map<String, Object> generatePalletLabels(
      DeliveryDocumentLine deliveryDocumentLine,
      Integer receivedQuantity,
      CommonLabelDetails commonLabelDetails,
      Long printJobId,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      boolean isSplitPallet,
      Long deliveryNumber,
      LabelFormat labelFormat) {

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    printLabelRequests.add(getSSTKTimestampLabel(commonLabelDetails, printJobId, zonedDateTime));
    printLabelRequests.add(
        getSSTKPalletLabel(
            deliveryDocumentLine,
            commonLabelDetails,
            receivedQuantity,
            printJobId,
            userId,
            zonedDateTime,
            isSplitPallet,
            deliveryNumber,
            labelFormat));

    PrintLabelRequest duplicatePrintLabelRequest = duplicateSSTKPalletLabel(printLabelRequests);
    if (Objects.nonNull(duplicatePrintLabelRequest)) {
      printLabelRequests.add(duplicatePrintLabelRequest);
    }

    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    return printJob;
  }

  public static Map<String, Object> generateDAPalletLabels(
      DeliveryDocumentLine deliveryDocumentLine,
      Map<String, Integer> trackingIdQuantityMap,
      List<ReceivedContainer> receivedContainers,
      Map<ReceivedContainer, PrintJob> receivedContainerPrintJobMap,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      LabelFormat labelFormat) {

    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    Map<String, Object> printJob = new HashMap<>();
    for (ReceivedContainer receivedContainer : receivedContainers) {
      int quantity = trackingIdQuantityMap.get(receivedContainer.getLabelTrackingId());
      Long printJobId = receivedContainerPrintJobMap.get(receivedContainer).getId();
      CommonLabelDetails commonLabelDetails =
          CommonLabelDetails.builder()
              .labelTrackingId(receivedContainer.getLabelTrackingId())
              .slot(receivedContainer.getDestinations().get(0).getSlot())
              .slotSize(receivedContainer.getDestinations().get(0).getSlot_size())
              .receiver(receivedContainer.getReceiver())
              .build();
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      Long deliveryNumber =
          receivedContainerPrintJobMap.containsKey(receivedContainer)
              ? receivedContainerPrintJobMap.get(receivedContainer).getDeliveryNumber()
              : null;
      List<PrintLabelRequest> containerPrintLabelRequest = new ArrayList<>();
      containerPrintLabelRequest.add(
          getSSTKTimestampLabel(commonLabelDetails, printJobId, zonedDateTime));
      PrintLabelRequest sSTKPalletLabel =
          getSSTKPalletLabel(
              deliveryDocumentLine,
              commonLabelDetails,
              quantity,
              printJobId,
              userId,
              zonedDateTime,
              false,
              deliveryNumber,
              labelFormat);
      containerPrintLabelRequest.add(sSTKPalletLabel);
      // Duplicate PrintLabelRequest.
      containerPrintLabelRequest.add(sSTKPalletLabel);
      printLabelRequests.addAll(containerPrintLabelRequest);
    }
    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    return printJob;
  }

  private static PrintLabelRequest getSSTKPalletLabel(
      DeliveryDocumentLine deliveryDocumentLine,
      CommonLabelDetails commonLabelDetails,
      Integer receivedQuantity,
      Long printJobId,
      String userId,
      ZonedDateTime zonedDateTime,
      boolean isSplitPallet,
      Long deliveryNumber,
      LabelFormat labelFormat) {
    List<LabelData> data = new ArrayList<>();
    String slot =
        Objects.nonNull(commonLabelDetails.getSlot())
            ? commonLabelDetails.getSlot()
            : StringUtils.EMPTY;
    String slotSize =
        Objects.nonNull(commonLabelDetails.getSlotSize())
                && !ReceivingConstants.ZERO_QTY.equals(commonLabelDetails.getSlotSize())
            ? RdcUtils.getStringValue(commonLabelDetails.getSlotSize())
            : StringUtils.EMPTY;
    String upc =
        Objects.nonNull(deliveryDocumentLine.getItemUpc())
            ? deliveryDocumentLine.getItemUpc()
            : deliveryDocumentLine.getCaseUpc();
    String receiver =
        Objects.nonNull(commonLabelDetails.getReceiver())
            ? RdcUtils.getStringValue(commonLabelDetails.getReceiver())
            : StringUtils.EMPTY;

    data.add(new LabelData(LabelConstants.LBL_BAR, commonLabelDetails.getLabelTrackingId()));
    data.add(new LabelData(LabelConstants.LBL_SLOT, slot));
    data.add(new LabelData(LabelConstants.LBL_PICK, RdcUtils.getStringValue(receivedQuantity)));
    data.add(
        new LabelData(
            LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
    data.add(
        new LabelData(
            LabelConstants.LBL_VNPK,
            RdcUtils.getStringValue(deliveryDocumentLine.getVendorPack())));
    data.add(
        new LabelData(
            LabelConstants.LBL_ITEM, RdcUtils.getStringValue(deliveryDocumentLine.getItemNbr())));
    data.add(
        new LabelData(
            LabelConstants.LBL_POLINE,
            RdcUtils.getStringValue(deliveryDocumentLine.getPurchaseReferenceLineNumber())));
    data.add(new LabelData(LabelConstants.LBL_RCVR, receiver));
    data.add(
        new LabelData(
            LabelConstants.LBL_WHPK,
            RdcUtils.getStringValue(deliveryDocumentLine.getWarehousePack())));
    data.add(new LabelData(LabelConstants.LBL_UPCBAR, upc));
    data.add(new LabelData(LabelConstants.LBL_USERID, userId));
    data.add(
        new LabelData(
            LabelConstants.LBL_TI, RdcUtils.getStringValue(deliveryDocumentLine.getPalletTie())));
    data.add(
        new LabelData(
            LabelConstants.LBL_HI, RdcUtils.getStringValue(deliveryDocumentLine.getPalletHigh())));
    data.add(
        new LabelData(LabelConstants.LBL_VENDORID, deliveryDocumentLine.getVendorStockNumber()));
    data.add(new LabelData(LabelConstants.LBL_NUM, RdcConstants.PRINT_LABEL_NUM));
    data.add(new LabelData(LabelConstants.LBL_CNT, RdcConstants.PRINT_LABEL_CNT));

    // Added for reprint dashboard to SCT
    data.add(
        new LabelData(LabelConstants.LBL_CONTAINER_ID, commonLabelDetails.getLabelTrackingId()));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));

    /* print PARTIAL or SPLIT PALLET only for SSTK freights. For DA slotting it
    needs to be EMPTY */
    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
        deliveryDocumentLine.getPurchaseRefType())) {
      if (!isSplitPallet) {
        String partialPalletLabel =
            isPartialPalletOpted(deliveryDocumentLine, receivedQuantity)
                ? LabelConstants.LBL_PARTIAL
                : StringUtils.EMPTY;
        data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, partialPalletLabel));
      } else {
        data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, LabelConstants.LBL_SPLIT_PALLET));
      }
    } else {
      data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, StringUtils.EMPTY));
    }

    data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));
    data.add(new LabelData(LabelConstants.LBL_SLOTSIZE, slotSize));
    if (Objects.nonNull(deliveryNumber)) {
      data.add(new LabelData(LabelConstants.LBL_DELIVERY_NUMBER, String.valueOf(deliveryNumber)));
    }
    String freightType = getFreightTypeByChannelMethod(deliveryDocumentLine);
    if (StringUtils.isNotEmpty(freightType)) {
      data.add(new LabelData(LabelConstants.LBL_FREIGHT_TYPE, freightType));
    }

    return getPrintRequest(
        data,
        commonLabelDetails.getLabelTrackingId(),
        printJobId,
        labelFormat,
        RdcConstants.LBL_TTL);
  }

  private static PrintLabelRequest getSSTKTimestampLabel(
      CommonLabelDetails commonLabelDetails, Long printJobId, ZonedDateTime zonedDateTime) {
    List<LabelData> data = new ArrayList<>();
    data.add(
        new LabelData(
            LabelConstants.LBL_TIME, RdcUtils.getSSTKTimestampLabelFormatTime(zonedDateTime)));
    data.add(
        new LabelData(
            LabelConstants.LBL_DATE, RdcUtils.getSSTKTimestampLabelFormatDate(zonedDateTime)));
    data.add(
        new LabelData(
            LabelConstants.LBL_TIMECODE, RdcUtils.getSSTKTimestampLabelTimeCode(zonedDateTime)));
    data.add(new LabelData(LabelConstants.LBL_BAR, commonLabelDetails.getLabelTrackingId()));

    return getPrintRequest(
        data,
        String.join("-", "ts", commonLabelDetails.getLabelTrackingId()),
        printJobId,
        LabelFormat.SSTK_TIMESTAMP,
        RdcConstants.LBL_TTL);
  }

  private static PrintLabelRequest getPrintRequest(
      List<LabelData> data,
      String labelTrackingId,
      Long printJobId,
      LabelFormat labelFormat,
      int labelTtlHours) {
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setData(data);
    printLabelRequest.setLabelIdentifier(labelTrackingId);
    printLabelRequest.setPrintJobId(RdcUtils.getStringValue(printJobId));
    printLabelRequest.setTtlInHours(labelTtlHours);
    printLabelRequest.setFormatName(labelFormat.getFormat());
    return printLabelRequest;
  }

  private static boolean isPartialPalletOpted(
      DeliveryDocumentLine deliveryDocumentLine, Integer receivedQuantity) {
    int palletTi = deliveryDocumentLine.getPalletTie();
    int palletHi = deliveryDocumentLine.getPalletHigh();

    int palletCapacity = 0;
    if (palletTi > 0 && palletHi > 0) {
      palletCapacity = palletTi * palletHi;
    }
    return (receivedQuantity < palletCapacity);
  }

  public static PrintLabelRequest duplicateSSTKPalletLabel(List<PrintLabelRequest> printRequests) {
    Optional<PrintLabelRequest> printRequestOptional =
        printRequests
            .stream()
            .filter(
                print ->
                    print.getFormatName().equals(LabelFormat.LEGACY_SSTK.getFormat())
                        || print.getFormatName().equals(LabelFormat.ATLAS_RDC_PALLET.getFormat())
                        || print.getFormatName().equals(LabelFormat.ATLAS_RDC_SSTK.getFormat()))
            .findFirst();
    return printRequestOptional.orElse(null);
  }

  /**
   * This method generates DA Case label based on the container destination slot
   *
   * @param deliveryDocumentLine
   * @param receivedQty
   * @param receivedContainers
   * @param printJobId
   * @param httpHeaders
   * @param zonedDateTime
   * @return
   */
  public static Map<String, Object> generateDACaseLabel(
      DeliveryDocumentLine deliveryDocumentLine,
      Integer receivedQty,
      List<ReceivedContainer> receivedContainers,
      Long printJobId,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      boolean isMfcIndicatorEnabled) {
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    receivedContainers.forEach(
        receivedContainer -> {
          printLabelRequests.add(
              getDaCaseLabel(
                  deliveryDocumentLine,
                  receivedQty,
                  receivedContainer,
                  printJobId,
                  httpHeaders,
                  zonedDateTime,
                  isMfcIndicatorEnabled));
        });
    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    return printJob;
  }

  private static PrintLabelRequest getDaCaseLabel(
      DeliveryDocumentLine deliveryDocumentLine,
      Integer receivedQty,
      ReceivedContainer receivedContainer,
      Long printJobId,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime,
      boolean isMfcIndicatorEnabled) {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    List<LabelData> data = new ArrayList<>();
    String slot = null;
    String store = null;
    String storeZone = null;
    String aisle = null;
    LabelFormat labelFormat = null;
    boolean isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();

    if (!CollectionUtils.isEmpty(receivedContainer.getDestinations())) {
      Destination destination = receivedContainer.getDestinations().get(0);
      slot = destination.getSlot();
      storeZone = receivedContainer.getStorezone();
      aisle = receivedContainer.getAisle();
      labelFormat =
          isAtlasConvertedItem
              ? RdcConstants.ATLAS_DA_LABEL_FORMAT_MAP.get(receivedContainer.getLabelType())
              : RdcConstants.DA_LABEL_FORMAT_MAP.get(slot);

      boolean shippingLabel =
          LabelFormat.DA_STORE_FRIENDLY.getFormat().equals(labelFormat.getFormat())
              || LabelFormat.ATLAS_DA_STORE_FRIENDLY.getFormat().equals(labelFormat.getFormat());

      boolean isRoutingLabel =
          StringUtils.equals(storeZone, RdcConstants.ROUTING_LABEL_STORE_ZONE)
              && StringUtils.equals(aisle, RdcConstants.ROUTING_LABEL_AISLE);
      String departmentNumber =
          Objects.nonNull(receivedContainer.getDepartment())
              ? receivedContainer.getDepartment().toString()
              : deliveryDocumentLine.getDepartment();

      if (shippingLabel) {
        if (!isAtlasConvertedItem) {
          data.add(
              new LabelData(
                  LabelConstants.LBL_DC_CARTON,
                  RdcUtils.getStringValue(receivedContainer.getCarton())));
        }
        String divisionNumber =
            Objects.nonNull(receivedContainer.getDivision())
                ? String.format(
                    RdcConstants.LBL_DIVISION_MAX_LENGTH, receivedContainer.getDivision())
                : StringUtils.EMPTY;
        store =
            StringUtils.leftPad(destination.getStore(), RdcConstants.STORE_NUMBER_MAX_LENGTH, "0");
        if (receivedContainer.isRoutingLabel()) {
          data.add(new LabelData(LabelConstants.LBL_STORE, RdcConstants.SYM_ROUTING_LABEL));
        } else {
          data.add(new LabelData(LabelConstants.LBL_STORE, RdcUtils.getStringValue(store)));
        }
        data.add(new LabelData(LabelConstants.LBL_DIV, divisionNumber));
        data.add(
            new LabelData(
                LabelConstants.LBL_AISLE, getAisle(receivedContainer, isMfcIndicatorEnabled)));
        data.add(
            new LabelData(
                LabelConstants.LBL_BATCH, RdcUtils.getStringValue(receivedContainer.getBatch())));
        data.add(new LabelData(LabelConstants.LBL_CP_QTY, RdcUtils.getStringValue(receivedQty)));
        data.add(new LabelData(LabelConstants.LBL_DEPT, RdcUtils.getStringValue(departmentNumber)));

        // if storeZone & aisle not available, print eventChar as *
        data.add(
            new LabelData(
                LabelConstants.LBL_EVENTCHAR,
                getEventChar(receivedContainer, isMfcIndicatorEnabled)));
        data.add(new LabelData(LabelConstants.LBL_FULLUSERID, RdcUtils.getStringValue(userId)));
        data.add(
            new LabelData(
                LabelConstants.LBL_LANE,
                RdcUtils.getStringValue(receivedContainer.getShippingLane())));
        data.add(
            new LabelData(
                LabelConstants.LBL_PACK, RdcUtils.getStringValue(receivedContainer.getPack())));
        data.add(
            new LabelData(
                LabelConstants.LBL_POCODE, getPoCodeByPoType(receivedContainer.getPocode())));

        data.add(
            new LabelData(
                LabelConstants.LBL_PO_EVENT, getPoEvent(receivedContainer, isMfcIndicatorEnabled)));
        data.add(
            new LabelData(
                LabelConstants.LBL_PRI_LOC,
                !isRoutingLabel
                    ? RdcUtils.getStringValue(receivedContainer.getPri_loc())
                    : StringUtils.EMPTY));
        data.add(
            new LabelData(
                LabelConstants.LBL_SEC_LOC,
                !isRoutingLabel
                    ? RdcUtils.getStringValue(receivedContainer.getSec_loc())
                    : StringUtils.EMPTY));
        data.add(
            new LabelData(
                LabelConstants.LBL_TER_LOC,
                !isRoutingLabel
                    ? RdcUtils.getStringValue(receivedContainer.getTer_loc())
                    : StringUtils.EMPTY));
        data.add(new LabelData(LabelConstants.LBL_PRINTER, StringUtils.EMPTY));
        data.add(
            new LabelData(
                LabelConstants.LBL_STOREZONE,
                getStoreZone(receivedContainer, storeZone, isMfcIndicatorEnabled)));
      } else if (labelFormat
          .getFormat()
          .equals(LabelFormat.DA_NON_CONVEYABLE_VOICE_PUT.getFormat())) {
        data.add(new LabelData(LabelConstants.LBL_LEVEL, StringUtils.EMPTY));
        populateInductLabelAttributes(
            data, deliveryDocumentLine, receivedContainer, receivedQty, isAtlasConvertedItem);
        String packAndHandlingMethod =
            deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod();
        if (RdcConstants.DA_CASEPACK_VOICE_PUT_HANDLING_METHODS_MAP.containsValue(
            packAndHandlingMethod)) {
          data.add(
              (new LabelData(LabelConstants.LBL_VOICE_TYPE, RdcConstants.DA_CASEPACK_VOICE_PUT)));
        } else if (RdcConstants.DA_BREAKPACK_VOICE_PUT_HANDLING_METHODS_MAP.containsValue(
            packAndHandlingMethod)) {
          data.add(
              (new LabelData(LabelConstants.LBL_VOICE_TYPE, RdcConstants.DA_BREAKPACK_VOICE_PUT)));
        } else {
          data.add((new LabelData(LabelConstants.LBL_VOICE_TYPE, StringUtils.EMPTY)));
        }

      } else if (labelFormat.getFormat().equals(LabelFormat.DA_CONVEYABLE_INDUCT_PUT.getFormat())
          || labelFormat.getFormat().equals(LabelFormat.ATLAS_DA_CONVEYABLE_PUT.getFormat())) {
        populateInductLabelAttributes(
            data, deliveryDocumentLine, receivedContainer, receivedQty, isAtlasConvertedItem);
        data.add(
            new LabelData(
                LabelConstants.LBL_DEPT,
                RdcUtils.getStringValue(receivedContainer.getDepartment())));
      }
    }

    // common stuff here
    if (!isAtlasConvertedItem) {
      data.add(
          new LabelData(
              LabelConstants.LBL_RCVR, RdcUtils.getStringValue(receivedContainer.getReceiver())));
      String secondaryDescription =
          Objects.nonNull(deliveryDocumentLine.getSecondaryDescription())
              ? (deliveryDocumentLine.getSecondaryDescription().length()
                      > RdcConstants.LBL_ITEM_DESCRIPTION_MAX_LENGTH
                  ? deliveryDocumentLine
                      .getSecondaryDescription()
                      .substring(0, RdcConstants.LBL_ITEM_DESCRIPTION_MAX_LENGTH)
                  : deliveryDocumentLine.getSecondaryDescription())
              : StringUtils.EMPTY;
      data.add(new LabelData(LabelConstants.LBL_DESC2, secondaryDescription));
    }

    String description =
        Objects.nonNull(deliveryDocumentLine.getDescription())
            ? (deliveryDocumentLine.getDescription().length()
                    > RdcConstants.LBL_ITEM_DESCRIPTION_MAX_LENGTH
                ? deliveryDocumentLine
                    .getDescription()
                    .substring(0, RdcConstants.LBL_ITEM_DESCRIPTION_MAX_LENGTH)
                : deliveryDocumentLine.getDescription())
            : StringUtils.EMPTY;

    data.add(new LabelData(LabelConstants.LBL_DESC1, description));
    data.add(
        new LabelData(
            LabelConstants.LBL_CODE128,
            RdcUtils.getStringValue(deliveryDocumentLine.getPalletSSCC())));
    data.add(
        new LabelData(
            LabelConstants.LBL_HAZMAT, RdcUtils.getStringValue(receivedContainer.getHazmat())));
    data.add(
        new LabelData(
            LabelConstants.LBL_ITEM, RdcUtils.getStringValue(deliveryDocumentLine.getItemNbr())));
    data.add(
        new LabelData(
            LabelConstants.LBL_BAR,
            RdcUtils.getStringValue(receivedContainer.getLabelTrackingId())));
    String upc =
        Objects.nonNull(deliveryDocumentLine.getItemUpc())
            ? deliveryDocumentLine.getItemUpc()
            : deliveryDocumentLine.getCaseUpc();
    // legacy Store label has max length of 13 digit so fetch only the last 13 digit.
    // item upcs are prefixed with leading zeros (i.e. 00078742369785)
    upc =
        upc.length() > RdcConstants.LBL_ITEM_UPC_MAX_LENGTH
            ? upc.substring(1, RdcConstants.LBL_ITEM_UPC_MAX_LENGTH)
            : upc;
    data.add(new LabelData(LabelConstants.LBL_UPCBAR, upc));
    data.add(
        new LabelData(
            LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
    data.add(
        new LabelData(LabelConstants.LBL_PO, deliveryDocumentLine.getPurchaseReferenceNumber()));
    data.add(
        new LabelData(
            LabelConstants.LBL_POLINE,
            RdcUtils.getStringValue(deliveryDocumentLine.getPurchaseReferenceLineNumber())));
    data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));
    data.add(new LabelData(LabelConstants.LBL_SLOT, RdcUtils.getStringValue(slot)));
    data.add(new LabelData(LabelConstants.LBL_USERID, RdcUtils.getStringValue(userId)));
    data.add(
        new LabelData(
            LabelConstants.LBL_COLOR, RdcUtils.getStringValue(deliveryDocumentLine.getColor())));

    String itemSize =
        Objects.nonNull(deliveryDocumentLine.getSize())
            ? (deliveryDocumentLine.getSize().length() > RdcConstants.LBL_ITEM_SIZE_MAX_LENGTH
                ? deliveryDocumentLine.getSize().substring(0, RdcConstants.LBL_ITEM_SIZE_MAX_LENGTH)
                : deliveryDocumentLine.getSize())
            : StringUtils.EMPTY;
    data.add(new LabelData(LabelConstants.LBL_SIZE, itemSize));

    String vendorStockNumber =
        Objects.nonNull(deliveryDocumentLine.getVendorStockNumber())
            ? (deliveryDocumentLine.getVendorStockNumber().length()
                    > RdcConstants.LBL_VENDOR_STOCK_NUMBER_MAX_LENGTH
                ? deliveryDocumentLine
                    .getVendorStockNumber()
                    .substring(0, RdcConstants.LBL_VENDOR_STOCK_NUMBER_MAX_LENGTH)
                : deliveryDocumentLine.getVendorStockNumber())
            : StringUtils.EMPTY;
    data.add(new LabelData(LabelConstants.LBL_VENDORID, vendorStockNumber));

    // Added for reprint dashboard to SCT -- this info is already in LBL_BAR and LBL_LBLDATE
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_ID,
            RdcUtils.getStringValue(receivedContainer.getLabelTrackingId())));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
    return getPrintRequest(
        data,
        receivedContainer.getLabelTrackingId(),
        printJobId,
        labelFormat,
        RdcConstants.DA_LBL_TTL);
  }

  private static String getPoEvent(
      ReceivedContainer receivedContainer, boolean isMfcIndicatorEnabled) {
    return RdcUtils.validateIfDestTypeIsMFC(receivedContainer.getDestType(), isMfcIndicatorEnabled)
        ? LabelConstants.LBL_MFC_PO_EVENT
        : RdcUtils.getStringValue(receivedContainer.getPoevent());
  }

  private static String getStoreZone(
      ReceivedContainer receivedContainer, String storeZone, boolean isMfcIndicatorEnabled) {
    return RdcUtils.validateIfDestTypeIsMFC(receivedContainer.getDestType(), isMfcIndicatorEnabled)
        ? LabelType.MFC.name().substring(0, 1)
        : RdcUtils.getStringValue(storeZone);
  }

  private static String getAisle(
      ReceivedContainer receivedContainer, boolean isMfcIndicatorEnabled) {
    return RdcUtils.validateIfDestTypeIsMFC(receivedContainer.getDestType(), isMfcIndicatorEnabled)
        ? LabelType.MFC.name().substring(1, 3)
        : RdcUtils.getStringValue(receivedContainer.getAisle());
  }

  private static String getEventChar(
      ReceivedContainer receivedContainer, boolean isMfcIndicatorEnabled) {
    return RdcUtils.validateIfDestTypeIsMFC(receivedContainer.getDestType(), isMfcIndicatorEnabled)
        ? StringUtils.EMPTY
        : StringUtils.isNotBlank(receivedContainer.getStorezone())
            ? StringUtils.EMPTY
            : StringUtils.isNotBlank(receivedContainer.getAisle())
                ? StringUtils.EMPTY
                : RdcUtils.getStringValue('*');
  }

  /**
   * *
   *
   * @param data
   * @param deliveryDocumentLine
   * @param receivedContainer
   * @param receivedQty
   */
  private static void populateInductLabelAttributes(
      List<LabelData> data,
      DeliveryDocumentLine deliveryDocumentLine,
      ReceivedContainer receivedContainer,
      Integer receivedQty,
      boolean isAtlasConvertedItem) {
    String zoneRange = null;
    data.add(
        new LabelData(
            LabelConstants.LBL_PICK,
            RdcUtils.getStringValue(RdcUtils.pickQuantity(receivedQty, deliveryDocumentLine))));
    data.add(
        new LabelData(
            LabelConstants.LBL_VNPK,
            RdcUtils.getStringValue(deliveryDocumentLine.getVendorPack())));
    data.add(
        new LabelData(
            LabelConstants.LBL_WHPK,
            RdcUtils.getStringValue(deliveryDocumentLine.getWarehousePack())));
    if (!isAtlasConvertedItem) {
      data.add(
          new LabelData(
              LabelConstants.LBL_PLTTAG, RdcUtils.getStringValue(receivedContainer.getTag())));
      data.add(new LabelData(LabelConstants.LBL_RETAIL, StringUtils.EMPTY));
      zoneRange = RdcUtils.getZoneRange(receivedContainer.getDestinations());
    } else {
      zoneRange = RdcUtils.getStringValue(receivedContainer.getDcZoneRange());
    }

    data.add(new LabelData(LabelConstants.LBL_SECTION, zoneRange));
  }

  public static Map<String, Object> generateDsdcPackLabel(
      DsdcReceiveRequest dsdcReceiveRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      Long printJobId,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime) {
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    printLabelRequests.add(
        getDsdcPackLabel(dsdcReceiveRequest, dsdcReceiveResponse, printJobId, zonedDateTime));

    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    return printJob;
  }

  /**
   * This method will prepare the print label request for DSDC containers
   *
   * @param instructionRequest
   * @param deliveryDocumentList
   * @param parentReceivedContainer
   * @param isAuditTag
   * @param printJobId
   * @param httpHeaders
   * @param zonedDateTime
   * @return
   */
  public static Map<String, Object> generateAtlasDsdcPackLabel(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> deliveryDocumentList,
      ReceivedContainer parentReceivedContainer,
      boolean isAuditTag,
      Long printJobId,
      HttpHeaders httpHeaders,
      ZonedDateTime zonedDateTime) {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    printLabelRequests.add(
        getAtlasDsdcPackLabel(
            instructionRequest,
            deliveryDocumentList,
            parentReceivedContainer,
            isAuditTag,
            printJobId,
            zonedDateTime,
            userId));

    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    return printJob;
  }

  /**
   * This method returns label format for DSDC Store Label and DSDC Audit Label
   *
   * @param dsdcReceiveRequest
   * @param dsdcReceiveResponse
   * @param printJobId
   * @param zonedDateTime
   * @return
   */
  private static PrintLabelRequest getDsdcPackLabel(
      DsdcReceiveRequest dsdcReceiveRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      Long printJobId,
      ZonedDateTime zonedDateTime) {

    String slot = null;
    String labelTrackingId = null;
    List<LabelData> data = new ArrayList<>();
    data.add(
        new LabelData(
            LabelConstants.LBL_PO, RdcUtils.getStringValue((dsdcReceiveResponse.getPo_nbr()))));

    data.add(
        new LabelData(
            LabelConstants.LBL_CODE128, RdcUtils.getStringValue(dsdcReceiveRequest.getPack_nbr())));

    if (Objects.equals(dsdcReceiveResponse.getAuditFlag(), (RdcConstants.DSDC_AUDIT_FLAG))) {
      data.add(
          new LabelData(
              LabelConstants.LBL_USERID, RdcUtils.getStringValue(dsdcReceiveRequest.getUserId())));
      data.add(
          new LabelData(
              LabelConstants.LBL_RCVR, RdcUtils.getStringValue(dsdcReceiveResponse.getRcvr_nbr())));
      data.add(
          new LabelData(
              LabelConstants.LBL_DOORNBR,
              RdcUtils.getStringValue(dsdcReceiveRequest.getDoorNum())));
      data.add(
          new LabelData(
              LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
      data.add(
          new LabelData(
              LabelConstants.LBL_MANIFEST,
              RdcUtils.getStringValue(dsdcReceiveRequest.getManifest())));
      slot = RdcConstants.DSDC_AUDIT_SLOT;
      labelTrackingId = dsdcReceiveRequest.getPack_nbr();
    } else {
      data.add(new LabelData(LabelConstants.LBL_PRINTER, StringUtils.EMPTY));
      data.add(
          new LabelData(
              LabelConstants.LBL_SLOT, RdcUtils.getStringValue(dsdcReceiveResponse.getSlot())));
      data.add(
          new LabelData(
              LabelConstants.LBL_BATCH, RdcUtils.getStringValue(dsdcReceiveResponse.getBatch())));
      data.add(
          new LabelData(
              LabelConstants.LBL_STORE, RdcUtils.getStringValue(dsdcReceiveResponse.getStore())));
      data.add(
          new LabelData(
              LabelConstants.LBL_DIV, StringUtils.leftPad(dsdcReceiveResponse.getDiv(), 2, '0')));
      data.add(new LabelData(LabelConstants.LBL_FULLUSERID, dsdcReceiveRequest.getUserId()));
      data.add(
          new LabelData(
              LabelConstants.LBL_POCODE,
              getPoCodeByPoType(Integer.parseInt(dsdcReceiveResponse.getPocode()))));
      // Last 11 digit of the DSDC label barcode is Carton number, subString 7 will print from 7th
      // position of the 18 digit label barcode
      data.add(
          new LabelData(
              LabelConstants.LBL_DC_CARTON,
              RdcUtils.getStringValue(dsdcReceiveResponse.getLabel_bar_code().substring(7))));
      data.add(
          new LabelData(
              LabelConstants.LBL_RCVR, RdcUtils.getStringValue(dsdcReceiveResponse.getRcvr_nbr())));
      data.add(
          new LabelData(
              LabelConstants.LBL_LANE,
              StringUtils.leftPad(dsdcReceiveResponse.getLane_nbr(), 3, '0')));
      data.add(
          new LabelData(
              LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
      data.add(
          new LabelData(
              LabelConstants.LBL_DEPT, RdcUtils.getStringValue(dsdcReceiveResponse.getDept())));
      data.add(
          new LabelData(
              LabelConstants.LBL_PO_EVENT,
              RdcUtils.getStringValue(dsdcReceiveResponse.getEvent())));
      data.add(
          new LabelData(
              LabelConstants.LBL_BAR,
              RdcUtils.getStringValue(dsdcReceiveResponse.getLabel_bar_code())));
      data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));
      data.add(new LabelData(LabelConstants.LBL_HAZMAT, StringUtils.EMPTY));
      slot = dsdcReceiveResponse.getSlot();
      labelTrackingId = RdcUtils.getStringValue(dsdcReceiveResponse.getLabel_bar_code());
    }
    // Added for reprint dashboard to SCT -- this info is already in LBL_BAR and LBL_LBLDATE
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_ID,
            RdcUtils.getStringValue(dsdcReceiveRequest.getPack_nbr())));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));

    return getPrintRequest(
        data,
        labelTrackingId,
        printJobId,
        RdcConstants.DA_LABEL_FORMAT_MAP.get(slot),
        RdcConstants.DA_LBL_TTL);
  }

  /**
   * @param instructionRequest
   * @param deliveryDocumentList
   * @param parentReceivedContainer
   * @param isAuditTag
   * @param printJobId
   * @param zonedDateTime
   * @param userId
   * @return
   */
  private static PrintLabelRequest getAtlasDsdcPackLabel(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> deliveryDocumentList,
      ReceivedContainer parentReceivedContainer,
      boolean isAuditTag,
      Long printJobId,
      ZonedDateTime zonedDateTime,
      String userId) {
    String labelTrackingId = instructionRequest.getSscc();
    List<LabelData> data = new ArrayList<>();
    LabelFormat dsdcLabelFormat =
        isAuditTag ? LabelFormat.ATLAS_DSDC_AUDIT : LabelFormat.ATLAS_DSDC;
    DeliveryDocument deliveryDocument = deliveryDocumentList.get(0);
    boolean isMultiPO = deliveryDocumentList.size() > 1;
    if (isMultiPO) {
      data.add(new LabelData(LabelConstants.LBL_PO, LabelConstants.MULTI_PO));
    } else {
      data.add(
          new LabelData(
              LabelConstants.LBL_PO,
              RdcUtils.getStringValue(deliveryDocument.getPurchaseReferenceNumber())));
    }
    String poevent =
        Objects.nonNull(parentReceivedContainer)
            ? parentReceivedContainer.getPoevent()
            : StringUtils.EMPTY;
    data.add(new LabelData(LabelConstants.LBL_PO_EVENT, poevent));

    data.add(new LabelData(LabelConstants.LBL_CODE128, RdcUtils.getStringValue(labelTrackingId)));

    if (isAuditTag) {
      data.add(new LabelData(LabelConstants.LBL_USERID, RdcUtils.getStringValue(userId)));
      data.add(
          new LabelData(
              LabelConstants.LBL_DOORNBR,
              RdcUtils.getStringValue(instructionRequest.getDoorNumber())));
      data.add(
          new LabelData(
              LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
      data.add(
          new LabelData(
              LabelConstants.LBL_MANIFEST,
              RdcUtils.getStringValue(instructionRequest.getDeliveryNumber())));
    } else {
      if (Objects.nonNull(parentReceivedContainer)) {
        String storeNumber =
            !CollectionUtils.isEmpty(parentReceivedContainer.getDestinations())
                ? parentReceivedContainer.getDestinations().get(0).getStore()
                : StringUtils.EMPTY;
        String divisionNumber =
            Objects.nonNull(parentReceivedContainer.getDivision())
                ? parentReceivedContainer.getDivision().toString()
                : StringUtils.EMPTY;
        data.add(new LabelData(LabelConstants.LBL_PRINTER, StringUtils.EMPTY));
        String batch =
            Objects.nonNull(parentReceivedContainer.getBatch())
                ? parentReceivedContainer.getBatch().toString()
                : StringUtils.EMPTY;
        data.add(new LabelData(LabelConstants.LBL_BATCH, RdcUtils.getStringValue(batch)));
        data.add(new LabelData(LabelConstants.LBL_STORE, RdcUtils.getStringValue(storeNumber)));
        data.add(
            new LabelData(LabelConstants.LBL_DIV, StringUtils.leftPad(divisionNumber, 2, '0')));
        data.add(new LabelData(LabelConstants.LBL_FULLUSERID, userId));
        data.add(
            new LabelData(
                LabelConstants.LBL_POCODE, getPoCodeByPoType(deliveryDocument.getPoTypeCode())));
        data.add(
            new LabelData(
                LabelConstants.LBL_LANE,
                StringUtils.leftPad(
                    Objects.nonNull(parentReceivedContainer.getShippingLane())
                        ? parentReceivedContainer.getShippingLane().toString()
                        : "",
                    LabelConstants.LBL_LANE_CHAR_COUNT,
                    '0')));
        data.add(
            new LabelData(
                LabelConstants.LBL_LBLDATE, RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));
        data.add(
            new LabelData(
                LabelConstants.LBL_DEPT,
                RdcUtils.getStringValue(deliveryDocument.getDeptNumber())));
        data.add(
            new LabelData(
                LabelConstants.LBL_BAR,
                RdcUtils.getStringValue(parentReceivedContainer.getLabelTrackingId())));
        data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));
        data.add(new LabelData(LabelConstants.LBL_HAZMAT, StringUtils.EMPTY));
        labelTrackingId = parentReceivedContainer.getLabelTrackingId();
      }
    }
    // Added for reprint dashboard to SCT -- this info is already in LBL_BAR and LBL_LBLDATE
    data.add(
        new LabelData(LabelConstants.LBL_CONTAINER_ID, RdcUtils.getStringValue(labelTrackingId)));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTime(zonedDateTime)));

    return getPrintRequest(
        data, labelTrackingId, printJobId, dsdcLabelFormat, RdcConstants.DA_LBL_TTL);
  }

  public static String getPoCodeByPoType(int poType) {
    String resultPoCode = RdcConstants.DEFAULT_PO_CODE;

    if (Arrays.asList(RdcConstants.AD_PO_TYPE_CODES).contains(poType)) {
      resultPoCode = PoCode.AD.toString();
    } else if (Arrays.asList(RdcConstants.WR_PO_TYPE_CODES).contains(poType)) {
      resultPoCode = PoCode.WR.toString();
    } else if (Arrays.asList(RdcConstants.WPM_PO_TYPE_CODES).contains(poType)) {
      resultPoCode = PoCode.WPM.toString();
    }
    return resultPoCode;
  }

  /**
   * * This method builds printJob for reprint based on the Mirage Exception Receiving response when
   * the item is already received in Exception Receiving
   *
   * @param receiveExceptionRequest
   * @param mirageExceptionResponse
   * @return printJob
   */
  public static Map<String, Object> reprintLabel(
      ReceiveExceptionRequest receiveExceptionRequest,
      MirageExceptionResponse mirageExceptionResponse,
      String dcTimeZone,
      HttpHeaders httpHeaders,
      boolean isMfcIndicatorEnabled)
      throws ReceivingException {

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String correlationId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setColor(RdcUtils.getStringValue(mirageExceptionResponse.getColor()));
    deliveryDocumentLine.setItemNbr(mirageExceptionResponse.getItemNumber());
    deliveryDocumentLine.setItemUpc(RdcUtils.getStringValue(mirageExceptionResponse.getItemUPC()));
    deliveryDocumentLine.setDescription(RdcUtils.getStringValue(mirageExceptionResponse.getDesc()));
    deliveryDocumentLine.setSize(mirageExceptionResponse.getSize());

    deliveryDocumentLine.setVendorPack(mirageExceptionResponse.getVendorPack());
    deliveryDocumentLine.setWarehousePack(mirageExceptionResponse.getWarehousePack());

    ItemData itemData = new ItemData();
    if (Objects.nonNull(mirageExceptionResponse.getRdsHandlingCode())
        && Objects.nonNull(mirageExceptionResponse.getRdsPackTypeCode())) {
      itemData.setItemPackAndHandlingCode(
          mirageExceptionResponse
              .getRdsPackTypeCode()
              .concat(mirageExceptionResponse.getRdsHandlingCode()));
    }
    // TODO: Need to set this according to the item's Property.
    itemData.setAtlasConvertedItem(false);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    if (Objects.nonNull(mirageExceptionResponse.getPurchaseReferenceLineNumber())) {
      deliveryDocumentLine.setPurchaseReferenceLineNumber(
          mirageExceptionResponse.getPurchaseReferenceLineNumber());
    }
    deliveryDocumentLine.setPurchaseReferenceNumber(
        mirageExceptionResponse.getPurchaseReferenceNumber());
    deliveryDocumentLine.setVendorStockNumber(
        RdcUtils.getStringValue(mirageExceptionResponse.getVendorStockNumber()));
    if (Objects.isNull(mirageExceptionResponse.getStoreInfo())) {
      DsdcReceiveRequest dsdcReceiveRequest =
          new DsdcReceiveRequest(
              correlationId,
              mirageExceptionResponse.getPackNumber(),
              mirageExceptionResponse.getPackinfo().getManifest(),
              receiveExceptionRequest.getDoorNumber(),
              userId);
      DsdcReceiveResponse dsdcReceiveResponse = mirageExceptionResponse.getPackinfo();
      ZonedDateTime zonedDateTime =
          RdcUtils.getZonedDateTimeByDCTimezone(
              dcTimeZone,
              ReceivingUtils.parseStringToDateTime(
                  mirageExceptionResponse.getLabelDate(), ReceivingConstants.UTC_DATE_FORMAT));
      printLabelRequest =
          getDsdcPackLabel(
              dsdcReceiveRequest,
              dsdcReceiveResponse,
              Long.valueOf(dsdcReceiveResponse.getDccarton()),
              zonedDateTime);
    } else {
      ReceivedContainer receivedContainer = mirageExceptionResponse.getStoreInfo();
      Date dateTime =
          ReceivingUtils.parseStringToDateTime(
              mirageExceptionResponse.getStoreInfo().getLabelTimestamp(),
              ReceivingConstants.LABEL_TIMESTAMP_FORMAT);
      ZonedDateTime zonedDateTime = RdcUtils.getZonedDateTimeByDCTimezone(dcTimeZone, dateTime);
      Long printJobId =
          Objects.nonNull(receiveExceptionRequest.getItemNumber())
              ? Long.valueOf(receiveExceptionRequest.getItemNumber())
              : mirageExceptionResponse.getItemNumber();
      printLabelRequest =
          getDaCaseLabel(
              deliveryDocumentLine,
              1,
              receivedContainer,
              printJobId,
              httpHeaders,
              zonedDateTime,
              isMfcIndicatorEnabled);
    }
    printLabelRequests.add(printLabelRequest);
    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);
    return printJob;
  }

  public static PrintLabelRequest populateReprintPalletSSTKLabel(
      ContainerItemDetails containerItemDetails,
      LabelAttributes labelAttributes,
      boolean isSplitPallet) {
    List<LabelData> data = new ArrayList<>();

    ContainerItemDetail itemDetail =
        !CollectionUtils.isEmpty(containerItemDetails.getItems())
            ? containerItemDetails.getItems().get(0)
            : new ContainerItemDetail();
    String slot =
        Objects.nonNull(containerItemDetails.getLocationName())
            ? containerItemDetails.getLocationName()
            : StringUtils.EMPTY;

    String upc =
        Objects.nonNull(itemDetail.getItemUPC())
            ? itemDetail.getItemUPC()
            : itemDetail.getCaseUPC();
    LabelFormat labelFormat = LabelFormat.ATLAS_RDC_PALLET;
    String poLine =
        CollectionUtils.isEmpty(itemDetail.getPodetails())
            ? StringUtils.EMPTY
            : RdcUtils.getStringValue(
                itemDetail.getPodetails().get(0).getPurchaseReferenceLineNumber());

    data.add(new LabelData(LabelConstants.LBL_BAR, containerItemDetails.getTrackingId()));
    data.add(new LabelData(LabelConstants.LBL_SLOT, slot));
    data.add(
        new LabelData(
            LabelConstants.LBL_PICK, RdcUtils.getStringValue(itemDetail.getVenPkQuantity())));
    data.add(
        new LabelData(
            LabelConstants.LBL_LBLDATE,
            RdcUtils.getLabelFormatDateAndTimeByTimeInMilliSeconds(
                containerItemDetails.getContainerCreateDate())));
    data.add(
        new LabelData(
            LabelConstants.LBL_VNPK, RdcUtils.getStringValue(itemDetail.getVenPkRatio())));
    data.add(
        new LabelData(
            LabelConstants.LBL_ITEM, RdcUtils.getStringValue(itemDetail.getItemNumber())));
    data.add(new LabelData(LabelConstants.LBL_POLINE, poLine));
    data.add(new LabelData(LabelConstants.LBL_RCVR, StringUtils.EMPTY));
    data.add(
        new LabelData(
            LabelConstants.LBL_WHPK, RdcUtils.getStringValue(itemDetail.getWarPkRatio())));
    data.add(
        new LabelData(
            LabelConstants.LBL_UPCBAR, StringUtils.isEmpty(upc) ? StringUtils.EMPTY : upc));
    data.add(new LabelData(LabelConstants.LBL_USERID, labelAttributes.getUserId()));
    data.add(
        new LabelData(
            LabelConstants.LBL_TI, RdcUtils.getStringValue(labelAttributes.getPalletTi())));
    data.add(
        new LabelData(
            LabelConstants.LBL_HI, RdcUtils.getStringValue(labelAttributes.getPalletHi())));
    data.add(
        new LabelData(
            LabelConstants.LBL_VENDORID,
            RdcUtils.getStringValue(labelAttributes.getVendorStockNumber())));
    data.add(new LabelData(LabelConstants.LBL_NUM, RdcConstants.PRINT_LABEL_NUM));
    data.add(new LabelData(LabelConstants.LBL_CNT, RdcConstants.PRINT_LABEL_CNT));

    // Added for reprint dashboard to SCT
    data.add(new LabelData(LabelConstants.LBL_CONTAINER_ID, containerItemDetails.getTrackingId()));
    data.add(
        new LabelData(
            LabelConstants.LBL_CONTAINER_CREATION_TIME,
            RdcUtils.getLabelFormatDateAndTimeByTimeInMilliSeconds(
                containerItemDetails.getContainerCreateDate())));

    /* print PARTIAL or SPLIT PALLET only for SSTK freights. For DA slotting it
    needs to be EMPTY */
    String channelType =
        StringUtils.isEmpty(itemDetail.getChannelType())
            ? StringUtils.EMPTY
            : itemDetail.getChannelType();
    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(channelType)) {
      if (!isSplitPallet) {
        DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
        deliveryDocumentLine.setPalletTie(labelAttributes.getPalletTi());
        deliveryDocumentLine.setPalletHigh(labelAttributes.getPalletHi());
        String partialPalletLabel =
            isPartialPalletOpted(deliveryDocumentLine, (int) itemDetail.getVenPkQuantity())
                ? LabelConstants.LBL_PARTIAL
                : StringUtils.EMPTY;
        data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, partialPalletLabel));
      } else {
        data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, LabelConstants.LBL_SPLIT_PALLET));
      }
    } else {
      data.add(new LabelData(LabelConstants.LBL_PARTIAL_TAG, StringUtils.EMPTY));
    }

    data.add(new LabelData(LabelConstants.LBL_REPRINT, StringUtils.EMPTY));
    data.add(new LabelData(LabelConstants.LBL_SLOTSIZE, labelAttributes.getSlotSize()));

    if (Objects.nonNull(containerItemDetails.getDeliveryNumber())) {
      data.add(
          new LabelData(
              LabelConstants.LBL_DELIVERY_NUMBER, containerItemDetails.getDeliveryNumber()));
    } else {
      data.add(new LabelData(LabelConstants.LBL_DELIVERY_NUMBER, StringUtils.EMPTY));
    }

    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(channelType)) {
      data.add(new LabelData(LabelConstants.LBL_FREIGHT_TYPE, SSTK_FREIGHT_TYPE));
    } else if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(channelType)) {
      data.add(new LabelData(LabelConstants.LBL_FREIGHT_TYPE, DA_FREIGHT_TYPE));
    } else {
      data.add(new LabelData(LabelConstants.LBL_FREIGHT_TYPE, StringUtils.EMPTY));
    }

    Random rand = new SecureRandom();
    int printJobId = rand.nextInt(MAX_RANDOM_INT_PRINT_JOB_ID) + INT_BASE_VALUE;
    return getPrintRequest(
        data,
        containerItemDetails.getTrackingId(),
        (long) printJobId,
        labelFormat,
        RdcConstants.LBL_TTL);
  }

  /**
   * Retrieve SSTK or DA channel based on the purchase ref type
   *
   * @param deliveryDocumentLine
   * @return
   */
  private static String getFreightTypeByChannelMethod(DeliveryDocumentLine deliveryDocumentLine) {
    if (SSTK_CHANNEL_METHODS_FOR_RDC
        .stream()
        .anyMatch(channel -> channel.equalsIgnoreCase(deliveryDocumentLine.getPurchaseRefType()))) {
      return SLOTTING_SSTK_RECEIVING_METHOD;
    }
    if (DA_CHANNEL_METHODS_FOR_RDC
        .stream()
        .anyMatch(channel -> channel.equalsIgnoreCase(deliveryDocumentLine.getPurchaseRefType()))) {
      return SLOTTING_DA_RECEIVING_METHOD;
    }
    return null;
  }
}
