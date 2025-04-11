package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Contact {
  private Reason reason;
  private String language;
  private ChannelAttributes channelAttributes;
}
