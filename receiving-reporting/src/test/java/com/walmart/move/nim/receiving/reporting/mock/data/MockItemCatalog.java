package com.walmart.move.nim.receiving.reporting.mock.data;

import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MockItemCatalog {
  private static List<ItemCatalogUpdateLog> itemCatalogUpdateLogs = new ArrayList<>();
  private static ItemCatalogUpdateLog itemCatalogUpdateLog1 =
      ItemCatalogUpdateLog.builder()
          .id(0L)
          .itemNumber(567898765L)
          .deliveryNumber(87654321L)
          .newItemUPC("20000943037194")
          .oldItemUPC("00000943037194")
          .build();

  private static ItemCatalogUpdateLog getItemCatalogUpdateLog1() {
    itemCatalogUpdateLog1.setFacilityNum(32818);
    return itemCatalogUpdateLog1;
  }

  private static ItemCatalogUpdateLog itemCatalogUpdateLog2 =
      ItemCatalogUpdateLog.builder()
          .id(1L)
          .itemNumber(567898765L)
          .deliveryNumber(87654321L)
          .newItemUPC("20000943037194")
          .oldItemUPC("00000943037194")
          .build();

  private static ItemCatalogUpdateLog itemCatalogUpdateLog3 =
      ItemCatalogUpdateLog.builder()
          .id(2L)
          .itemNumber(567898765L)
          .deliveryNumber(87654321L)
          .newItemUPC("20000943037194")
          .oldItemUPC("00000943037194")
          .createUserId("sysadmin")
          .createTs(new Date())
          .vendorNumber("MOCK_VENDOR_NUMBER")
          .vendorStockNumber("MOCK_VENDOR_STOCK_NUMBER")
          .build();

  private static ItemCatalogUpdateLog getItemCatalogUpdateLog2() {
    itemCatalogUpdateLog2.setFacilityNum(6561);
    return itemCatalogUpdateLog2;
  }

  private static ItemCatalogUpdateLog getItemCatalogUpdateLog3() {
    itemCatalogUpdateLog3.setFacilityNum(6001);
    return itemCatalogUpdateLog3;
  }

  public static List<ItemCatalogUpdateLog> getItemCatalogUpdateLogs() {
    itemCatalogUpdateLogs.add(getItemCatalogUpdateLog1());
    itemCatalogUpdateLogs.add(getItemCatalogUpdateLog2());
    itemCatalogUpdateLogs.add(getItemCatalogUpdateLog3());

    return itemCatalogUpdateLogs;
  }

  public static String expectedMailTemplateForItemCatalog =
      "<html><body><p style='text-align:left;font-size:14'>Hi,<br> Please find the item catalog report of each of the following DCs: 32818, 6561 in the attachment.<p style='text-align:left;font-size:14'> There were no item catalog recorded for each of the following DCs: 32987<h4 style='text-decoration: underline;'> Note: </h4><p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.<p>Thanks,<br>Atlas-receiving team</p></html>";
}
