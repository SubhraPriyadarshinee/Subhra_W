package com.walmart.move.nim.receiving.data;

public class MockRcManagedConfig {
  public static final String DESTINATION_PARENT_CONTAINER_TYPE =
      "{\n"
          + "    \"9074\":{\n"
          + "        \"RTV\":\"Cart\",\n"
          + "        \"RESTOCK\":\"Cart\",\n"
          + "        \"POTENTIAL_FRAUD\":\"Cart\",\n"
          + "        \"DISPOSE\":\"Cart\"\n"
          + "    }\n"
          + "}";
  public static final String DESTINATION_PARENT_CONTAINER_TYPE_AS_NULL = null;
  public static final String DESTINATION_PARENT_CONTAINER_TYPE_AS_EMPTY_OBJECT = "{}";
  public static final String
      DESTINATION_PARENT_CONTAINER_TYPE_HAVING_DIFFERENT_FACILITY_CONFIGURATION =
          "{\n"
              + "    \"1234\":{\n"
              + "        \"RTV\":\"Cart\",\n"
              + "        \"RESTOCK\":\"Cart\",\n"
              + "        \"DISPOSE\":\"Cart\"\n"
              + "    }\n"
              + "}";
  public static final String
      DESTINATION_PARENT_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_AS_EMPTY_OBJECT =
          "{\n" + "    \"9074\":{}\n" + "}";
  public static final String
      DESTINATION_PARENT_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_WITH_MISSING_DISPOSITION_TYPE =
          "{\n"
              + "    \"9074\":{\n"
              + "        \"RESTOCK\":\"Cart\",\n"
              + "        \"DISPOSE\":\"Cart\"\n"
              + "    }\n"
              + "}";
  public static final String DESTINATION_CONTAINER_TYPE =
      "{\n"
          + "    \"9074\":{\n"
          + "        \"RTV\":\"Tote\",\n"
          + "        \"RESTOCK\":\"Tote\"\n"
          + "    }\n"
          + "}";
  public static final String DESTINATION_CONTAINER_TYPE_AS_NULL = null;
  public static final String DESTINATION_CONTAINER_TYPE_AS_EMPTY_OBJECT = "{}";
  public static final String DESTINATION_CONTAINER_TYPE_HAVING_DIFFERENT_FACILITY_CONFIGURATION =
      "{\n"
          + "    \"1234\":{\n"
          + "        \"RTV\":\"Tote\",\n"
          + "        \"RESTOCK\":\"Tote\"\n"
          + "    }\n"
          + "}";
  public static final String
      DESTINATION_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_AS_EMPTY_OBJECT =
          "{\n" + "    \"9074\":{}\n" + "}";
  public static final String
      DESTINATION_CONTAINER_TYPE_HAVING_FACILITY_CONFIGURATION_WITH_MISSING_DISPOSITION_TYPE_RTV =
          "{\n" + "    \"9074\":{\n" + "        \"RESTOCK\":\"Tote\"\n" + "    }\n" + "}";
}
