package com.walmart.move.nim.receiving.core.client.epcis;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class EpcisRequest {

  private String eventTime;
  private String eventTimeZoneOffset;
  private String recordTime;
  private List<String> epcList;
  private String action;
  private String bizStep;
  private String disposition;
  private String readPoint;
  private String bizLocation;
  private List<EpcisReceiveIdentity> bizTransactionList;
  private List<EpcisReceiveIdentity> sourceList;
  private List<EpcisReceiveIdentity> destinationList;
  private List<EpcisReceiveIdentity> additionalAttributes;
  private EpcisKeyValue batchNumber;
  private EpcisKeyValue expiryDate;
  private EpcisKeyValue qty;
  private boolean ich;
  private boolean isValidationPerformed;
  private ChildEPCs childEPCs;
  private String parentID;
  private String reasonCode;
  private String returnType;

  @AllArgsConstructor
  public static class ChildEPCs {
    private List<String> epcList;
  }
}
