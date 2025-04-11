package com.walmart.move.nim.receiving.reporting.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class UserCaseChannelTypeResponse {

  private String user;
  private long casesCount;
  private String channelType;

  public UserCaseChannelTypeResponse() {}

  public UserCaseChannelTypeResponse(String user, long casesCount, String channelType) {
    super();
    this.user = user;
    this.casesCount = casesCount;
    this.channelType = channelType;
  }
}
