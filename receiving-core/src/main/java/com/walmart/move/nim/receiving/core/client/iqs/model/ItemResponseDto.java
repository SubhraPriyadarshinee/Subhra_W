package com.walmart.move.nim.receiving.core.client.iqs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.*;

@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ItemResponseDto {

  private String deptNbr;
  private String itemNbr;
  private String consumableGtin;
  private String orderablePackGtin;
  private String warehousePackGtin;
  private String supplyItemPrimaryDescription;
  private String supplyItemSecondaryDescription;
  private String supplierStockId;
  private String supplierDeptNbr;
  private String warehousePackQty;
  private String orderablePackQty;
  private String deptCategoryNbr;
  private String deptSubcatgNbr;
  private String shelfLabel4Description;
  private String shelfLabel3Description;
  private ItemNode itemNode;
  private List<Gtins> gtins;
  private Integer merchandiseSubcategoryNbr;
  private Integer merchandiseCategoryNbr;
  private Double itemSize;
  private String itemSizeUOM;
  private Integer warehouseAreaCode;

  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ItemNode {
    Integer palletTinode;
    Integer palletHinode;
    Integer facilityNum;
    Integer dcNodeWarehouseAreaOverride;
    List<GtinAttributes> gtinAttributes;
  }

  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GtinAttributes {
    String gtin;
    Double tradeItemCubeQty;
    String tradeItemCubeUomCode;
    Double tradeItemWeightQty;
    String tradeItemWeightUomCode;
  }

  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Gtins {
    String tradeItemGtin;
    Integer palletTiQty;
    Integer palletHiQty;
    List<TradeItemCube> tradeItemCube;
    List<TradeItemWeight> tradeItemWeight;
  }

  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TradeItemCube {
    Double tradeItemCubeQty;
    String tradeItemCubeUomCode;
  }

  @Data
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TradeItemWeight {
    Double tradeItemWeightQty;
    String tradeItemWeightUomCode;
  }
}
