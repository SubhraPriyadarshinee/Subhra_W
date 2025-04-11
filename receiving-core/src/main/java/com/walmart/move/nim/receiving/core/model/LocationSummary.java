package com.walmart.move.nim.receiving.core.model;

import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LocationSummary extends MessageData {

  private String source;

  @SerializedName("user_id")
  private String userId;

  private Date receivingTS;
  private Location location;

  public static class Location {
    private Integer locationId;
    private String locationType;

    @SerializedName("scc_code")
    private String sccCode;

    public Location(Integer locationId, String locationType, String sccCode) {
      this.locationId = locationId;
      this.locationType = locationType;
      this.sccCode = sccCode;
    }
  }
}
