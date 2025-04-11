package com.walmart.move.nim.receiving.acc.model.acl.notification;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.constants.ACLError;
import com.walmart.move.nim.receiving.acc.constants.ACLErrorCode;
import com.walmart.move.nim.receiving.core.model.sumo.SumoContent;
import com.walmart.move.nim.receiving.core.model.sumo.SumoCustomData;
import com.walmart.move.nim.receiving.core.model.sumo.SumoNotification;
import java.util.List;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class ACLSumoNotification extends SumoNotification {

  private static final Gson gson = new Gson();
  /**
   * the custom_payload object must only contain key/value pairs. The "value" must be a string or an
   * error will be returned. If you need to send a JSON object as the value, just "stringify" it
   * first.
   */
  private ACLCustomPayload custom_payload;

  public ACLSumoNotification(String title, String alert, ACLNotification notification) {
    super(title, alert);
    this.custom_payload = new ACLCustomPayload(notification);
  }

  public ACLSumoNotification(
      String title, String alert, ACLNotification notification, SumoContent android) {
    super(title, alert, new SumoCustomData(gson.toJson(notification)), android);
  }

  /**
   * Squish and stringify an {@link ACLNotification} so that it conforms to sumo's requirements for
   * a {@code custom_payload} object -- a JSON object with only string property values See {@link
   * http://amp.docs.walmart.com/sumo/api-references.html}
   */
  @ToString
  private static class ACLCustomPayload {
    private String equipmentName;
    private String equipmentType;
    private String locationId;
    private String equipmentStatus;
    private String updatedTs;

    public ACLCustomPayload(ACLNotification notification) {
      List<EquipmentStatus> equipmentStatusList = notification.getEquipmentStatus();
      equipmentStatusList.forEach(
          equipmentStatus -> {
            ACLError errorValue = ACLErrorCode.getErrorValue(equipmentStatus.getCode());
            if (!Objects.isNull(errorValue)) {
              equipmentStatus.setMessage(errorValue.getMessage());
              equipmentStatus.setZone(errorValue.getZone());
              equipmentStatus.setComponentId(
                  Objects.nonNull(equipmentStatus.getComponentId())
                      ? equipmentStatus.getComponentId()
                      : errorValue.getZone().toString());
            }
          });
      this.equipmentName = notification.getEquipmentName();
      this.equipmentType = notification.getEquipmentType();
      this.locationId = notification.getLocationId();
      this.equipmentStatus =
          notification.getEquipmentStatus() == null
              ? null
              : gson.toJson(notification.getEquipmentStatus());
      this.updatedTs = notification.getUpdatedTs();
    }
  }
}
