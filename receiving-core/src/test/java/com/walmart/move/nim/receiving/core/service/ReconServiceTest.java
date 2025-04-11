package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.WFTResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReconServiceTest extends ReceivingTestBase {
  @InjectMocks InstructionService instructionService;
  @InjectMocks InstructionPersisterService instructionPersisterService;

  @Autowired InstructionRepository instructionRepository;

  private ContainerItem containerItem;
  private List<ContainerItem> containerItemList;
  private ContainerDetails containerDetails = new ContainerDetails();
  @Mock private ContainerPersisterService containerPersisterService;
  private Container container;
  private Instruction instruction;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  @Mock private ContainerRepository containerRepository;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        instructionPersisterService, "instructionRepository", instructionRepository);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);

    container = new Container();
    container.setTrackingId("a32L8990000000000000106519");
    container.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
    container.setDeliveryNumber(1l);

    populateDataInInstructionTable();
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerPersisterService);
    reset(containerRepository);
  }

  private void populateDataInInstructionTable() {
    List<Instruction> instructions = new ArrayList<>();
    Date date = new Date();

    instructionRepository.deleteAll();
    Instruction instruction = new Instruction();
    instruction.setId(1L);
    instruction.setCreateUserId("userA");
    instruction.setActivityName("SSTK");
    instruction.setReceivedQuantity(50);
    instruction.setDeliveryNumber(13579246L);
    instruction.setPurchaseReferenceNumber("12345679");
    instruction.setCreateTs(date);
    instruction.setGtin("01123840356119");
    instructions.add(instruction);

    Instruction instruction1 = new Instruction();
    instruction1.setId(2L);
    instruction1.setCreateUserId("userA");
    instruction1.setActivityName("SSTK");
    instruction1.setReceivedQuantity(100);
    instruction1.setDeliveryNumber(13579246L);
    instruction1.setPurchaseReferenceNumber("54321679");
    instruction1.setCreateTs(date);
    instruction.setGtin("01123840356118");
    instructions.add(instruction1);

    Instruction instruction2 = new Instruction();
    instruction2.setId(3L);
    instruction2.setCreateUserId("userA");
    instruction2.setActivityName("CROSSU");
    instruction2.setReceivedQuantity(100);
    instruction2.setDeliveryNumber(13579246L);
    instruction2.setPurchaseReferenceNumber("64213579");
    instruction2.setCreateTs(date);
    instruction2.setGtin("01123840356117");
    containerDetails.setTrackingId("a328990000000000000106509");
    instruction2.setContainer(containerDetails);
    instructions.add(instruction2);

    instructionRepository.saveAll(instructions);
  }

  //  @Test
  public void testGetWFTResponseForInstructionID_Success()
      throws ReceivingException, InterruptedException {

    instructionRepository.findAll();
    WFTResponse wftResponse =
        instructionService.getInstructionAndContainerDetailsForWFT(null, "1", httpHeaders);
    assertNotNull(wftResponse.getInstruction());
    Assert.assertEquals(wftResponse.getInstruction().getActivityName(), "SSTK");
  }

  @Test
  public void testGetWFTResponseForTrackingID_Success() throws ReceivingException {
    when(containerPersisterService.getContainerDetails((any(String.class)))).thenReturn(container);
    WFTResponse wftResponse =
        instructionService.getInstructionAndContainerDetailsForWFT(
            "a32L8990000000000000106519", null, httpHeaders);
    assertNotNull(wftResponse.getContainer());
    Assert.assertEquals(
        wftResponse.getContainer().getMessageId(), "aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
  }

  @Test
  public void testGetWFTResponse_Fail() throws ReceivingException {
    try {
      WFTResponse wftResponse =
          instructionService.getInstructionAndContainerDetailsForWFT(null, null, httpHeaders);
    } catch (ReceivingBadDataException e) {
      Assert.assertEquals(e.getMessage(), "Either trackingId or instructionId should be mandatory");
    }
  }
}
