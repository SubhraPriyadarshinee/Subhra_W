package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.XDK1;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.XDK2;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ei.InventoryDetails;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RdcContainerUtils {

  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Autowired private SlottingServiceImpl slottingService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerService containerService;
  @Autowired private RdcDcFinUtils rdcDcFinUtils;
  @Autowired private MovePublisher movePublisher;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Autowired(required = false)
  private EIService eiService;

  @Autowired private InventoryTransformer inventoryTransformer;

  public static final Logger LOGGER = LoggerFactory.getLogger(RdcContainerUtils.class);

  public List<ContainerItem> buildContainerItem(
      String labelTrackingId,
      DeliveryDocument deliveryDocument,
      Integer receivedQuantity,
      String containerDestType) {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    populateContainerItem(
        labelTrackingId,
        deliveryDocument,
        receivedQuantity,
        containerItem,
        null,
        null,
        containerDestType);
    containerItems.add(containerItem);
    return containerItems;
  }

  public ContainerItem buildContainerItemDetails(
      String labelTrackingId,
      DeliveryDocument deliveryDocument,
      Integer receivedQuantity,
      ContainerItem containerItem,
      String storeAlignment,
      List<Distribution> distributions,
      String containerDestType) {
    if (Objects.nonNull(containerItem)) {
      LOGGER.info("Container already exists for labelTrackingId:{}", labelTrackingId);
    }
    containerItem = Objects.nonNull(containerItem) ? containerItem : new ContainerItem();
    return populateContainerItem(
        labelTrackingId,
        deliveryDocument,
        receivedQuantity,
        containerItem,
        storeAlignment,
        distributions,
        containerDestType);
  }

  private ContainerItem populateContainerItem(
      String labelTrackingId,
      DeliveryDocument deliveryDocument,
      Integer receivedQuantity,
      ContainerItem containerItem,
      String storeAlignment,
      List<Distribution> distributions,
      String containerDestType) {

    Map<String, DeliveryDocumentLine> deliveryDocumentLineMap;
    deliveryDocumentLineMap =
        deliveryDocument
            .getDeliveryDocumentLines()
            .stream()
            .collect(
                Collectors.toMap(
                    del ->
                        del.getChildTrackingId() != null
                            ? del.getChildTrackingId()
                            : del.getTrackingId(),
                    Function.identity()));

    DeliveryDocumentLine defaultDeliveryDocumentLine =
        deliveryDocument.getDeliveryDocumentLines().get(0);
    DeliveryDocumentLine deliveryDocumentLine = null;
    if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      deliveryDocumentLine = deliveryDocumentLineMap.get(labelTrackingId);
    }

    if (deliveryDocumentLine == null) {
      deliveryDocumentLine = defaultDeliveryDocumentLine;
    }

    Integer receivedQtyInEaches = null;
    containerItem.setTrackingId(labelTrackingId);
    containerItem.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    containerItem.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
    containerItem.setTotalPurchaseReferenceQty(deliveryDocumentLine.getTotalOrderQty());

    containerItem.setItemNumber(deliveryDocumentLine.getItemNbr());
    containerItem.setGtin(deliveryDocumentLine.getGtin());
    containerItem.setItemUPC(deliveryDocumentLine.getItemUpc());
    containerItem.setCaseUPC(deliveryDocumentLine.getCaseUpc());

    /**
     * In case of DA legacy items we need to convert the received qty in eaches based on VNPK/WHPK.
     * For Atlas DA items we will be getting allocated qty as Eaches in Label instruction data, we
     * will use same. Other than DA, remaining freights will be received in Eaches by VNPK
     * conversions .
     */
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
        deliveryDocumentLine.getPurchaseRefType())) {
      if (!deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
        if (RdcUtils.isBreakPackConveyPicks(deliveryDocumentLine)) {
          receivedQtyInEaches =
              ReceivingUtils.conversionToEaches(
                  receivedQuantity,
                  ReceivingConstants.Uom.WHPK,
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        } else {
          receivedQtyInEaches =
              ReceivingUtils.conversionToEaches(
                  receivedQuantity,
                  ReceivingConstants.Uom.VNPK,
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        }
      } else {
        receivedQtyInEaches = receivedQuantity;
      }
    } else if (ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())
        && deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
      receivedQtyInEaches = receivedQuantity;
    } else {
      receivedQtyInEaches =
          ReceivingUtils.conversionToEaches(
              receivedQuantity,
              ReceivingConstants.Uom.VNPK,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
    }
    containerItem.setQuantity(receivedQtyInEaches);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    containerItem.setVnpkQty(deliveryDocumentLine.getVendorPack());
    containerItem.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    containerItem.setPoDCNumber(deliveryDocument.getPoDCNumber());
    containerItem.setDescription(deliveryDocumentLine.getDescription());
    containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    containerItem.setPurchaseCompanyId(Integer.parseInt(deliveryDocument.getPurchaseCompanyId()));
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.EACHES);
    containerItem.setVendorNbrDeptSeq(deliveryDocument.getVendorNbrDeptSeq());
    containerItem.setVnpkWgtQty(deliveryDocumentLine.getWeight());
    containerItem.setVnpkWgtUom(deliveryDocumentLine.getWeightUom());
    containerItem.setVnpkcbqty(deliveryDocumentLine.getCube());
    containerItem.setVnpkcbuomcd(deliveryDocumentLine.getCubeUom());
    containerItem.setActualTi(deliveryDocumentLine.getPalletTie());
    containerItem.setActualHi(deliveryDocumentLine.getPalletHigh());
    containerItem.setPoDeptNumber(deliveryDocument.getDeptNumber());

    if (StringUtils.isNotBlank(deliveryDocument.getDeptNumber())) {
      containerItem.setDeptNumber(Integer.parseInt(deliveryDocument.getDeptNumber()));
    }
    if (Objects.nonNull(deliveryDocumentLine.getVendorPackCost())) {
      containerItem.setVendorPackCost(deliveryDocumentLine.getVendorPackCost().doubleValue());
    }
    if (Objects.nonNull(deliveryDocumentLine.getWarehousePackSell())) {
      containerItem.setWhpkSell(deliveryDocumentLine.getWarehousePackSell().doubleValue());
    }
    if (StringUtils.isNotBlank(deliveryDocument.getVendorNumber())) {
      containerItem.setVendorNumber(Integer.parseInt(deliveryDocument.getVendorNumber()));
    }
    if (StringUtils.isNotBlank(deliveryDocumentLine.getDepartment())) {
      containerItem.setDeptNumber(Integer.parseInt(deliveryDocumentLine.getDepartment()));
    }
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getSlotType())) {
      containerItem.setSlotType(deliveryDocumentLine.getAdditionalInfo().getSlotType());
    }

    if (StringUtils.isNotBlank(deliveryDocumentLine.getPromoBuyInd())) {
      containerItem.setPromoBuyInd(deliveryDocumentLine.getPromoBuyInd());
    }
    if (Objects.nonNull(deliveryDocument.getPoTypeCode())) {
      containerItem.setPoTypeCode(deliveryDocument.getPoTypeCode());
    }

    if (Objects.nonNull(storeAlignment)
        && appConfig.getValidSymAsrsAlignmentValues().contains(storeAlignment)) {
      containerItem.setAsrsAlignment(storeAlignment);
    }

    String baseDivCode =
        StringUtils.isNotBlank(deliveryDocument.getBaseDivisionCode())
            ? deliveryDocument.getBaseDivisionCode()
            : ReceivingConstants.BASE_DIVISION_CODE;
    containerItem.setBaseDivisionCode(baseDivCode);

    String financialReportingGroupCode =
        StringUtils.isNotBlank(deliveryDocument.getFinancialReportingGroup())
            ? deliveryDocument.getFinancialReportingGroup().toUpperCase()
            : Objects.requireNonNull(TenantContext.getFacilityCountryCode()).toUpperCase();
    containerItem.setFinancialReportingGroupCode(financialReportingGroupCode);
    containerItem.setRotateDate(new Date());

    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(
        ReceivingConstants.IS_ATLAS_CONVERTED_ITEM,
        String.valueOf(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()));
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode()))
      containerItemMiscInfo.put(
          ReceivingConstants.PACK_TYPE_CODE,
          deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getHandlingCode()))
      containerItemMiscInfo.put(
          ReceivingConstants.HANDLING_CODE,
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    if (StringUtils.isNotBlank(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment())) {
      containerItem.setAsrsAlignment(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment());
      if (MapUtils.isNotEmpty(deliveryDocumentLine.getAdditionalInfo().getImages())
          && StringUtils.isNotBlank(
              deliveryDocumentLine
                  .getAdditionalInfo()
                  .getImages()
                  .get(ReceivingConstants.GDM_ITEM_IMAGE_URL_SIZE_450))) {
        containerItemMiscInfo.put(
            ReceivingConstants.IMAGE_URL,
            deliveryDocumentLine
                .getAdditionalInfo()
                .getImages()
                .get(ReceivingConstants.GDM_ITEM_IMAGE_URL_SIZE_450));
      }
    }
    containerItemMiscInfo.put(
        ReceivingConstants.SECONDARY_QTY_UOM, ReceivingConstants.Uom.DCFIN_LB_ZA);

    Boolean isMfcIndicatorEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG, false);

    String poEvent =
        RdcUtils.validateIfDestTypeIsMFC(containerDestType, isMfcIndicatorEnabled)
            ? ReceivingConstants.MFC_PO_EVENT
            : StringUtils.isNotBlank(deliveryDocumentLine.getEvent())
                ? deliveryDocumentLine.getEvent()
                : StringUtils.EMPTY;
    if (StringUtils.isNotEmpty(poEvent))
      containerItemMiscInfo.put(ReceivingConstants.PO_EVENT, poEvent);

    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);

    if (CollectionUtils.isNotEmpty(distributions)) {
      distributions.get(0).setAllocQty(containerItem.getQuantity());
      distributions.get(0).setQtyUom(ReceivingConstants.Uom.EACHES);
      containerItem.setDistributions(distributions);
    }
    return containerItem;
  }

  public Container buildContainer(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      DeliveryDocument deliveryDocument,
      String userId,
      String labelTrackingId,
      String slotId,
      String storeNumber) {
    Container container = new Container();
    getContainerInfo(
        labelTrackingId,
        null,
        false,
        updateInstructionRequest.getDoorNumber(),
        updateInstructionRequest.getDeliveryNumber(),
        slotId,
        storeNumber,
        userId,
        deliveryDocument,
        container,
        null);
    container.setInstructionId(instruction.getId());
    container.setMessageId(getUUID());
    container.setContainerType(ContainerType.PALLET.getText());
    container.setSsccNumber(instruction.getSsccNumber());
    container.setContainerMiscInfo(
        getContainerMiscInfo(
            deliveryDocument,
            updateInstructionRequest.getDeliveryNumber(),
            container,
            null,
            Collections.emptyList()));

    return container;
  }

  public Container buildContainer(
      String scannedLocation,
      Long instructionId,
      Long deliveryNumber,
      String messageId,
      DeliveryDocument deliveryDocument,
      String userId,
      ReceivedContainer receivedContainer,
      Container container,
      ReceiveInstructionRequest receiveInstructionRequest) {
    container = Objects.nonNull(container) ? container : new Container();
    String slotId = receivedContainer.getDestinations().get(0).getSlot();
    String storeNumber = receivedContainer.getDestinations().get(0).getStore();
    if (StringUtils.isNotBlank(receivedContainer.getLabelType())) {
      container.setLabelType(receivedContainer.getLabelType());
    }

    if (StringUtils.isNotBlank(receivedContainer.getFulfillmentMethod())) {
      container.setFulfillmentMethod(receivedContainer.getFulfillmentMethod());
    }

    // these fields are needed for PUT inner picks details upon sorter divert through JMS
    if (StringUtils.isNotBlank(receivedContainer.getLabelType())
        && container.getLabelType().equals(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType())
        && !tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false)) {
      if (Objects.nonNull(receivedContainer.getBatch())) {
        container.setPrintBatch(receivedContainer.getBatch().toString());
      }
      if (Objects.nonNull(receivedContainer.getPickBatch())) {
        container.setPickBatch(receivedContainer.getPickBatch().toString());
      }
      if (StringUtils.isNotBlank(receivedContainer.getAisle())) {
        container.setAisle(receivedContainer.getAisle());
      }
    }

    getContainerInfo(
        receivedContainer.getLabelTrackingId(),
        receivedContainer.getParentTrackingId(),
        receivedContainer.isRoutingLabel(),
        scannedLocation,
        deliveryNumber,
        slotId,
        storeNumber,
        userId,
        deliveryDocument,
        container,
        receivedContainer);
    container.setMessageId(getUUID());
    container.setShipmentId(receivedContainer.getAsnNumber());
    if (rdcInstructionUtils.isCasePackPalletReceiving(
        deliveryDocument.getDeliveryDocumentLines().get(0))) {
      if (!deliveryDocument
          .getDeliveryDocumentLines()
          .get(0)
          .getAdditionalInfo()
          .isAtlasConvertedItem()) {
        container.setContainerType(ContainerType.CASE.getText());
      } else {
        container.setContainerType(ContainerType.PALLET.getText());
      }
    } else if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
      container.setContainerType(ContainerType.PALLET.getText());
    } else if (Objects.nonNull(deliveryDocument.getEventType())
        && EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      setContainerTypeForOffline(deliveryDocument, receivedContainer, container);
    } else if (rdcInstructionUtils.isDADocument(deliveryDocument)) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      if (Objects.nonNull(receivedContainer.getInventoryLabelType())) {
        container.setContainerType(
            getContainerTypeForAtlasDaItem(receivedContainer, deliveryDocumentLine));
      } else {
        container.setContainerType(
            getContainerTypeForNonAtlasDaItem(
                deliveryDocumentLine, receiveInstructionRequest, slotId));
      }
    } else {
      // DSDC
      populateContainerType(receivedContainer, container);
    }

    container.setContainerMiscInfo(
        getContainerMiscInfo(
            deliveryDocument,
            deliveryNumber,
            container,
            receivedContainer.getDestType(),
            receivedContainer.getContainerTags()));
    if (Objects.nonNull(instructionId)) {
      container.setInstructionId(instructionId);
    }
    return container;
  }

  /**
   * if container type from OP has come as REPACK assign it, otherwise follow existing logic
   *
   * @param deliveryDocument
   * @param receivedContainer
   * @param container
   */
  private static void setContainerTypeForOffline(
      DeliveryDocument deliveryDocument, ReceivedContainer receivedContainer, Container container) {
    if (StringUtils.isNotBlank(receivedContainer.getParentTrackingId())) {
      container.setContainerType(ContainerType.VIRTUAL.getText());
    } else if (StringUtils.isBlank(receivedContainer.getParentTrackingId())
        && ReceivingConstants.REPACK.equals(deliveryDocument.getCtrType())) {
      container.setContainerType(ReceivingConstants.REPACK);
    } else {
      container.setContainerType(ContainerType.CASE.getText());
    }
  }

  /**
   * Populate container type for DSDC containers
   *
   * @param receivedContainer
   * @param container
   */
  private void populateContainerType(ReceivedContainer receivedContainer, Container container) {
    if (Objects.nonNull(receivedContainer.getParentTrackingId())) {
      container.setContainerType(ContainerType.VIRTUAL.getText());
    } else {
      container.setContainerType(ContainerType.SORTATIONS.getText());
    }
  }

  /**
   * @param deliveryDocumentLine
   * @param receiveInstructionRequest
   * @param slotId
   * @return
   */
  private String getContainerTypeForNonAtlasDaItem(
      DeliveryDocumentLine deliveryDocumentLine,
      ReceiveInstructionRequest receiveInstructionRequest,
      String slotId) {
    String containerType = null;
    if (rdcReceivingUtils.isWhpkReceiving(deliveryDocumentLine, receiveInstructionRequest)) {
      containerType = ContainerType.VIRTUAL.getText();
    } else if (isVendorPackForNonAtlasItem(slotId)) {
      containerType = ContainerType.CASE.getText();
    } else {
      containerType = ContainerType.PALLET.getText();
    }
    return containerType;
  }

  /**
   * This method returns the container type based on the label type for Atlas DA items
   *
   * @param receivedContainer
   * @param deliveryDocumentLine
   * @return
   */
  private String getContainerTypeForAtlasDaItem(
      ReceivedContainer receivedContainer, DeliveryDocumentLine deliveryDocumentLine) {
    String containerType = null;
    String packType = deliveryDocumentLine.getAdditionalInfo().getPackTypeCode();
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    if (StringUtils.isNotBlank(receivedContainer.getParentTrackingId())) {
      switch (receivedContainer.getInventoryLabelType()) {
        case DA_CON_SLOTTING:
        case R8000_DA_FULL_CASE:
          containerType = ContainerType.CASE.getText();
          break;
        case XDK1:
        case XDK2:
          containerType = ContainerType.VIRTUAL.getText();
          break;
        case DA_BREAK_PACK_INNER_PICK:
        case R8002_DSDC:
          containerType = ContainerType.VIRTUAL.getText();
          break;
        case DA_CON_AUTOMATION_SLOTTING:
          containerType = ContainerType.CASE.getText();
          break;
        case DA_NON_CON_SLOTTING:
          if (RdcConstants.CASE_PACK_TYPE_CODE.equals(packType)) {
            containerType = ContainerType.CASE.getText();
          } else {
            containerType = ContainerType.VIRTUAL.getText();
          }
        default:
          break;
      }
    } else {
      switch (receivedContainer.getInventoryLabelType()) {
        case R8000_DA_FULL_CASE:
          if (RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE.equals(
              itemPackAndHandlingCode)) {
            containerType = ContainerType.CASE.getText();
          } else {
            containerType =
                receivedContainer.isPalletPullByStore()
                    ? ContainerType.PALLET.getText()
                    : ContainerType.CASE.getText();
          }
          break;
        case XDK1:
        case XDK2:
          containerType = ContainerType.CASE.getText();
          break;
        case DA_BREAK_PACK_PUT_INDUCT:
          containerType = ContainerType.CASE.getText();
          break;
        case DA_NON_CON_SLOTTING:
        case DA_CON_SLOTTING:
        case DA_CON_AUTOMATION_SLOTTING:
          containerType = ContainerType.PALLET.getText();
          break;
        case R8002_DSDC:
          containerType = ContainerType.SORTATIONS.getText();
          break;
        default:
          break;
      }
    }
    return containerType;
  }

  /**
   * If the parent container is Pallet Pull then it's considered as Pallet. If the container is DA
   * Full case or Break Pack PUT induct then its considered as Vendor Pack
   *
   * @param inventoryLabelType
   * @param isPalletPullByStore
   * @return
   */
  private boolean isVendorPackForAtlasItem(String inventoryLabelType, boolean isPalletPullByStore) {
    if (StringUtils.isNotBlank(inventoryLabelType)) {
      return !isPalletPullByStore
          && (inventoryLabelType.equals(InventoryLabelType.R8000_DA_FULL_CASE.getType())
              || inventoryLabelType.equals(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType()));
    }
    return Boolean.FALSE;
  }

  private boolean isVendorPackForNonAtlasItem(String slotId) {
    if (StringUtils.isNotBlank(slotId)) {
      return RdcConstants.DA_LABEL_FORMAT_MAP.containsKey(slotId);
    }
    return Boolean.FALSE;
  }

  private Container getContainerInfo(
      String labelTrackingId,
      String parentLabelTrackingId,
      boolean isRoutingLabel,
      String doorNumber,
      Long deliveryNumber,
      String slotId,
      String storeNumber,
      String userId,
      DeliveryDocument deliveryDocument,
      Container container,
      ReceivedContainer receivedContainer) {
    boolean isAtlasConvertedItem =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem();
    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, String.valueOf(TenantContext.getFacilityNum()));
    container.setFacility(facility);

    container.setTrackingId(labelTrackingId);
    container.setParentTrackingId(parentLabelTrackingId);
    container.setLocation(doorNumber);
    container.setDeliveryNumber(deliveryNumber);
    container.setIsConveyable(deliveryDocument.getDeliveryDocumentLines().get(0).getIsConveyable());
    container.setOnConveyor(Boolean.FALSE);

    if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
      container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
      container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    } else if (isAtlasConvertedItem
        && rdcManagedConfig.isDummyDeliveryEnabled()
        && Objects.nonNull(deliveryDocument.getOriginFacilityNum())
        && (rdcManagedConfig
                .getWpmSites()
                .contains(deliveryDocument.getOriginFacilityNum().toString())
            || rdcManagedConfig
                .getRdc2rdcSites()
                .contains(deliveryDocument.getOriginFacilityNum().toString()))
        && isXdkLabelType(receivedContainer.getLabelType())) {
      LOGGER.info(
          "[XDK] updating the status to '{}' for container '{}' for delivery '{}'",
          InventoryStatus.PICKED.name(),
          receivedContainer.getLabelTrackingId(),
          receivedContainer.getDeliveryNumber());
      container.setInventoryStatus(InventoryStatus.PICKED.name());
      container.setContainerStatus(ReceivingConstants.STATUS_PICKED);
    } else {
      container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
      InventoryLabelType inventoryLabelType =
          Objects.nonNull(receivedContainer) ? receivedContainer.getInventoryLabelType() : null;
      container.setInventoryStatus(
          getInventoryStatusForDaItems(
              inventoryLabelType, slotId, isAtlasConvertedItem, isRoutingLabel));
    }

    container.setCtrReusable(Boolean.FALSE);
    if (StringUtils.isNotBlank(storeNumber)) {
      container.setCtrShippable(Boolean.TRUE);
    } else {
      container.setCtrShippable(Boolean.FALSE);
    }
    container.setCreateUser(userId);
    container.setLastChangedUser(userId);
    container.setLastChangedTs(new Date());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    if (StringUtils.isNotBlank(slotId)) {
      destination.put(ReceivingConstants.SLOT, slotId);
    }
    if (StringUtils.isNotBlank(storeNumber)) {
      destination.put(
          ReceivingConstants.COUNTRY_CODE, String.valueOf(TenantContext.getFacilityCountryCode()));
      destination.put(ReceivingConstants.BU_NUMBER, storeNumber);
    }
    container.setDestination(destination);
    return container;
  }

  /**
   * This method determines Inventory Status for DA items. If its Da Atlas items, labelType will be
   * used to determine the inventory status. If its non Atlas items, slotId will be used to
   * determine the inventory status.
   *
   * @param inventoryLabelType
   * @param slotId
   * @param isAtlasConvertedItem
   * @param isRoutingLabel
   */
  private String getInventoryStatusForDaItems(
      InventoryLabelType inventoryLabelType,
      String slotId,
      boolean isAtlasConvertedItem,
      boolean isRoutingLabel) {
    String inventoryStatus = null;
    if (isAtlasConvertedItem && Objects.nonNull(inventoryLabelType)) {
      inventoryStatus = getInventoryStatusForDaAtlasItems(inventoryLabelType, isRoutingLabel);
    } else {
      inventoryStatus = getInventoryStatusForDaNonAtlasItems(slotId);
    }
    return inventoryStatus;
  }

  private String getInventoryStatusForDaNonAtlasItems(String slotId) {
    String inventoryStatus = null;
    if (!RdcConstants.DA_LABEL_FORMAT_MAP.containsKey(slotId)) {
      inventoryStatus = InventoryStatus.AVAILABLE.name();
    } else if (RdcConstants.DA_LABEL_FORMAT_MAP.get(slotId).equals(LabelFormat.DA_STORE_FRIENDLY)
        || RdcConstants.DA_LABEL_FORMAT_MAP.get(slotId).equals(LabelFormat.DSDC)) {
      inventoryStatus = InventoryStatus.PICKED.name();
    } else {
      inventoryStatus = InventoryStatus.ALLOCATED.name();
    }
    return inventoryStatus;
  }

  /**
   * This method returns the inventory status based on the inventory label type
   *
   * @param inventoryLabelType
   * @param isRoutingLabel
   * @return
   */
  private String getInventoryStatusForDaAtlasItems(
      InventoryLabelType inventoryLabelType, boolean isRoutingLabel) {
    String inventoryStatus = null;
    switch (inventoryLabelType) {
      case DA_NON_CON_SLOTTING:
      case DA_CON_SLOTTING:
      case DA_BREAK_PACK_PUT_INDUCT:
      case DA_BREAK_PACK_INNER_PICK:
      case XDK1:
      case XDK2:
      case DA_CON_AUTOMATION_SLOTTING:
        inventoryStatus = InventoryStatus.ALLOCATED.name();
        break;
      case R8000_DA_FULL_CASE:
      case R8002_DSDC:
        inventoryStatus =
            isRoutingLabel ? InventoryStatus.ALLOCATED.name() : InventoryStatus.PICKED.name();
        break;
      default:
        break;
    }
    return inventoryStatus;
  }

  private Map<String, Object> getContainerMiscInfo(
      DeliveryDocument deliveryDocument,
      Long deliveryNumber,
      Container container,
      String destType,
      List<ContainerTag> containerTags) {
    Map<String, Object> containerMiscInfo = new HashMap<>();

    if (deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .isAtlasConvertedItem()) {
      DeliveryMetaData deliveryMetaData =
          rdcDeliveryMetaDataService.findDeliveryMetaData(deliveryNumber);

      if (Objects.nonNull(deliveryMetaData)) {
        if (StringUtils.isNotBlank(deliveryMetaData.getTrailerNumber())) {
          containerMiscInfo.put(
              ReceivingConstants.TRAILER_NUMBER, deliveryMetaData.getTrailerNumber());
        }
        if (StringUtils.isNotBlank(deliveryMetaData.getCarrierName())) {
          containerMiscInfo.put(CARRIER_NAME, deliveryMetaData.getCarrierName());
        }
        if (StringUtils.isNotBlank(deliveryMetaData.getCarrierScacCode())) {
          containerMiscInfo.put(CARRIER_SCAC_CODE, deliveryMetaData.getCarrierScacCode());
        }
        if (StringUtils.isNotBlank(deliveryMetaData.getBillCode())) {
          containerMiscInfo.put(BILL_CODE, deliveryMetaData.getBillCode());
        }
      }
    }

    if (Objects.nonNull(deliveryDocument.getProDate())) {
      containerMiscInfo.put(ReceivingConstants.PRO_DATE, deliveryDocument.getProDate());
    }
    if (Objects.nonNull(deliveryDocument.getOriginFacilityNum())) {
      containerMiscInfo.put(
          ReceivingConstants.ORIGIN_FACILITY_NUMBER, deliveryDocument.getOriginFacilityNum());
    }
    if (StringUtils.isNotBlank(deliveryDocument.getOriginType())) {
      containerMiscInfo.put(ReceivingConstants.ORIGIN_TYPE, deliveryDocument.getOriginType());
    }
    if (StringUtils.isNotBlank(deliveryDocument.getPurchaseReferenceLegacyType())) {
      containerMiscInfo.put(
          ReceivingConstants.PURCHASE_REF_LEGACY_TYPE,
          deliveryDocument.getPurchaseReferenceLegacyType());
    }
    if (StringUtils.isNotBlank(container.getLabelType())) {
      containerMiscInfo.put(ReceivingConstants.INVENTORY_LABEL_TYPE, container.getLabelType());
    }
    if (StringUtils.isNotBlank(container.getFulfillmentMethod())) {
      containerMiscInfo.put(
          ReceivingConstants.OP_FULFILLMENT_METHOD, container.getFulfillmentMethod());
    }
    if (StringUtils.isNotBlank(deliveryDocument.getChannelMethod())) {
      containerMiscInfo.put(ReceivingConstants.CHANNEL_METHOD, deliveryDocument.getChannelMethod());
    }

    if (Objects.nonNull(deliveryDocument.getMessageNumber())) {
      containerMiscInfo.put(ReceivingConstants.MESSAGE_NUMBER, deliveryDocument.getMessageNumber());
    } else if (Objects.nonNull(deliveryDocument.getDeliveryDocumentLines())) {
      setMessageNumberForChildContainer(deliveryDocument, container, containerMiscInfo);
    }
    if (Objects.nonNull(deliveryDocument.getPalletId())) {
      containerMiscInfo.put(ReceivingConstants.PALLET_ID, deliveryDocument.getPalletId());
    }
    // these fields are needed for PUT inner picks details upon sorter divert
    if (StringUtils.isNotBlank(container.getPickBatch())) {
      containerMiscInfo.put(ReceivingConstants.STORE_PICK_BATCH, container.getPickBatch());
    }
    if (StringUtils.isNotBlank(container.getPrintBatch())) {
      containerMiscInfo.put(ReceivingConstants.STORE_PRINT_BATCH, container.getPrintBatch());
    }
    if (StringUtils.isNotBlank(container.getAisle())) {
      containerMiscInfo.put(ReceivingConstants.STORE_AISLE, container.getAisle());
    }

    if (StringUtils.isNotBlank(destType)) {
      containerMiscInfo.put(ReceivingConstants.DEST_TYPE, destType);
    }

    if (CollectionUtils.isNotEmpty(containerTags)) {
      containerMiscInfo.put(
          ReceivingConstants.CONTAINER_TAG, ReceivingUtils.stringfyJson(containerTags));
    }
    return containerMiscInfo;
  }

  /**
   * Set message number for child containers (rewrite containers from WPM) - from document line
   *
   * @param deliveryDocument
   * @param container
   * @param containerMiscInfo
   */
  private static void setMessageNumberForChildContainer(
      DeliveryDocument deliveryDocument,
      Container container,
      Map<String, Object> containerMiscInfo) {
    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      if (Objects.nonNull(deliveryDocumentLine.getChildTrackingId())
          && Objects.nonNull(deliveryDocumentLine.getMessageNumber())
          && deliveryDocumentLine
              .getChildTrackingId()
              .equalsIgnoreCase(container.getTrackingId())) {
        containerMiscInfo.put(
            ReceivingConstants.MESSAGE_NUMBER, deliveryDocumentLine.getMessageNumber());
        LOGGER.info(
            "Message number: {} set against tracking id: {} for delivery number : {}",
            deliveryDocumentLine.getMessageNumber(),
            container.getTrackingId(),
            deliveryDocument.getDeliveryNumber());
        break;
      }
    }
  }

  public ContainerDetails getContainerDetails(
      String labelTrackingId,
      Map<String, Object> printLabelData,
      ContainerType containerType,
      String outboundChannelMethod) {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(labelTrackingId);
    containerDetails.setCtrType(containerType.getText());
    containerDetails.setInventoryStatus(InventoryStatus.PICKED.name());
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrLabel(getLabelData(printLabelData));
    containerDetails.setCtrStatus(ReceivingConstants.STATUS_COMPLETE);
    containerDetails.setOutboundChannelMethod(outboundChannelMethod);
    return containerDetails;
  }

  private Map<String, Object> getLabelData(Map<String, Object> printLabelData) {
    Map<String, Object> labelData = new HashMap<>();
    labelData.put(
        ReceivingConstants.PRINT_HEADERS_KEY,
        printLabelData.get(ReceivingConstants.PRINT_HEADERS_KEY));
    labelData.put(
        ReceivingConstants.PRINT_CLIENT_ID_KEY,
        printLabelData.get(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    List<PrintLabelRequest> printLabelRequestList =
        (List<PrintLabelRequest>) printLabelData.get(ReceivingConstants.PRINT_REQUEST_KEY);

    PrintLabelRequest printLabelRequest = checkIfSSTKPalletLabel(printLabelRequestList);
    if (Objects.nonNull(printLabelRequest)) {
      labelData.put(
          ReceivingConstants.PRINT_REQUEST_KEY, Collections.singletonList(printLabelRequest));
    } else {
      labelData.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequestList);
    }
    return labelData;
  }

  private PrintLabelRequest checkIfSSTKPalletLabel(List<PrintLabelRequest> printLabelRequestList) {
    return LabelGenerator.duplicateSSTKPalletLabel(printLabelRequestList);
  }

  public void setLocationHeaders(HttpHeaders httpHeaders, LocationInfo locationInfo) {
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, locationInfo.getLocationId());
    httpHeaders.add(
        RdcConstants.WFT_LOCATION_TYPE,
        locationInfo.getLocationType()
            + ReceivingConstants.DELIM_DASH
            + locationInfo.getLocationId());
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, locationInfo.getSccCode());
  }

  public void applyReceivingCorrections(
      Container container4mDB, Integer adjustByQtyInEaches, HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelContainerResponse cancelContainerResponse = null;
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    cancelContainerResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(container4mDB, httpHeaders);
    if (Objects.isNull(cancelContainerResponse)) {
      ContainerItem currentContainerItem = container4mDB.getContainerItems().get(0);
      Integer updatedContainerQuantityInEaches =
          adjustByQtyInEaches > 0
              ? currentContainerItem.getQuantity() - adjustByQtyInEaches
              : currentContainerItem.getQuantity() + adjustByQtyInEaches;

      if (ContainerUtils.isAtlasConvertedItem(currentContainerItem)) {
        // publish pallet adjustment message to hawkeye
        boolean isSymPutAwayEligible =
            SymboticUtils.isValidForSymPutaway(
                currentContainerItem.getAsrsAlignment(),
                appConfig.getValidSymAsrsAlignmentValues(),
                currentContainerItem.getSlotType());
        if (isSymPutAwayEligible) {
          symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
              container4mDB.getTrackingId(),
              currentContainerItem,
              ReceivingConstants.PUTAWAY_UPDATE_ACTION,
              updatedContainerQuantityInEaches,
              httpHeaders);
        }

        final Integer adjustByQtyInVnpk =
            ReceivingUtils.conversionToVendorPack(
                updatedContainerQuantityInEaches * -1,
                ReceivingConstants.Uom.EACHES,
                currentContainerItem.getVnpkQty(),
                currentContainerItem.getWhpkQty());
        Receipt receipt =
            containerAdjustmentHelper.adjustQuantityInReceipt(
                adjustByQtyInVnpk, ReceivingConstants.Uom.VNPK, container4mDB, userId);

        Integer updatedContainerItemQty =
            adjustByQtyInEaches > 0
                ? currentContainerItem.getQuantity() - adjustByQtyInEaches
                : currentContainerItem.getQuantity() + adjustByQtyInEaches;
        Container adjustedContainer =
            containerAdjustmentHelper.adjustPalletQuantity(
                updatedContainerItemQty, container4mDB, userId);
        containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(receipt, adjustedContainer);

        // Update adjusted quantity in instruction table
        Instruction instruction =
            instructionPersisterService.getInstructionById(container4mDB.getInstructionId());
        instruction.setReceivedQuantity(adjustByQtyInVnpk * -1);
        instructionPersisterService.saveInstruction(instruction);
      } else {
        LOGGER.info(
            "Container: {} has non-atlas converted item, so skipping this receiving adjustment ....",
            container4mDB.getTrackingId());
        return;
      }
    } else {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.EXCEPTION_ON_PROCESSING_INV_ADJUSTMENT_MSG,
              cancelContainerResponse.getTrackingId(),
              cancelContainerResponse.getErrorCode());
      LOGGER.error(
          "Error in applying receiving adjustments to container: {} and error: {}",
          container4mDB.getTrackingId(),
          errorDescription);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVENTORY_ADJUSTMENT_MSG_PROCESSING_ERROR,
          container4mDB.getTrackingId(),
          errorDescription);
    }
  }

  public void backoutContainer(Container container, HttpHeaders headers) throws ReceivingException {
    CancelContainerResponse cancelContainerResponse = null;
    cancelContainerResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(container, headers);
    if (Objects.isNull(cancelContainerResponse)) {
      ContainerItem containerItem = container.getContainerItems().get(0);
      if (ContainerUtils.isAtlasConvertedItem(containerItem)) {
        // publish putaway cancellation message to hawkeye
        boolean isSymPutawayEligible =
            SymboticUtils.isValidForSymPutaway(
                containerItem.getAsrsAlignment(),
                appConfig.getValidSymAsrsAlignmentValues(),
                containerItem.getSlotType());
        if (isSymPutawayEligible) {
          symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
              container.getTrackingId(),
              containerItem,
              ReceivingConstants.PUTAWAY_DELETE_ACTION,
              ReceivingConstants.ZERO_QTY,
              headers);
        }
        // update container and receipt
        Receipt adjustedReceipt = containerAdjustmentHelper.adjustReceipts(container);
        container.getContainerItems().get(0).setQuantity(0);
        container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
        containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(adjustedReceipt, container);
      } else {
        LOGGER.info(
            "Backout container: Item: {} belongs to lpn: {} is not atlas converted",
            containerItem.getItemNumber(),
            container.getTrackingId());
      }
      // Update adjusted quantity in instruction table
      rdcInstructionUtils.updateInstructionQuantity(
          container.getInstructionId(), ReceivingConstants.ZERO_QTY);
    } else {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.EXCEPTION_ON_PROCESSING_INV_ADJUSTMENT_MSG,
              cancelContainerResponse.getTrackingId(),
              cancelContainerResponse.getErrorCode());
      LOGGER.error(
          "Error in applying VTR adjustments to container: {} and error: {}",
          container.getTrackingId(),
          errorDescription);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVENTORY_ADJUSTMENT_MSG_PROCESSING_ERROR,
          container.getTrackingId(),
          errorDescription);
    }
  }

  @Transactional(readOnly = true)
  public int receivedContainerQuantityBySSCC(String ssccNumber) {
    return containerPersisterService.receivedContainerQuantityBySSCCAndStatus(ssccNumber);
  }

  @Transactional(readOnly = true)
  public boolean isContainerReceivedBySSCCAndItem(String ssccNumber, Long itemNumber) {
    boolean isContainerReceivedBySSCCAndItem = false;
    Optional<List<Long>> receivedContainerDetailsOptional =
        containerPersisterService.receivedContainerDetailsBySSCC(ssccNumber);
    if (receivedContainerDetailsOptional.isPresent()) {
      List<Long> receivedContainerItems = receivedContainerDetailsOptional.get();
      if (receivedContainerItems.contains(itemNumber)) {
        isContainerReceivedBySSCCAndItem = true;
      }
    }
    return isContainerReceivedBySSCCAndItem;
  }

  /**
   * This method processes warehouse damage adjustment message that was consumed from inventory.
   *
   * @param container4mDB
   * @param adjustByQtyInEaches
   * @param httpHeaders
   */
  public void processWarehouseDamageAdjustments(
      Container container4mDB, Integer adjustByQtyInEaches, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse = null;

    cancelContainerResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(container4mDB, httpHeaders);
    if (Objects.isNull(cancelContainerResponse)) {
      ContainerItem currentContainerItem = container4mDB.getContainerItems().get(0);
      Integer updatedContainerQuantityInEaches =
          adjustByQtyInEaches > 0
              ? currentContainerItem.getQuantity() - adjustByQtyInEaches
              : currentContainerItem.getQuantity() + adjustByQtyInEaches;

      /**
       * If item is atlas converted item and SYM eligible then putaway delete or update message will
       * be published to Hawkeye based on the damaged quantity value.
       */
      if (ContainerUtils.isAtlasConvertedItem(currentContainerItem)) {
        boolean isSymPutAwayEligible =
            SymboticUtils.isValidForSymPutaway(
                currentContainerItem.getAsrsAlignment(),
                appConfig.getValidSymAsrsAlignmentValues(),
                currentContainerItem.getSlotType());
        if (isSymPutAwayEligible) {
          if (updatedContainerQuantityInEaches == 0) {
            LOGGER.info(
                "Publishing putaway delete message to Hawkeye for trackingId: {}",
                container4mDB.getTrackingId());
            symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
                container4mDB.getTrackingId(),
                currentContainerItem,
                ReceivingConstants.PUTAWAY_DELETE_ACTION,
                ReceivingConstants.ZERO_QTY,
                httpHeaders);
          } else {
            LOGGER.info(
                "Publishing putaway update message to Hawkeye for trackingId: {}",
                container4mDB.getTrackingId());
            symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
                container4mDB.getTrackingId(),
                currentContainerItem,
                ReceivingConstants.PUTAWAY_UPDATE_ACTION,
                updatedContainerQuantityInEaches,
                httpHeaders);
          }
        } else {
          LOGGER.info(
              "Container: {} has item: {} which is not SYM eligible, so receiving corrections are not published to Hawkeye  ...",
              container4mDB.getTrackingId(),
              currentContainerItem.getItemNumber());
          return;
        }
      } else {
        LOGGER.info(
            "Container: {} has item: {} which is non-atlas converted item, so skipping this receiving adjustment ....",
            container4mDB.getTrackingId(),
            currentContainerItem.getItemNumber());
        return;
      }
    } else {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.EXCEPTION_ON_PROCESSING_INV_ADJUSTMENT_MSG,
              cancelContainerResponse.getTrackingId(),
              cancelContainerResponse.getErrorCode());
      LOGGER.error(
          "Error in applying warehouse damage adjustments to container: {} and error: {}",
          container4mDB.getTrackingId(),
          errorDescription);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVENTORY_ADJUSTMENT_MSG_PROCESSING_ERROR,
          container4mDB.getTrackingId(),
          errorDescription);
    }
  }

  /** @param container */
  public void publishContainersToInventory(Container container) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
        false)) {
      containerService.publishMultipleContainersToInventory(
          transformer.transformList(Arrays.asList(container)));
    }
  }

  /** @param containers */
  public void publishContainersToInventory(List<Container> containers) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
        false)) {
      containerService.publishMultipleContainersToInventory(transformer.transformList(containers));
    }
  }

  /**
   * @param container
   * @param purchaseReferenceLegacyType
   */
  public void postReceiptsToDcFin(Container container, String purchaseReferenceLegacyType) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
        false)) {
      rdcDcFinUtils.postToDCFin(Collections.singletonList(container), purchaseReferenceLegacyType);
    }
  }

  /**
   * @param fromLocation
   * @param receivedQty
   * @param moveTreeMap
   * @param httpHeaders
   */
  public void publishMove(
      String fromLocation,
      int receivedQty,
      LinkedTreeMap<String, Object> moveTreeMap,
      HttpHeaders httpHeaders) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_MOVE_PUBLISH_ENABLED, false)) {
      movePublisher.publishMove(receivedQty, fromLocation, moveTreeMap, httpHeaders);
    }
  }

  /**
   * @param deliveryDocument
   * @param receivedContainer
   * @param instruction4mDB
   * @param httpHeaders
   */
  public void publishPutawayMessageToHawkeye(
      DeliveryDocument deliveryDocument,
      ReceivedContainer receivedContainer,
      Instruction instruction4mDB,
      HttpHeaders httpHeaders) {
    SymFreightType symFreightType = null;
    if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
      symFreightType = SymFreightType.SSTK;
    } else if (rdcInstructionUtils.isDADocument(deliveryDocument)) {
      symFreightType = SymFreightType.DA;
    }
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        receivedContainer, deliveryDocument, instruction4mDB, symFreightType, httpHeaders);
  }

  /**
   * @param dsdcReceiveResponse
   * @param labelTrackingId
   * @param receivedQuantity
   * @param containerItem
   * @return
   */
  public List<ContainerItem> buildContainerItem(
      DsdcReceiveResponse dsdcReceiveResponse,
      String labelTrackingId,
      Integer receivedQuantity,
      ContainerItem containerItem) {
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId(labelTrackingId);
    containerItem.setPurchaseReferenceNumber(dsdcReceiveResponse.getPo_nbr());
    containerItem.setInboundChannelMethod(ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItem.setQuantity(receivedQuantity);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.VNPK);
    // description needed when we display containers in Reprint label screen
    containerItem.setDescription(ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItem.setPoDCNumber(TenantContext.getFacilityNum().toString());

    if (StringUtils.isNotBlank(dsdcReceiveResponse.getDept())) {
      containerItem.setDeptNumber(Integer.parseInt(dsdcReceiveResponse.getDept()));
    }

    if (Objects.nonNull(dsdcReceiveResponse.getPocode())) {
      containerItem.setPoTypeCode(Integer.parseInt(dsdcReceiveResponse.getPocode()));
    }

    String baseDivCode =
        StringUtils.isNotBlank(dsdcReceiveResponse.getDiv())
            ? dsdcReceiveResponse.getDiv()
            : ReceivingConstants.BASE_DIVISION_CODE;
    containerItem.setBaseDivisionCode(baseDivCode);

    String financialReportingGroupCode =
        Objects.requireNonNull(TenantContext.getFacilityCountryCode()).toUpperCase();
    containerItem.setFinancialReportingGroupCode(financialReportingGroupCode);
    containerItem.setRotateDate(new Date());

    containerItems.add(containerItem);
    return containerItems;
  }
  // Build Container for DSDC with minimal information

  /**
   * This method will take care of building the Container for DSDC Receiving, as RDS response has
   * very minimal information only.
   *
   * @param instructionRequest
   * @param dsdcReceiveResponse
   * @param instructionId
   * @param container
   * @return
   */
  public Container buildContainer(
      InstructionRequest instructionRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      Long instructionId,
      String userId,
      Container container) {
    container = getContainerInfo(instructionRequest, dsdcReceiveResponse, userId, container);
    container.setMessageId(getUUID());
    container.setContainerType(ContainerType.CASE.name());
    if (Objects.nonNull(instructionId)) {
      container.setInstructionId(instructionId);
    }
    return container;
  }

  /**
   * This method will build the container Information for DSDC Store Label
   *
   * @param instructionRequest
   * @param dsdcReceiveResponse
   * @param userId
   * @return
   */
  private Container getContainerInfo(
      InstructionRequest instructionRequest,
      DsdcReceiveResponse dsdcReceiveResponse,
      String userId,
      Container container) {
    if (Objects.nonNull(container)) {
      LOGGER.info(
          "DSDC container already exists for labelTrackingId:{}",
          dsdcReceiveResponse.getLabel_bar_code());
    }
    container = Objects.nonNull(container) ? container : new Container();
    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, String.valueOf(TenantContext.getFacilityNum()));
    container.setFacility(facility);
    container.setSsccNumber(instructionRequest.getSscc());
    container.setTrackingId(dsdcReceiveResponse.getLabel_bar_code());
    container.setLocation(instructionRequest.getDoorNumber());
    container.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
    container.setOnConveyor(Boolean.FALSE);
    container.setIsConveyable(Boolean.TRUE);
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrShippable(Boolean.TRUE);
    container.setCreateUser(userId);
    container.setLastChangedUser(userId);
    container.setLastChangedTs(new Date());
    container.setCreateTs(new Date());
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, dsdcReceiveResponse.getSlot());
    destination.put(RdcConstants.DA_DESTINATION_STORE_NUMBER, dsdcReceiveResponse.getStore());
    container.setDestination(destination);
    return container;
  }

  /**
   * Publish container to EI. If a container has child containers (Break Pack) we need to publish
   * child containers only to EI else we can publish the parent container (Case pack)
   *
   * @param container
   * @param transformTypeInput
   */
  public void publishContainerToEI(Container container, String... transformTypeInput) {
    Arrays.stream(transformTypeInput)
        .forEach(
            transformType -> {
              try {
                if (CollectionUtils.isNotEmpty(container.getChildContainers())) {
                  container
                      .getChildContainers()
                      .forEach(childContainer -> publishToEI(childContainer, transformType));
                } else {
                  publishToEI(container, transformType);
                }
              } catch (Exception exception) {
                LOGGER.error(
                    "Exception occur while publishing {} container to ei: {}",
                    container.getTrackingId(),
                    ExceptionUtils.getStackTrace(exception));
              }
            });
  }

  /**
   * @param container
   * @param transformType
   */
  private void publishToEI(Container container, String transformType) {
    InventoryDetails dcReceivingInventoryDetails =
        inventoryTransformer.transformToInventory(container, transformType);
    eiService.publishContainerToEI(container, dcReceivingInventoryDetails, transformType);
  }

  /**
   * Tracking ID conversion from 18 to 16 digit
   *
   * @param trackingId - the container label that requires conversion
   */
  public String convertEighteenToSixteenDigitLabel(String trackingId) {
    if (trackingId == null) {
      return null;
    }
    if (trackingId.length() == RdcConstants.EIGHTEEN_LENGTH) {
      StringBuilder trackingIdSixteen = new StringBuilder();
      String storeNum = trackingId.substring(1, 5);
      String divNum = trackingId.substring(5, 7);
      String originDCNum = trackingId.substring(8, 12);
      String shipUnitNum = trackingId.substring(12, 18);
      trackingIdSixteen.append(storeNum);
      trackingIdSixteen.append(divNum);
      trackingIdSixteen.append(originDCNum);
      trackingIdSixteen.append(shipUnitNum);
      return trackingIdSixteen.toString();
    } else {
      return trackingId;
    }
  }

  /**
   * This method check the label Type if for offline or not
   *
   * @param labelType
   * @return
   */
  protected boolean isXdkLabelType(String labelType) {
    return Objects.nonNull(labelType) && (XDK1.equals(labelType) || (XDK2.equals(labelType)));
  }

  private String getUUID() {
    return UUID.randomUUID().toString();
  }

  /**
   * Convert the proDate format to the one supported by DcFin
   *
   * @param container
   * @param trackingId
   * @return Container
   */
  public Container convertDateFormatForProDate(Container container, String trackingId)
      throws ReceivingException {

    if (Objects.nonNull(container.getContainerMiscInfo())
        && Objects.nonNull(container.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE))) {
      String proDate =
          ReceivingUtils.convertDateFormat(
              String.valueOf(container.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE)));
      container.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, proDate);
    }

    Set<Container> childContainerList = container.getChildContainers();

    if (!CollectionUtils.isEmpty(childContainerList)) {
      for (Container childContainer : childContainerList) {
        if (Objects.nonNull(childContainer.getContainerMiscInfo())
            && Objects.nonNull(
                childContainer.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE))) {
          String proDate =
              ReceivingUtils.convertDateFormat(
                  String.valueOf(
                      childContainer.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE)));
          childContainer.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, proDate);
        }

        if (Objects.nonNull(childContainer.getContainerMiscInfo())
            && Objects.isNull(
                childContainer.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE))) {
          SimpleDateFormat inputDateFormat = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");
          String formattedDate = inputDateFormat.format(new Date());
          String proDate = ReceivingUtils.convertDateFormat(formattedDate);
          childContainer.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, proDate);
        }
      }
    }

    if (Objects.nonNull(container.getContainerMiscInfo())
        && Objects.isNull(container.getContainerMiscInfo().get(ReceivingConstants.PRO_DATE))) {
      SimpleDateFormat inputDateFormat = new SimpleDateFormat("MMM dd, yyyy h:mm:ss a");
      String formattedDate = inputDateFormat.format(new Date());
      String proDate = ReceivingUtils.convertDateFormat(formattedDate);
      container.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, proDate);
    }

    return container;
  }
}
