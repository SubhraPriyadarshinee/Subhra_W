package com.walmart.move.nim.receiving.core.client.inventory.model;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 *
 *
 * <pre>
 * [
 * {
 * "containerId": "C0001",
 * "financialReportingGroup": "us",
 * "baseDivisionCode": "wm",
 * "holdReasons": [71],
 * "holdDirectedBy": "HO",
 * "holdInitiatedTime":"2023-08-19T00:00:00.000Z"
 * }
 * ]
 * </pre>
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryOnHoldRequestV1 {
  private String containerId;
  private String financialReportingGroup;
  private String baseDivisionCode;
  private List<Integer> holdReasons;
  private String holdDirectedBy;
  private Date holdInitiatedTime;
}
