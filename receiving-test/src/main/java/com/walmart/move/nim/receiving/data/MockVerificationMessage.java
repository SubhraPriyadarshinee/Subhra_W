package com.walmart.move.nim.receiving.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MockVerificationMessage {
  public static final String MESSAGE =
      "  \"deliveryNumber\": \"124356\",\n"
          + "  \"locationId\": \"DOOR_31\",\n"
          + "  \"lpn\": \"E06938000020267148\",\n"
          + "  \"eventTs\": \"2023-07-14T10:00:12.141\",\n"
          + "  \"inboundTagId\": \"a069380000000000000010822\",\n";

  public static final String PALLET_RECEIVED_STATUS_FALSE = "  \"palletReceivedStatus\": false\n";
  public static final String PALLET_RECEIVED_STATUS_TRUE = "  \"palletReceivedStatus\": true\n";
  public static final String MESSAGE_TYPE_NORMAL = "  \"messageType\": \"NORMAL\",\n";
  public static final String MESSAGE_TYPE_BYPASS = "  \"messageType\": \"BYPASS\",\n";

  public static final String VALID_SYM_VERIFICATION_MESSAGE =
      "{\n" + MESSAGE + MESSAGE_TYPE_NORMAL + PALLET_RECEIVED_STATUS_FALSE + "}";

  public static final String INVALID_SYM_VERIFICATION_MESSAGE_TYPE =
      "{\n" + MESSAGE + MESSAGE_TYPE_BYPASS + PALLET_RECEIVED_STATUS_FALSE + "}";

  public static final String SYM_VERIFICATION_MESSAGE_RECEIVED =
      "{\n" + MESSAGE + MESSAGE_TYPE_NORMAL + PALLET_RECEIVED_STATUS_TRUE + "}";

  public static final String INVALID_SYM_VERIFICATION_MESSAGE =
      "{\n" + MESSAGE + PALLET_RECEIVED_STATUS_FALSE + "}";
}
