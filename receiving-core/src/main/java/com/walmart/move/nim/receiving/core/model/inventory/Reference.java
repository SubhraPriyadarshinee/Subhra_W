package com.walmart.move.nim.receiving.core.model.inventory;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT,
    property = "type")
@JsonSubTypes({
  @Type(value = PurchaseOrder.class, name = "PurchaseOrder"),
  @Type(value = Receipt.class, name = "Receipt")
})
public interface Reference {}
