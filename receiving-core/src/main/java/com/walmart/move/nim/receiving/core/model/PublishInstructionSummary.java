package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PublishInstructionSummary extends MessageData {

  @Expose private String messageId;
  @Expose private String instructionCode;
  @Expose private String instructionMsg;
  @Expose private String activityName;
  @Expose private String instructionStatus;
  @Expose private Date instructionExecutionTS;
  @Expose private Boolean printChildContainerLabels;
  @Expose private UserInfo userInfo;
  @Expose private Container container;
  @Expose private Integer updatedQty;
  @Expose private String updatedQtyUOM;
  @Expose private Integer vnpkQty;
  @Expose private Integer whpkQty;
  @Expose private Location location;
  @Expose private String warehouseAreaCode;
  /** WFT to use this user Role for Performance for GDC */
  @Expose private String userRole;
  /** RCV to send createUser to WFT in case of VTR for GDC */
  @Expose private String createUser;
  /** RCV to send eventType as vtr to WFT in case of VTR for GDC */
  @Expose private String eventType;

  @Getter
  public static class UserInfo {
    @Expose private String userId;
    @Expose private String securityId;

    public UserInfo(String userId, String securityId) {
      this.userId = userId;
      this.securityId = securityId;
    }
  }

  public static class Location {
    @Expose private String locationId;
    @Expose private String locationType;

    @JsonProperty("scc_code")
    @Expose
    private String sccCode;

    public Location(String locationId, String locationType, String sccCode) {
      this.locationId = locationId;
      this.locationType = locationType;
      this.sccCode = sccCode;
    }
  }
}
