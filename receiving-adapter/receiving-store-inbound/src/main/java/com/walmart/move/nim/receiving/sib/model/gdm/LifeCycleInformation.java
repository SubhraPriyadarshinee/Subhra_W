package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class LifeCycleInformation {

  private String type;
  private String userId;
  private Date time;
}
