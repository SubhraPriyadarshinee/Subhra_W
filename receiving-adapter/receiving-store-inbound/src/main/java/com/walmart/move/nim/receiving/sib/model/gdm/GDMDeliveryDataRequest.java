package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GDMDeliveryDataRequest {

  private Criteria criteria;
  private Page page;
  private Sort sort;
}
