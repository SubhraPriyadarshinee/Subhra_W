package com.walmart.move.nim.receiving.core.model.delivery.meta;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * DeliveryMetaJson holds json object
 *
 * @author vn50o7n
 */
@Getter
@Setter
@ToString
public class PoLineDetails {
  List<DocumentMeta> documents;
}
