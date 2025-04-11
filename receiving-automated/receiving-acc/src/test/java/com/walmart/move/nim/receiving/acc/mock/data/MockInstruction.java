package com.walmart.move.nim.receiving.acc.mock.data;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.springframework.http.HttpHeaders;

public class MockInstruction {

  public static Instruction getDockTagInstruction() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", "EFLCP08");
    moveTreeMap.put("correlationID", "1a2bc3d4");
    moveTreeMap.put("containerTag", "c32987000000000000000001");
    moveTreeMap.put("lastChangedOn", new Date());
    moveTreeMap.put("lastChangedBy", "sysadmin");

    Instruction instruction = new Instruction();
    instruction.setId(329870001L);
    instruction.setInstructionCode(ReceivingConstants.DOCK_TAG);
    instruction.setInstructionMsg("Create dock tag container instruction");
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(getDockTagContainerDetails());
    instruction.setMessageId("0eb0a8b6-36e1-4792-b4a1-9da242d8199e");
    instruction.setDockTagId("c32987000000000000000001");
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(261220189L);
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(0);
    instruction.setCreateUserId("sysadmin");
    instruction.setMove(moveTreeMap);
    return instruction;
  }

  public static ContainerDetails getDockTagContainerDetails() {
    ContainerDetails containerDetails;
    Map<String, Object> labelData = new HashMap<>();
    labelData.put("key", "DOOR");
    labelData.put("value", "5555");
    labelData.put("key", "DATE");
    labelData.put("value", new Date());
    labelData.put("key", "LPN");
    labelData.put("value", "c32987000000000000000001");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "sysadmin");
    labelData.put("key", "DELIVERYNBR");
    labelData.put("value", 261220189L);

    List<Map<String, Object>> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "c32987000000000000000001");
    printRequest.put("formatName", "dock_tag_atlas");
    printRequest.put("ttlInHours", 72);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", ReceivingConstants.RECEIVING_PROVIDER_ID);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.WMT_PRODUCT_NAME, ReceivingConstants.APP_NAME_VALUE);
    containerLabel.put("headers", httpHeaders);
    containerLabel.put("printRequests", printRequestList);

    containerDetails = new ContainerDetails();
    containerDetails.setTrackingId("c32987000000000000000001");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("WORK_IN_PROGRESS");
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrLabel(containerLabel);
    return containerDetails;
  }

  public static PrintJob getPrintJob() {
    PrintJob printJob = new PrintJob();

    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add("a328990000000000000106509");

    printJob.setDeliveryNumber(21119003L);
    printJob.setCreateUserId("sysadmin");
    printJob.setInstructionId(1L);
    printJob.setLabelIdentifier(printJobLpnSet);

    return printJob;
  }

  public static Container getDockTagContainer() {
    Container container = new Container();
    container.setCreateUser("sysadmin");
    container.setLastChangedUser("sysadmin");
    container.setMessageId("0eb0a8b6-36e1-4792-b4a1-9da242d8199e");
    container.setInventoryStatus("WORK_IN_PROGRESS");
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrShippable(Boolean.FALSE);
    container.setTrackingId("c32987000000000000000001");
    container.setInstructionId(329870001L);
    container.setLocation("5555");
    container.setDeliveryNumber(261220189L);
    container.setContainerType("PALLET");
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setOnConveyor(Boolean.FALSE);

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");

    container.setFacility(facility);
    return container;
  }

  public static Instruction getCompletedDockTagInstruction() {
    Instruction instruction = getDockTagInstruction();
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysadmin");
    return instruction;
  }
}
