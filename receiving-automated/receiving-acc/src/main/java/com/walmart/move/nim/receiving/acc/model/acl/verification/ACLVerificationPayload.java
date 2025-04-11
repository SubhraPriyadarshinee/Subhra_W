package com.walmart.move.nim.receiving.acc.model.acl.verification;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ACLVerificationPayload {
  List<ACLVerificationEventMessage> labelVerificationAck;
}
