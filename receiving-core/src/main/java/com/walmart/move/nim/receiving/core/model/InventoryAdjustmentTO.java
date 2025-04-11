package com.walmart.move.nim.receiving.core.model;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;

@Getter
@Setter
public class InventoryAdjustmentTO extends MessageData {

  private JsonObject jsonObject;

  private HttpHeaders httpHeaders;
}
