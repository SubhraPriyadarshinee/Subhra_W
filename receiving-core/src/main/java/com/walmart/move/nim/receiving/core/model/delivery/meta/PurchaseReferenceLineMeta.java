package com.walmart.move.nim.receiving.core.model.delivery.meta;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Contains Purchase Reference Line Number and line level attributes
 *
 * @author vn50o7n
 */
@Getter
@Setter
@ToString
public class PurchaseReferenceLineMeta {
  Integer purchaseReferenceLineNumber;
  String ignoreExpiry;
  String ignoreExpiryBy;
  String ignoreOverage;
  String ignoreOverageBy;
  String approvedHaccp;
  String approvedHaccpBy;
}
