package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InstructionRepositoryTest extends ReceivingTestBase {

  private static final int HOURS = 24;

  @Autowired private InstructionRepository instructionRepository;

  private List<Instruction> instructionList;

  /** Insert data */
  @BeforeClass
  public void setUp() {
    instructionList = new ArrayList<>();
    Instruction completedIntrsuction = new Instruction();
    Instruction pendingInstruction = new Instruction();
    Instruction reportInstruction = new Instruction();

    // Move data
    LinkedTreeMap move = new LinkedTreeMap();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");

    // Completed Instruction
    ContainerDetails completedInstructionContainer = new ContainerDetails();
    completedIntrsuction.setContainer(completedInstructionContainer);
    completedIntrsuction.setChildContainers(null);
    completedIntrsuction.setCreateTs(new Date());
    completedIntrsuction.setCreateUserId("sysadmin");
    completedIntrsuction.setLastChangeTs(new Date());
    completedIntrsuction.setLastChangeUserId("sysadmin");
    completedIntrsuction.setCompleteTs(new Date());
    completedIntrsuction.setCompleteUserId("sysadmin");
    completedIntrsuction.setDeliveryNumber(Long.valueOf("21119003"));
    completedIntrsuction.setGtin("00000943037194");
    // Commented below line to check if partial instructions get discarded in
    // FindByDeliveryNumberAndInstructionCodeIsNotNull call
    // completedIntrsuction.setInstructionCode("Build Container");
    completedIntrsuction.setInstructionMsg("Build the Container");
    completedIntrsuction.setItemDescription("HEM VALUE PACK (4)");
    completedIntrsuction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    completedIntrsuction.setMove(move);
    completedIntrsuction.setPoDcNumber("32899");
    completedIntrsuction.setPrintChildContainerLabels(true);
    completedIntrsuction.setPurchaseReferenceNumber("9763140004");
    completedIntrsuction.setPurchaseReferenceLineNumber(1);
    completedIntrsuction.setProjectedReceiveQty(2);
    completedIntrsuction.setProviderId("DA");
    completedIntrsuction.setActivityName(ReceivingConstants.DA_CON_ACTIVITY_NAME);
    completedIntrsuction.setFirstExpiryFirstOut(Boolean.FALSE);
    completedIntrsuction.setIsReceiveCorrection(Boolean.FALSE);
    completedIntrsuction.setProblemTagId("99906938999999");
    completedIntrsuction.setFacilityNum(32612);

    // Pending Instruction
    ContainerDetails pendingInstructionContainer = new ContainerDetails();
    pendingInstruction.setFacilityNum(32612);
    pendingInstruction.setContainer(pendingInstructionContainer);
    pendingInstruction.setChildContainers(null);
    pendingInstruction.setCreateTs(new Date());
    pendingInstruction.setCreateUserId("sysadmin");
    pendingInstruction.setLastChangeTs(new Date());
    pendingInstruction.setLastChangeUserId("sysadmin");
    pendingInstruction.setDeliveryNumber(Long.valueOf("21119003"));
    pendingInstruction.setGtin("00000943037204");
    pendingInstruction.setInstructionCode("Build Container");
    pendingInstruction.setInstructionMsg("Build the Container");
    pendingInstruction.setItemDescription("HEM VALUE P  ACK (5)");
    pendingInstruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    pendingInstruction.setMove(move);
    pendingInstruction.setPoDcNumber("32899");
    pendingInstruction.setPrintChildContainerLabels(true);
    pendingInstruction.setPurchaseReferenceNumber("9763140005");
    pendingInstruction.setPurchaseReferenceLineNumber(1);
    pendingInstruction.setProjectedReceiveQty(2);
    pendingInstruction.setProviderId("DA");
    pendingInstruction.setActivityName(ReceivingConstants.DA_NON_CON_ACTIVITY_NAME);
    pendingInstruction.setProblemTagId("123456789");
    pendingInstruction.setFirstExpiryFirstOut(Boolean.TRUE);
    pendingInstruction.setIsReceiveCorrection(Boolean.TRUE);

    // Completed Instruction

    reportInstruction.setContainer(completedInstructionContainer);
    reportInstruction.setChildContainers(null);
    reportInstruction.setCreateTs(new Date());
    reportInstruction.setCreateUserId("reportUser");
    reportInstruction.setLastChangeTs(new Date());
    reportInstruction.setLastChangeUserId("reportUser");
    reportInstruction.setCompleteTs(new Date());
    reportInstruction.setCompleteUserId("reportUser");
    reportInstruction.setDeliveryNumber(Long.valueOf("21119004"));
    reportInstruction.setGtin("00000943037195");
    reportInstruction.setInstructionMsg("Build the Container");
    reportInstruction.setItemDescription("HEM VALUE PACK (4)");
    reportInstruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    reportInstruction.setMove(move);
    reportInstruction.setPoDcNumber("32899");
    reportInstruction.setPrintChildContainerLabels(true);
    reportInstruction.setPurchaseReferenceNumber("9763140006");
    reportInstruction.setPurchaseReferenceLineNumber(1);
    reportInstruction.setProjectedReceiveQty(2);
    reportInstruction.setProviderId("SSTK");
    reportInstruction.setActivityName(ReceivingConstants.SSTK_ACTIVITY_NAME);
    reportInstruction.setFirstExpiryFirstOut(Boolean.FALSE);

    instructionList.add(completedIntrsuction);
    instructionList.add(pendingInstruction);
    instructionList.add(reportInstruction);

    // New test data for open instructions
    for (int i = 1; i < 5; i++) {
      Instruction instruction = new Instruction();
      instruction.setFacilityNum(32612);
      instruction.setCreateUserId("sysadmin");
      instruction.setInstructionCode("Build Container");
      instruction.setDeliveryNumber(Long.valueOf("26109445"));
      instruction.setPurchaseReferenceNumber("0559419741");
      instruction.setPurchaseReferenceLineNumber(i);
      instructionList.add(instruction);
    }

    instructionRepository.saveAll(instructionList);
    instructionRepository.saveAll(persistInstructionsWithInstructionSetId());
  }

  @Test
  public void testFindByDeliveryNumber() {
    List<Instruction> instructions =
        instructionRepository.findByDeliveryNumber(Long.valueOf("21119003"));
    assertTrue(instructions.size() == 2);
    assertTrue(
        instructionList.get(0).getActivityName().equals(ReceivingConstants.DA_CON_ACTIVITY_NAME));
    assertTrue(instructionList.get(0).getFirstExpiryFirstOut().equals(Boolean.FALSE));
    assertTrue(instructionList.get(1).getFirstExpiryFirstOut().equals(Boolean.TRUE));
    assertTrue(instructionList.get(0).getIsReceiveCorrection().equals(Boolean.FALSE));
    assertTrue(instructionList.get(1).getIsReceiveCorrection().equals(Boolean.TRUE));
  }

  @Test
  public void testFindByDeliveryNumberAndInstructionCodeIsNotNull() {
    List<Instruction> instructions =
        instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(
            Long.valueOf("21119003"));
    assertTrue(instructions.size() == 1);
  }

  @Test
  public void testPositiveCountByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull() {
    Long instructionCount =
        instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            Long.valueOf("21119003"));
    assertTrue(instructionCount == 1l);
  }

  @Test
  public void testNegativeCountByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull() {
    Long instructionCount =
        instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            Long.valueOf("0"));
    assertTrue(instructionCount == 0l);
  }

  @Test
  public void testPositiveFindByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull() {
    List<Instruction> instructionList =
        instructionRepository.findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
            Long.valueOf("21119003"), "123456789");
    assertTrue(instructionList.size() == 1);
    assertTrue(instructionList.get(0).getDeliveryNumber().equals(Long.valueOf("21119003")));
    assertTrue(instructionList.get(0).getProblemTagId().equals("123456789"));
  }

  @Test
  public void testNegativeFindByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull() {
    List<Instruction> instructionList =
        instructionRepository.findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
            Long.valueOf("21119004"), "123456789");
    assertTrue(instructionList.size() == 0);
  }

  @Test
  public void testPositiveFindByProblemTagIdAndInstructionCodeIsNotNull() {
    List<Instruction> instructionList =
        instructionRepository.findByProblemTagIdAndInstructionCodeIsNotNull("123456789");
    assertTrue(instructionList.size() == 1);
    assertTrue(instructionList.get(0).getProblemTagId().equals("123456789"));
  }

  @Test
  public void testNegativeFindByProblemTagIdAndInstructionCodeIsNotNull() {
    List<Instruction> instructionList =
        instructionRepository.findByProblemTagIdAndInstructionCodeIsNotNull("1234567890");
    assertTrue(instructionList.size() == 0);
  }

  @Test
  public void testPositiveFindByLastChangeTimestampBeforeAndFacilityNum() {
    Date presentDate = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(presentDate);
    cal.add(Calendar.DATE, 1);
    Date futureDate = cal.getTime();

    List<Instruction> instructionList =
        instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                futureDate, 32612);
    assertTrue(instructionList.size() > 0); // todo expected size to be positive?
  }

  @Test
  public void testNegativeFindByLastChangeTimestampBeforeAndFacilityNum() {
    Date presentDate = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(presentDate);
    cal.add(Calendar.DATE, -1);
    Date pastDate = cal.getTime();

    List<Instruction> instructionList =
        instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                pastDate, 32612);
    assertTrue(instructionList.size() == 0);
  }

  @Test
  public void
      testPositiveFindByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionCodeIsNotNull() {
    Long pendingInstructionCumulativeProjectedReceivedQuantity =
        instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            "9763140005", 1);
    assertTrue(pendingInstructionCumulativeProjectedReceivedQuantity == 2l);
  }

  @Test
  public void
      testNegativeFindByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionCodeIsNotNull() {
    Long pendingInstructionCumulativeProjectedReceivedQuantity =
        instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            "9763140004", 2);
    assertTrue(pendingInstructionCumulativeProjectedReceivedQuantity == null);
  }

  @Test
  public void testOpenInstructions() {
    List<Instruction> openInstructions =
        instructionRepository
            .findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                Long.valueOf("26109445"), "0559419741");
    assertTrue(openInstructions.size() == 4);
  }

  /** Test case for count of number of problem pallets received after a given timestamp */
  @Test
  public void testCountDistinctProblemPalletsByCreateTsAfter() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -HOURS);
    java.util.Date fromdate = cal.getTime();
    Integer numberOfItems =
        instructionRepository
            .countByProblemTagIdIsNotNullAndCreateTsAfterAndCreateTsBeforeAndCompleteTsNotNull(
                fromdate, Calendar.getInstance().getTime());

    assertEquals(numberOfItems.intValue(), 1);
  }

  /**
   * Test case for count of average number of pallets received per delivery after a given timestamp
   */
  @Test
  public void testFindNumberOfPalletsPerDeliveryByCreateTsAfter() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -HOURS);
    java.util.Date afterDate = cal.getTime();
    List<Long> listOfNumberOfPalletsPerDelivery =
        instructionRepository.findNumberOfPalletsPerDeliveryByCreateTsAfterAndCompleteTsNotNull(
            afterDate, Calendar.getInstance().getTime());

    assertEquals(listOfNumberOfPalletsPerDelivery.size(), 2);
  }

  @Test
  public void testSSTKInstruction_saveAndGet() throws JsonSyntaxException, IOException {
    String dataPathDeliveryDocumentSSTK =
        new File("../receiving-test/src/main/resources/json/DeliveryDocumentForItemScanSSTK.json")
            .getCanonicalPath();
    String dataPathOFResponseSSTK =
        new File("../receiving-test/src/main/resources/json/SSTKOFResponse.json")
            .getCanonicalPath();

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(
            new Gson()
                .fromJson(
                    new String(Files.readAllBytes(Paths.get(dataPathDeliveryDocumentSSTK))),
                    DeliveryDocument[].class));
    FdeCreateContainerResponse fdeCreateContainerResponse =
        new Gson()
            .fromJson(
                new String(Files.readAllBytes(Paths.get(dataPathOFResponseSSTK))),
                FdeCreateContainerResponse.class);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("7386C27C-106D-456E-A8F1-0C68CA8972B8");
    instructionRequest.setUpcNumber("10000000000002");
    instructionRequest.setDeliveryNumber("15112021");
    instructionRequest.setDoorNumber("22");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocuments.get(0),
            InstructionUtils.mapHttpHeaderToInstruction(
                MockHttpHeaders.getHeaders(),
                InstructionUtils.createInstruction(instructionRequest)));

    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);

    instructionRepository.save(instruction);
    Instruction savedInstruction =
        instructionRepository.findByMessageId("7386C27C-106D-456E-A8F1-0C68CA8972B8");

    assertTwoInstructionsAreEqual(savedInstruction, instruction);
  }

  @Test
  public void testDAInstruction_saveAndGet() throws JsonSyntaxException, IOException {

    String dataPathDeliveryDocumentDA =
        new File("../receiving-test/src/main/resources/json/DeliveryDocumentForItemScanDA.json")
            .getCanonicalPath();
    String dataPathOFResponseDA =
        new File("../receiving-test/src/main/resources/json/DAOFResponse.json").getCanonicalPath();

    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(
            new Gson()
                .fromJson(
                    new String(Files.readAllBytes(Paths.get(dataPathDeliveryDocumentDA))),
                    DeliveryDocument[].class));
    FdeCreateContainerResponse fdeCreateContainerResponse =
        new Gson()
            .fromJson(
                new String(Files.readAllBytes(Paths.get(dataPathOFResponseDA))),
                FdeCreateContainerResponse.class);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("7386C27C-106D-456E-A8F1-0C68CA8972B7");
    instructionRequest.setUpcNumber("10000000000001");
    instructionRequest.setDeliveryNumber("15112022");
    instructionRequest.setDoorNumber("21");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocuments.get(0),
            InstructionUtils.mapHttpHeaderToInstruction(
                MockHttpHeaders.getHeaders(),
                InstructionUtils.createInstruction(instructionRequest)));

    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);

    instructionRepository.save(instruction);
    Instruction savedInstruction =
        instructionRepository.findByMessageId("7386C27C-106D-456E-A8F1-0C68CA8972B7");
    assertTwoInstructionsAreEqual(savedInstruction, instruction);
  }

  private void assertTwoInstructionsAreEqual(Instruction actual, Instruction expected) {
    assertEquals(actual.getId(), expected.getId(), "Instruction ID not matched.");
    assertEquals(
        actual.getMessageId(), expected.getMessageId(), "Instruction message id not matched.");
    assertEquals(
        actual.getPurchaseReferenceNumber(),
        expected.getPurchaseReferenceNumber(),
        "Instruction purchase reference number not matched.");
    assertEquals(
        actual.getPurchaseReferenceLineNumber(),
        expected.getPurchaseReferenceLineNumber(),
        "Instruction purchase reference line number not matched.");
    assertEquals(
        actual.getPoDcNumber(), expected.getPoDcNumber(), "Instruction po dc number not matched.");
    assertEquals(
        actual.getPrintChildContainerLabels(),
        expected.getPrintChildContainerLabels(),
        "Instruction print child container labels not matched.");
    assertEquals(
        actual.getContainer().getTrackingId(),
        expected.getContainer().getTrackingId(),
        "Instruction container not matched.");
    assertEquals(
        actual.getFacilityCountryCode(),
        expected.getFacilityCountryCode(),
        "Instruction facility country code not matched.");
    assertEquals(
        actual.getFacilityNum(),
        expected.getFacilityNum(),
        "Instruction facility number not matched.");
    assertEquals(
        actual.getCreateTs().getTime(),
        expected.getCreateTs().getTime(),
        "Instruction create timestamp not matched.");
    assertEquals(
        actual.getCreateUserId(),
        expected.getCreateUserId(),
        "Instruction created user id not matched.");
    assertEquals(
        actual.getCompleteUserId(),
        expected.getCompleteUserId(),
        "Instruction completed user id not matched.");
    assertEquals(
        actual.getCompleteTs(),
        expected.getCompleteTs(),
        "Instruction completed timestamp not matched.");
    assertEquals(
        actual.getLastChangeTs(),
        expected.getLastChangeTs(),
        "Instruction last change timestamp not matched.");
    assertEquals(
        actual.getLastChangeUserId(),
        expected.getLastChangeUserId(),
        "Instruction last change user id not matched.");
    assertEquals(
        actual.getDeliveryNumber(),
        expected.getDeliveryNumber(),
        "Instruction delivery number not matched.");
    assertEquals(
        actual.getActivityName(),
        expected.getActivityName(),
        "Instruction activity name not matched.");
    assertEquals(
        actual.getFirstExpiryFirstOut(),
        expected.getFirstExpiryFirstOut(),
        "Instruction first expiry first out not matched.");
    // assertEquals(actual.getMove(), expected.getMove(), "Instruction move not matched.");
    assertEquals(actual.getGtin(), expected.getGtin(), "Instruction gtin not matched.");
    assertEquals(
        actual.getInstructionCode(),
        expected.getInstructionCode(),
        "Instruction code not matched.");
    assertEquals(
        actual.getInstructionMsg(),
        expected.getInstructionMsg(),
        "Instruction message not matched.");
    assertEquals(
        actual.getItemDescription(),
        expected.getItemDescription(),
        "Instruction item description not matched.");
    assertEquals(
        actual.getProviderId(), expected.getProviderId(), "Instruction provider id not matched.");
    assertEquals(
        actual.getProblemTagId(),
        expected.getProblemTagId(),
        "Instruction problem tag id not matched.");
    assertEquals(
        actual.getProjectedReceiveQtyUOM(),
        expected.getProjectedReceiveQtyUOM(),
        "Instruction projected received quantity uom not matched.");
    assertEquals(
        actual.getProjectedReceiveQty(),
        expected.getProjectedReceiveQty(),
        "Instruction projected received quantity not matched.");
    assertEquals(
        actual.getSsccNumber(), expected.getSsccNumber(), "Instruction sscc number not matched.");
    /*
     * For SSTK it will be null so assertion is not required for SSTK.
     */
    if (actual.getChildContainers() != null && expected.getChildContainers() != null) {
      assertEquals(
          actual.getChildContainers().size(),
          expected.getChildContainers().size(),
          "Instruction child containers not matched.");
    }
    /*
     * For SSTK it will be null so assertion is not required for SSTK.
     */
    if (actual.getLabels() != null && expected.getLabels() != null) {
      assertEquals(
          actual.getLabels().getAvailableLabels().size(),
          expected.getLabels().getAvailableLabels().size(),
          "Instruction available labels not matched.");
      assertEquals(
          actual.getLabels().getUsedLabels().size(),
          expected.getLabels().getUsedLabels().size(),
          "Instruction used labels not matched.");
    }
  }

  @Test
  public void
      testGetSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine_ProblemTagPoPoLineExists() {
    Optional<Instruction> completedInstruction =
        instructionList
            .stream()
            .filter(instruction -> instruction.getGtin().equalsIgnoreCase("00000943037194"))
            .findAny();
    if (completedInstruction.isPresent()) {
      Instruction instruction = completedInstruction.get();
      instruction.setInstructionCode("TEST");
      instruction.setReceivedQuantity(10);
      instructionList.add(instruction);
      instructionRepository.saveAll(instructionList);
    }

    Long totalReceivedQtyByProblemTagIdAndPoPoLine =
        instructionRepository
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                "9763140004", 1, "99906938999999");
    assertNotNull(totalReceivedQtyByProblemTagIdAndPoPoLine);
    assertEquals((long) totalReceivedQtyByProblemTagIdAndPoPoLine, 10L);
  }

  @Test
  public void
      testGetSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine_ProblemTagPoPoLineNotExists() {
    Long totalReceivedQtyByProblemTagIdAndPoPoLine =
        instructionRepository
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                "9763140001", 1, "99906938999998");
    assertNull(totalReceivedQtyByProblemTagIdAndPoPoLine);
  }

  @Test
  public void testGetDeliveryDocumentsByDeliveryNumberAndInstructionSetIdReturnsDocuments() {
    List<String> deliveryDocuments =
        instructionRepository.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(1234L, 1L);
    assertTrue(deliveryDocuments.size() > 0);
    assertTrue(deliveryDocuments.size() == 2);

    for (String deliveryDocument : deliveryDocuments) {
      DeliveryDocument getDeliveryDocumentResponse =
          new Gson().fromJson(deliveryDocument, DeliveryDocument.class);
      assertTrue(
          getDeliveryDocumentResponse
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .isAtlasConvertedItem());
    }
  }

  @Test
  public void testGetDeliveryDocumentsByDeliveryNumberAndInstructionSetIdReturnsEmptyDocuments() {
    List<String> deliveryDocuments =
        instructionRepository.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(12345L, 1L);
    assertTrue(deliveryDocuments.size() == 0);
  }

  @Test
  public void
      testFindByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNullAndInstructionCodeIsNotNull() {
    List<Instruction> instructionList =
        instructionRepository.findByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNull(
            12345L, "3452352");
    assertTrue(instructionList.size() == 0);
  }

  private List<Instruction> persistInstructionsWithInstructionSetId() {
    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setPurchaseReferenceNumber("PO1");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    ItemData itemData = new ItemData();
    itemData.setAtlasConvertedItem(true);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocument1.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));

    Instruction instruction1 = new Instruction();
    instruction1.setId(123L);
    instruction1.setInstructionSetId(1L);
    instruction1.setDeliveryNumber(1234L);
    instruction1.setReceivedQuantity(10);
    instruction1.setCreateUserId("sysadmin");
    instruction1.setCreateTs(new Date());
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("1234"));
    instruction1.setGtin("00000943037204");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE P  ACK (5)");
    instruction1.setMessageId("58e1df0-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction1.setPoDcNumber("32818");
    instruction1.setPrintChildContainerLabels(false);
    instruction1.setPurchaseReferenceNumber("PO1");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId(ReceivingConstants.PROVIDER_ID);
    instruction1.setActivityName(ReceivingConstants.SSTK_ACTIVITY_NAME);
    instruction1.setFirstExpiryFirstOut(Boolean.TRUE);
    instruction1.setIsReceiveCorrection(Boolean.TRUE);
    instruction1.setDeliveryDocument(new Gson().toJson(deliveryDocument1));

    Instruction instruction2 = instruction1;
    instruction2.setId(234L);

    List<Instruction> instructions = new ArrayList<>();
    instructions.add(instruction1);
    instructions.add(instruction2);

    return instructions;
  }
}
