package com.walmart.move.nim.receiving.data;

public class MockSymPutawayConfirmationMessage {

  public static final String VALID_PUTAWAY_CONFIRMATION_MESSAGE =
      "{\n"
          + "  \"trackingId\": \"c060200000200000003510353\",\n"
          + "  \"status\": \"COMPLETED\",\n"
          + "  \"quantityUOM\": \"ZA\",\n"
          + "  \"quantity\": 10,\n"
          + "  \"errorDetails\": [\n"
          + "    {\n"
          + "      \"code\": \"NOT_SYM_ELIGIBLE\",\n"
          + "      \"quantity\": 10\n"
          + "    }\n"
          + "  ]\n"
          + "}";

  public static final String INVALID_PUTAWAY_CONFIRMATION_MESSAGE_EMPTY_TRACKING_ID =
      "{\n"
          + "  \"trackingId\": \"\",\n"
          + "  \"status\": \"COMPLETED\",\n"
          + "  \"quantityUOM\": \"ZA\",\n"
          + "  \"quantity\": 10,\n"
          + "  \"errorDetails\": [\n"
          + "    {\n"
          + "      \"code\": \"NOT_SYM_ELIGIBLE\",\n"
          + "      \"quantity\": 10\n"
          + "    }\n"
          + "  ]\n"
          + "}";
  public static final String PUTAWAY_CONFIRMATION_MESSAGE_WITH_ERROR_DETAILS =
      "{\n"
          + "  \"trackingId\": \"c060200000200000003510353\",\n"
          + "  \"status\": \"COMPLETED\",\n"
          + "  \"quantityUOM\": \"ZA\",\n"
          + "  \"quantity\": 10,\n"
          + "  \"errorDetails\": [\n"
          + "    {\n"
          + "      \"code\": \"NOT_SYM_ELIGIBLE\",\n"
          + "      \"quantity\": 10\n"
          + "    }\n"
          + "  ]\n"
          + "}";

  public static final String VALID_SYM_NACK_MESSAGE =
      "{\n" + "  \"reason\": \"wrong format \",\n" + "  \"status\": \"FORMAT_ERROR\"\n" + "}";

  public static final String VALID_PUTAWAY_CONFIRMATION_MESSAGE_INVALID_LPN =
      "{\n"
          + "  \"trackingId\": \"099970200027724762\",\n"
          + "  \"status\": \"COMPLETED\",\n"
          + "  \"quantityUOM\": \"ZA\",\n"
          + "  \"quantity\": 10,\n"
          + "  \"errorDetails\": [\n"
          + "    {\n"
          + "      \"code\": \"NOT_SYM_ELIGIBLE\",\n"
          + "      \"quantity\": 10\n"
          + "    }\n"
          + "  ]\n"
          + "}";
}
