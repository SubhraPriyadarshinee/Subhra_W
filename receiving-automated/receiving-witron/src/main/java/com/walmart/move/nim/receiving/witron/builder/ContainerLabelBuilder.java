package com.walmart.move.nim.receiving.witron.builder;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.WarehouseAreaCodes.getwareHouseCodeMapping;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.WarehouseAreaCodes;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.model.GdcLabelData;
import com.walmart.move.nim.receiving.witron.model.PrintLabelRequest;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.collections.CollectionUtils;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** ContainerLabelBuilder */
@Component
public class ContainerLabelBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerLabelBuilder.class);
  public static final String MECH_DC_INDICATOR = "M-";

  @Autowired private TenantSpecificConfigReader configUtils;

  @Autowired private GDCFlagReader gdcFlagReader;

  @ManagedConfiguration private WitronManagedConfig witronManagedConfig;

  public ContainerLabel generateContainerLabel(
      String trackingId,
      String slotId,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders) {

    // Build label data
    List<GdcLabelData> labelDataList =
        buildGDCLabelDataList(trackingId, slotId, deliveryDocumentLine);

    // Build Print and Set Label Data
    List<PrintLabelRequest> printLabelRequests = buildPrintRequestList(trackingId, labelDataList);

    return buildContainerLabel(httpHeaders, printLabelRequests);
  }

  public ContainerLabel generateContainerLabelV2(
      GLSReceiveResponse glsReceiveResponse,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders) {
    LOGGER.info("Building containerLabel for Manual GDC: {}", glsReceiveResponse.getPalletTagId());

    // Build Label Data
    List<GdcLabelData> labelDataList =
        buildGDCLabelDataList(
            glsReceiveResponse.getPalletTagId(),
            glsReceiveResponse.getSlotId(),
            deliveryDocumentLine);

    // Build Print and Set Label Data
    List<PrintLabelRequest> printLabelRequests =
        buildPrintRequestList(glsReceiveResponse.getPalletTagId(), labelDataList);

    return buildContainerLabel(httpHeaders, printLabelRequests);
  }

  private List<GdcLabelData> buildGDCLabelDataList(
      String trackingId, String slotId, DeliveryDocumentLine deliveryDocumentLine) {
    // Build Label Data
    List<GdcLabelData> labelDataList = new ArrayList<>();
    labelDataList.add(
        GdcLabelData.builder()
            .key("slot")
            .value(determineSlot(deliveryDocumentLine, slotId))
            .build());
    labelDataList.add(
        GdcLabelData.builder().key("lpn7").value(StringUtils.right(trackingId, 7)).build());
    labelDataList.add(GdcLabelData.builder().key("LPN").value(trackingId).build());
    final ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    labelDataList.add(
        GdcLabelData.builder()
            .key("warehouseArea")
            .value(
                WarehouseAreaCodes.getwareHouseDescMapping(additionalInfo.getWarehouseAreaCode()))
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("profiledWarehouseArea")
            .value(
                isNotBlank(additionalInfo.getProfiledWarehouseArea())
                    ? additionalInfo.getProfiledWarehouseArea()
                    : "")
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key(PRINT_WAREHOUSE_AREA_CD)
            .value(getwareHouseCodeMapping(additionalInfo.getWarehouseAreaCode()))
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("itemNum")
            .value(String.valueOf(deliveryDocumentLine.getItemNbr()))
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("itemDesc")
            .value(deliveryDocumentLine.getDescription())
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("itemPack")
            .value(String.valueOf(deliveryDocumentLine.getVendorPack()))
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("itemSize")
            .value(nonNull(deliveryDocumentLine.getSize()) ? deliveryDocumentLine.getSize() : "")
            .build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("PO")
            .value(deliveryDocumentLine.getPurchaseReferenceNumber())
            .build());

    labelDataList.add(
        GdcLabelData.builder()
            .key("tiHi")
            .value(
                deliveryDocumentLine.getPalletTie() + " - " + deliveryDocumentLine.getPalletHigh())
            .build());
    labelDataList.add(
        GdcLabelData.builder().key("UPC").value(deliveryDocumentLine.getCaseUpc()).build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("vendorStockNbr")
            .value(deliveryDocumentLine.getVendorStockNumber())
            .build());
    labelDataList.add(
        GdcLabelData.builder().key("lpn5").value(StringUtils.right(trackingId, 5)).build());
    labelDataList.add(
        GdcLabelData.builder()
            .key("md")
            .value(getDcDateMMDay(configUtils.getDCTimeZone(getFacilityNum())))
            .build());

    return labelDataList;
  }

  private String determineSlot(DeliveryDocumentLine deliveryDocumentLine, String slot) {
    String profiledWarehouseArea =
        deliveryDocumentLine.getAdditionalInfo().getProfiledWarehouseArea();
    boolean isMECHAutomationType =
        CollectionUtils.isNotEmpty(witronManagedConfig.getMechGDCProfiledWarehouseAreaValues())
            && witronManagedConfig
                .getMechGDCProfiledWarehouseAreaValues()
                .contains(profiledWarehouseArea);
    boolean isNONMECHAutomationType =
        CollectionUtils.isNotEmpty(witronManagedConfig.getMechGDCProfiledWarehouseAreaValues())
            && witronManagedConfig
                .getNonMechGDCProfiledWarehouseAreaValues()
                .contains(profiledWarehouseArea);

    LOGGER.info(
        "Set label Automation type/slot for profiledWarehouseArea {}", profiledWarehouseArea);
    // For Automated grocery replace slot with MECH or NON-MECH based on profiledWarehousedArea
    if (gdcFlagReader.isAutomatedDC() && isMECHAutomationType) {
      return MECH_AUTOMATION_TYPE;
    } else if (gdcFlagReader.isAutomatedDC() && isNONMECHAutomationType) {
      return NON_MECH_AUTOMATION_TYPE;
    }
    return Objects.nonNull(slot) ? slot : "";
  }

  private List<PrintLabelRequest> buildPrintRequestList(
      String trackingId, List<GdcLabelData> labelDataList) {
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setFormatName(getPrintLabelFormat(trackingId));
    printLabelRequest.setLabelIdentifier(trackingId);
    printLabelRequest.setTtlInHours(
        configUtils.getPrintLabelTtlHrs(getFacilityNum(), PRINT_LABEL_TTL_CONFIG_KEY));
    printLabelRequest.setData(labelDataList);
    printLabelRequests.add(printLabelRequest);
    return printLabelRequests;
  }

  /**
   * This is the format should have already live in lpass
   *
   * @param trackindId
   * @return PrintLabelFormat as String
   */
  public String getPrintLabelFormat(String trackindId) {
    String labelFormat = GDC_LABEL_FORMAT_NAME;
    if (StringUtils.isNotEmpty(trackindId) && trackindId.length() > 7) {
      // TODO: remove once all component uses one atlas and use only GDC_LABEL_FORMAT_NAME_V2 format
      labelFormat = GDC_LABEL_FORMAT_NAME_V2;
      if (configUtils.getConfiguredFeatureFlag(
          String.valueOf(getFacilityNum()), IS_WITRON_LABEL_ENABLE, false)) {
        labelFormat = PRINT_LABEL_WITRON_TEMPLATE;
      } else if (configUtils.getConfiguredFeatureFlag(
          String.valueOf(getFacilityNum()), IS_GDC_LABEL_V3_ENABLE, false)) {
        labelFormat = GDC_LABEL_FORMAT_NAME_V3;
      }
    } else if (configUtils.getConfiguredFeatureFlag(
        String.valueOf(getFacilityNum()), IS_LPN_7_DIGIT_ENABLED, false)) {
      labelFormat = GDC_LABEL_FORMAT_7_DIGIT;
    }
    // TODO: remove once all component uses one atlas
    return labelFormat;
  }

  private ContainerLabel buildContainerLabel(
      HttpHeaders httpHeaders, List<PrintLabelRequest> printLabelRequests) {
    Map<String, String> headers = ContainerUtils.getPrintRequestHeaders(httpHeaders);

    ContainerLabel containerLabel = new ContainerLabel();
    containerLabel.setClientId(ReceivingConstants.RECEIVING);
    containerLabel.setHeaders(headers);
    containerLabel.setPrintRequests(printLabelRequests);

    LOGGER.info("Returning containerLabel v2: {}", containerLabel);
    return containerLabel;
  }

  private String getDcDateMMDay(String dcTimeZone) {
    dcTimeZone = isBlank(dcTimeZone) ? UTC_TIME_ZONE : dcTimeZone;
    final ZoneId dcZoneId = ZoneId.of(dcTimeZone);
    final Instant nowInUtc = Instant.now();
    final ZonedDateTime dcDateTime = ZonedDateTime.ofInstant(nowInUtc, dcZoneId);

    // convert dcDateTime to Print Label format
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PRINT_LABEL_MONTH_DAY);
    return dcDateTime.format(formatter);
  }
}
