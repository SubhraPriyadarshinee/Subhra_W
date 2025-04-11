package com.walmart.move.nim.receiving.fixture.utils;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.model.PalletReceiveRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

public class FixtureSlottingUtils {

  public static SlottingPalletRequest prepareSlottingPalletRequest(
      List<Map<String, Object>> foundItems,
      Container containerDetails,
      List<ContainerItem> containerItemList) {
    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    Map<String, Object> itemMDMDCPropertiesSupplyItemDetails =
        ItemMDMUtils.getItemMDMDCPropertiesSupplyItemDetails(foundItems);
    Map<String, Object> itemMDMDDimensionDetails =
        ItemMDMUtils.getItemMDMDDimensionDetails(itemMDMDCPropertiesSupplyItemDetails);
    Map<String, Object> itemMDMWeightDetails =
        ItemMDMUtils.getItemMDMWeightDetails(itemMDMDCPropertiesSupplyItemDetails);
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
    slottingContainerItemDetails.setItemNbr(containerItemList.get(0).getItemNumber());
    slottingContainerItemDetails.setQty(containerDetails.getContainerItems().get(0).getQuantity());
    slottingContainerItemDetails.setQtyUom(
        String.valueOf(itemMDMDDimensionDetails.get(FixtureConstants.ITEM_MDM_UOM)));
    slottingContainerItemDetails.setVnpkRatio(FixtureConstants.DEFAULT_VNPK_QTY);
    slottingContainerItemDetails.setWhpkRatio(FixtureConstants.DEFAULT_WHPK_QTY);
    slottingContainerItemDetails.setWareHouseHi(FixtureConstants.DEFAULT_WAREHOUSE_HI);
    slottingContainerItemDetails.setWareHouseTi(FixtureConstants.DEFAULT_WAREHOUSE_TI);
    slottingContainerItemDetails.setWhpkHeight(
        (Double) itemMDMDDimensionDetails.get(FixtureConstants.ITEM_MDM_HEIGHT));
    slottingContainerItemDetails.setWhpkWeight(
        ((Double) itemMDMWeightDetails.get(FixtureConstants.ITEM_MDM_AMOUNT)).floatValue());
    slottingContainerItemDetails.setWhpkLength(
        (Double) itemMDMDDimensionDetails.get(FixtureConstants.ITEM_MDM_DEPTH));
    slottingContainerItemDetails.setWhpkWidth(
        (Double) itemMDMDDimensionDetails.get(FixtureConstants.ITEM_MDM_WIDTH));
    slottingContainerItemDetails.setRotateDate(FixtureConstants.ITEM_MDM_ROTATE_DATE);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    slottingContainerDetails.setContainerTrackingId(containerDetails.getTrackingId());
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setMessageId(UUID.randomUUID().toString());
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);
    slottingPalletRequest.setReceivingMethod(FixtureConstants.ReceivingMethod);

    return slottingPalletRequest;
  }

  public static SlottingPalletRequest slottingPalletRequest(
      List<Map<String, Object>> foundItems,
      Container containerDetails,
      List<ContainerItem> containerItemList,
      FixtureManagedConfig fixtureManagedConfig,
      PalletReceiveRequest palletReceiveRequest) {

    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    SlottingContainerItemDetails slottingContainerItemDetails = new SlottingContainerItemDetails();
    List<SlottingContainerDetails> slottingContainerDetailsList = new ArrayList<>();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
    Map<String, Object> itemMDMDCPropertiesSupplyItemDetails =
        ItemMDMUtils.getItemMDMDCPropertiesSupplyItemDetails(foundItems);
    List<Map<String, Object>> itemMDMDCPropertiesTradeDetails =
        ItemMDMUtils.getItemMDMTradeDetails(itemMDMDCPropertiesSupplyItemDetails);
    slottingContainerItemDetails.setItemUPC(
        (String) itemMDMDCPropertiesTradeDetails.get(0).get(FixtureConstants.GTIN));
    slottingContainerItemDetails.setFinancialReportingGroup(FixtureConstants.REPORTING_GROUP);
    slottingContainerItemDetails.setQtyUom(FixtureConstants.UOM);
    slottingContainerItemDetails.setQty(containerDetails.getContainerItems().get(0).getQuantity());
    slottingContainerItemDetails.setBaseDivisionCode(FixtureConstants.BASE_DIVISION_CODE);
    slottingContainerItemDetails.setItemNbr(containerItemList.get(0).getItemNumber());
    slottingContainerItemDetailsList.add(slottingContainerItemDetails);
    slottingContainerDetails.setContainerName(palletReceiveRequest.getContainerName());
    slottingContainerDetails.setContainerType(FixtureConstants.CONTAINER_TYPE);
    slottingContainerDetails.setContainerTrackingId(containerDetails.getTrackingId());
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingContainerDetailsList.add(slottingContainerDetails);
    slottingPalletRequest.setContainerDetails(slottingContainerDetailsList);
    slottingPalletRequest.setMessageId(UUID.randomUUID().toString());
    slottingPalletRequest.setGenerateMove(fixtureManagedConfig.isMoveRequired());

    String stageLocationName = palletReceiveRequest.getStageLocationName();
    slottingPalletRequest.setSourceLocation(
        StringUtils.isNoneEmpty(stageLocationName)
            ? stageLocationName
            : fixtureManagedConfig.getReceivingDock());
    return slottingPalletRequest;
  }
}
