package com.walmart.move.nim.receiving.core.model.delivery.meta;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * DocumentMeta pojo represents top level json object
 *
 * @author vn50o7n
 */
@Getter
@Setter
@ToString
public class DocumentMeta {
  String purchaseReferenceNumber;
  String poType;
  List<PurchaseReferenceLineMeta> lines;
}
