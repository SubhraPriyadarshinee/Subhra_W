package com.walmart.move.nim.receiving.acc.model.acl.notification;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ACLNotificationSearchResponse {
  private Integer pageNumber;
  private Integer pageSize;
  private List<ACLNotificationSummary> aclLogs;
}
