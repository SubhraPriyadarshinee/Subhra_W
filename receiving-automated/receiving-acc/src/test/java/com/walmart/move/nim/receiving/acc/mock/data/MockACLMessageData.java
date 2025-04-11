package com.walmart.move.nim.receiving.acc.mock.data;

import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MockACLMessageData {

  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getNotificationEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACLNotificationEvent.json");
  }

  public static String getNotificationEventWithCodeOnly() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACLNotificationEventCodeOnly.json");
  }

  public static String getNotificationEventWithMultipleEquipment() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACLNotificationMultipleEquipment.json");
  }

  public static String getUnknownNotificationEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACLNotificationUnknownEvent.json");
  }

  public static String getHawkeyeNotificationEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/json/hawkeyeACLNotification.json");
  }

  public static String getHawkeyeDeliveryLinkEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/json/hawkEyeDeliveryLinkResponse.json");
  }

  public static String getMultipleHawkeyeNotificationEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/json/hawkeyeACLNotificationMultiple.json");
  }

  public static String getMultipleHawkeyeNotificationEvent2() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/json/hawkeyeACLNotificationMultiple2.json");
  }

  public static String getVerificationEvent() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACLVerificationEvent.json");
  }

  public static String getBuildContainerRequestForACLReceiving() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/FDERequestForACLReceiving.json");
  }

  public static String getBuildContainerRequestForACLReceivingAgainstAllowedOvg() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/"
            + "json/FDERequestForACLReceivingAgainstAllowedOvg.json");
  }

  public static String getBuildContainerResponseForACLReceiving() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/FDEResponseForACLReceiving.json");
  }

  public static String getMockInstructionRequest() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/ACCInstructionRequest.json");
  }

  public static String getDeliveryDocuments() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/DeliveryDocuments.json");
  }

  public static String getMockInstruction() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/MockInstruction.json");
  }

  public static String possibleUPCFirstItem() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/PossibleUPC_551705258.json");
  }

  public static String possibleUPCSecondItem() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/PossibleUPC_553072805.json");
  }

  public static String possibleUPCThirdItem() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/PossibleUPC_551705259.json");
  }
}
