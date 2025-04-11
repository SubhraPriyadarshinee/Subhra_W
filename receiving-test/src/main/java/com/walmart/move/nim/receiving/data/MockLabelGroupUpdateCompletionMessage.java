package com.walmart.move.nim.receiving.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MockLabelGroupUpdateCompletionMessage {
  public static final String GROUP_TYPE = "  \"groupType\": \"RCV_DA\",\n";
  public static final String VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG =
      "{\n"
          + "  \"deliveryNumber\": \"124356\",\n"
          + GROUP_TYPE
          + "  \"locationId\": \"DOOR_31\",\n"
          + "  \"status\": COMPLETED,\n"
          + "  \"inboundTagId\": \"a069380000000000000010822\",\n"
          + "  \"tagType\": DOCK_TAG\n"
          + "}";

  public static final String VALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE =
      "{\n"
          + "  \"deliveryNumber\": \"143456\",\n"
          + GROUP_TYPE
          + "  \"locationId\": \"DOOR_33\",\n"
          + "  \"status\": COMPLETED\n"
          + "}";

  public static final String VALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE_PALLET =
      "{\n"
          + "  \"deliveryNumber\": \"153456\",\n"
          + GROUP_TYPE
          + "  \"locationId\": \"DOOR_34\",\n"
          + "  \"status\": COMPLETED,\n"
          + "  \"inboundTagId\": \"009778092061\",\n"
          + "  \"tagType\": PALLET_TAG\n"
          + "}";

  public static final String INVALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE =
      "{\n"
          + "  \"deliveryNumber\": \"163456\",\n"
          + GROUP_TYPE
          + "  \"locationId\": \"DOOR_35\",\n"
          + "  \"inboundTagId\": \"009778092061\",\n"
          + "  \"tagType\": PALLET_TAG\n"
          + "}";
}
