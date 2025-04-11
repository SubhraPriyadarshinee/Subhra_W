package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;

/**
 * Model for error response from GDM
 *
 * @author g0k0072
 */
@Getter
public class ExternalErrorResponse {

  private List<Message> messages;

  public class Message {
    @Getter private String code;
    @Getter private String desc;
    @Getter private String type;
  }
}
