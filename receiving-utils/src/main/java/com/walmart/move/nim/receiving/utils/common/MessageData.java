package com.walmart.move.nim.receiving.utils.common;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * This is the super class of all the message data in any message listener.
 *
 * @author a0b02ft
 */
@Getter
@Setter
@ToString
public class MessageData {

  private Integer facilityNum;
  private String facilityCountryCode;
}
