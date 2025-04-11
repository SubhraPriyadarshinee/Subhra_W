package com.walmart.move.nim.receiving.acc.model.acl.verification;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class ACLVerificationEventMessage extends MessageData {
  private String locationId;
  private String groupNbr;
  private String lpn;
  private String eventTs;

  /** Added printerId for Integrate with Hawkeye */
  private String printerId;
}
