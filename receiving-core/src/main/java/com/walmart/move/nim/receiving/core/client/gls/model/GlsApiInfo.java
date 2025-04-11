package com.walmart.move.nim.receiving.core.client.gls.model;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
public class GlsApiInfo {
  public String status;
  public ArrayList<Error> errors;
  public JsonElement payload;
}
