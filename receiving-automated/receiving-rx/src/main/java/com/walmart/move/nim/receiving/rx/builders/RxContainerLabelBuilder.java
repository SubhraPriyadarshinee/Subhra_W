package com.walmart.move.nim.receiving.rx.builders;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ENABLE_LABEL_PARTIAL_TAG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_RDS_RECEIPT_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.LABEL_PRINT_FORMAT_CONFIG;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RxContainerLabelBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RxContainerLabelBuilder.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public PrintLabelData generateContainerLabel(
      ReceivedContainer receivedContainer,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      Container parentContainer,
      Instruction instructionFromDB) {
    String trackingId = "";
    if(StringUtils.isNumeric(receivedContainer.getLabelTrackingId())){
       trackingId = Long.valueOf(receivedContainer.getLabelTrackingId()).toString();
    }else{
       trackingId = receivedContainer.getLabelTrackingId();
    }
    LOGGER.info("Building containerLabel for LPN: {}", trackingId);

    // Build header
    Map<String, String> headers = new HashMap<>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    headers.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // Build label data
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.ITEM_NUM_LABEL_NAME)
            .value(deliveryDocumentLine.getItemNbr().toString())
            .build());
    labelDataList.add(
        LabelData.builder().key(RxConstants.TI).value(ReceivingConstants.EMPTY_STRING).build());
    labelDataList.add(
        LabelData.builder().key(RxConstants.HI).value(ReceivingConstants.EMPTY_STRING).build());
    long labelVnpk = deliveryDocumentLine.getVendorPack();
    long labelWhpk = deliveryDocumentLine.getWarehousePack();
    labelDataList.add(
        LabelData.builder().key(RxConstants.VNPK).value(String.valueOf(labelVnpk)).build());
    labelDataList.add(
        LabelData.builder().key(RxConstants.WHPK).value(String.valueOf(labelWhpk)).build());
    labelDataList.add(
        LabelData.builder().key(RxConstants.LPN_LABEL_NAME).value(trackingId).build());
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.SLOT)
            .value(receivedContainer.getDestinations().get(0).getSlot())
            .build());
    String gtinFromChildContainer = getGtinFromChildContainer(parentContainer);
    String upc =
        StringUtils.isNotBlank(gtinFromChildContainer)
            ? gtinFromChildContainer
            : instructionFromDB.getGtin();
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.UPC)
            .value(StringUtils.defaultIfBlank(upc, ReceivingConstants.EMPTY_STRING))
            .build());
    String ndc =
        StringUtils.isNotBlank(deliveryDocumentLine.getNdc())
            ? deliveryDocumentLine.getNdc()
            : deliveryDocumentLine.getVendorStockNumber();
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.VDR_SK)
            .value(StringUtils.defaultIfBlank(ndc, ReceivingConstants.EMPTY_STRING))
            .build());
    labelDataList.add(LabelData.builder().key(RxConstants.PTAG).value(trackingId).build());

    if(null!=receivedContainer.getReceiver()
            && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DC_RDS_RECEIPT_ENABLED)) {
      labelDataList.add(
              LabelData.builder()
                      .key(RxConstants.RCVR)
                      .value(receivedContainer.getReceiver().toString())
                      .build());
    }
    labelDataList.add(
        LabelData.builder().key(RxConstants.LN).value(ReceivingConstants.EMPTY_STRING).build());

    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    String receivedDate = ReceivingUtils.getLabelFormatDateAndTime(
            ReceivingUtils.getDCDateTime(dcTimeZone), "MM/dd/yy HH:mm:ss");
    labelDataList.add(
            LabelData.builder().key(RxConstants.DATE).value(receivedDate).build());

    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.LABEL_QTY)
            .value(ReceivingConstants.EMPTY_STRING)
            .build());
    labelDataList.add(
        LabelData.builder()
            .key(RxConstants.TOTAL_QTY)
            .value(ReceivingConstants.EMPTY_STRING)
            .build());
    labelDataList.add(LabelData.builder().key(RxConstants.LABEL_TIMESTAMP).value(receivedDate).build());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    labelDataList.add(LabelData.builder().key(RxConstants.USER).value(userId).build());

    Integer receivedQty =
        ReceivingUtils.conversionToVendorPack(
            instructionFromDB.getReceivedQuantity(),
            instructionFromDB.getReceivedQuantityUOM(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), ENABLE_LABEL_PARTIAL_TAG)) {
      if (receivedQty < 1) {
        labelDataList.add(
            LabelData.builder()
                .key(RxConstants.QTY)
                .value(
                    String.valueOf(
                        ReceivingUtils.conversionToWareHousePack(
                            instructionFromDB.getReceivedQuantity(),
                            instructionFromDB.getReceivedQuantityUOM(),
                            deliveryDocumentLine.getVendorPack(),
                            deliveryDocumentLine.getWarehousePack())))
                .build());
        labelDataList.add(LabelData.builder().key(RxConstants.UOM).value(RxConstants.WHPK).build());
        if (Objects.nonNull(instructionFromDB.getInstructionSetId())) {
          labelDataList.add(
              LabelData.builder()
                  .key(RxConstants.PARTIAL)
                  .value(RxConstants.LBL_SPLIT_PALLET)
                  .build());
        } else {
          labelDataList.add(
              LabelData.builder().key(RxConstants.PARTIAL).value(RxConstants.PARTIAL).build());
        }
      } else {
        labelDataList.add(
            LabelData.builder().key(RxConstants.QTY).value(String.valueOf(receivedQty)).build());
        labelDataList.add(LabelData.builder().key(RxConstants.UOM).value(RxConstants.VNPK).build());
        if (Objects.nonNull(instructionFromDB.getInstructionSetId())) {
          labelDataList.add(
              LabelData.builder()
                  .key(RxConstants.PARTIAL)
                  .value(RxConstants.LBL_SPLIT_PALLET)
                  .build());
        } else {
          labelDataList.add(
              LabelData.builder()
                  .key(RxConstants.PARTIAL)
                  .value(ReceivingConstants.EMPTY_STRING)
                  .build());
        }
      }
      labelDataList.add(
          LabelData.builder()
              .key(RxConstants.PRIME_SLOT_ID)
              .value(
                  StringUtils.defaultIfBlank(
                      getPrimeSlotDetails(instructionFromDB), ReceivingConstants.EMPTY_STRING))
              .build());
    } else {
      labelDataList.add(
          LabelData.builder()
              .key(RxConstants.QTY)
              .value(
                  String.valueOf(
                      ReceivingUtils.conversionToVendorPackRoundUp(
                          instructionFromDB.getReceivedQuantity(),
                          instructionFromDB.getReceivedQuantityUOM(),
                          deliveryDocumentLine.getVendorPack(),
                          deliveryDocumentLine.getWarehousePack())))
              .build());
      if (Objects.nonNull(instructionFromDB.getInstructionSetId())) {
        labelDataList.add(
            LabelData.builder()
                .key(RxConstants.PARTIAL)
                .value(RxConstants.LBL_SPLIT_PALLET)
                .build());
      }
    }

    // Prepare print request
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setFormatName(tenantSpecificConfigReader.getCcmValue(TenantContext.getFacilityNum(), LABEL_PRINT_FORMAT_CONFIG, RxConstants.LABEL_FORMAT_NAME));
    printLabelRequest.setLabelIdentifier(trackingId);
    printLabelRequest.setTtlInHours(72);
    printLabelRequest.setData(labelDataList);

    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    printLabelRequests.add(printLabelRequest);

    // Build container label
    PrintLabelData containerLabel = new PrintLabelData();
    containerLabel.setClientId(RxConstants.CLIENT_ID);
    containerLabel.setHeaders(headers);
    containerLabel.setPrintRequests(printLabelRequests);

    LOGGER.info("Returning containerLabel: {}", containerLabel);
    return containerLabel;
  }

  private String getPrimeSlotDetails(Instruction instructionFromDB) {
    String primeSlot = null;
    LinkedTreeMap<String, Object> moveDetails = instructionFromDB.getMove();
    if (Objects.nonNull(moveDetails)) {
      primeSlot =
          moveDetails.get(ReceivingConstants.MOVE_PRIME_LOCATION) == null
              ? ReceivingConstants.EMPTY_STRING
              : String.valueOf(moveDetails.get(ReceivingConstants.MOVE_PRIME_LOCATION));
    }
    return primeSlot;
  }

  private String getGtinFromChildContainer(Container parentContainer) {
    Set<Container> childContainers = parentContainer.getChildContainers();
    if (CollectionUtils.isNotEmpty(childContainers)) {
      return childContainers.iterator().next().getContainerItems().get(0).getGtin();
    } else {
      return parentContainer
          .getContainerItems()
          .stream()
          .filter(containerItem -> StringUtils.isNotEmpty(containerItem.getGtin()))
          .findFirst()
          .get()
          .getGtin();
      // parentContainer.getContainerItems().get(0).getGtin();
    }
  }
}
