package com.walmart.move.nim.receiving.fixture.utils;

import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.CollectionUtils;

public class ItemMDMUtils {

  public static Map<String, Object> getItemMDMDCPropertiesSupplyItemDetails(
      List<Map<String, Object>> foundItems) {
    Map<String, Object> itemMDMDCPropertiesDetails =
        (Map<String, Object>) foundItems.get(0).get(FixtureConstants.ITEM_MDM_DC_PROPERTIES);
    Map<String, Object> itemMDMDCPropertiesSupplyItemDetails =
        (Map<String, Object>) itemMDMDCPropertiesDetails.get(FixtureConstants.ITEM_MDM_SUPPLY_ITEM);
    return itemMDMDCPropertiesSupplyItemDetails;
  }

  public static List<Map<String, Object>> getItemMDMTradeDetails(
      Map<String, Object> itemMDMDCPropertiesSupplyItemDetails) {
    List<Map<String, Object>> itemMDMDCPropertiesSupplyItemDimesnionDetails =
        (List<Map<String, Object>>)
            itemMDMDCPropertiesSupplyItemDetails.get(FixtureConstants.ITEM_TRADE_DETAILS);
    return itemMDMDCPropertiesSupplyItemDimesnionDetails;
  }

  // Retrieving the Item Dimension such as Length, Widhth ,Height from MDM
  public static Map<String, Object> getItemMDMDDimensionDetails(
      Map<String, Object> itemMDMDCPropertiesSupplyItemDetails) {
    Map<String, Object> itemMDMDCPropertiesSupplyItemDimesnionDetails =
        (Map<String, Object>)
            itemMDMDCPropertiesSupplyItemDetails.get(FixtureConstants.ITEM_MDM_DIMENSION);
    return itemMDMDCPropertiesSupplyItemDimesnionDetails;
  }

  // Retrieving the pallet size from item mdm
  public static Map<String, Object> getItemMDMDPalletSize(
      Map<String, Object> itemMDMDCPropertiesSupplyItemDetails) {
    Map<String, Object> palletSize =
        (Map<String, Object>)
            itemMDMDCPropertiesSupplyItemDetails.get(FixtureConstants.PALLET_SIZE);
    return palletSize;
  }

  // Retrieving the Item Weight from MDM
  public static Map<String, Object> getItemMDMWeightDetails(
      Map<String, Object> itemMDMDCPropertiesSupplyItemDetails) {
    Map<String, Object> itemMDMWeightDetailsDetails =
        (Map<String, Object>)
            itemMDMDCPropertiesSupplyItemDetails.get(FixtureConstants.ITEM_MDM_WEIGHT);
    return itemMDMWeightDetailsDetails;
  }

  // Retrieving the Warehouse such as code,description from MDM
  public static Map<String, Object> getItemMDMDWareHouseDetails(
      Map<String, Object> itemMDMDCPropertiesSupplyItemDetails) {
    Map<String, Object> itemMDMDCPropertiesSupplyItemWareHouseDetails =
        (Map<String, Object>) itemMDMDCPropertiesSupplyItemDetails.get(FixtureConstants.WAREHOUSE);
    return itemMDMDCPropertiesSupplyItemWareHouseDetails;
  }

  // Retrieving the warehouseArea such as code,description from MDM
  public static Map<String, Object> getItemMDMDWareHouseAreaDetails(
      Map<String, Object> itemMDMDCPropertiesSupplyItemWareHouseDetails) {
    Map<String, Object> itemMDMDCPropertiesSupplyItemWareHouseAreaDetails =
        (Map<String, Object>)
            itemMDMDCPropertiesSupplyItemWareHouseDetails.get(FixtureConstants.WAREHOUSEAREA);
    return itemMDMDCPropertiesSupplyItemWareHouseAreaDetails;
  }

  public static String getUpc(Map<String, Object> itemDetails) {
    String upc = null;
    List<Map<String, Object>> itemMDMTradeDetails = getItemMDMTradeDetails(itemDetails);
    if (!CollectionUtils.isEmpty(itemMDMTradeDetails)) {
      Optional<Map<String, Object>> tradeDetails = itemMDMTradeDetails.stream().findFirst();
      if (tradeDetails.isPresent()) {
        upc = (String) (tradeDetails.get().get(FixtureConstants.GTIN));
      }
    }
    return upc;
  }
}
