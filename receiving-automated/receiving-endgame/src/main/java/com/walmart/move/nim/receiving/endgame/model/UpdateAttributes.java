package com.walmart.move.nim.receiving.endgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UpdateAttributes {
  private String rotateDate;
  private boolean isExpired;
  private Boolean isAuditEnabled;

  public String getRotateDate() {
    return rotateDate;
  }

  public void setRotateDate(String rotateDate) {
    this.rotateDate = rotateDate;
  }

  public boolean isExpired() {
    return isExpired;
  }

  public void setIsExpired(boolean expired) {
    isExpired = expired;
  }

  public void setAuditEnabled(Boolean auditEnabled) {
    isAuditEnabled = auditEnabled;
  }

  public Boolean getAuditEnabled() {
    return isAuditEnabled;
  }
}
