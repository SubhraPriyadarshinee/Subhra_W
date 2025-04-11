package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeleteInstructionServiceTest extends ReceivingTestBase {

  @InjectMocks private InstructionService instructionService;

  @Mock private InstructionRepository instructionRepository;

  Instruction instruction = new Instruction();
  private List<Instruction> instructionList = new ArrayList<Instruction>();
  private Long deliveryNumber = Long.valueOf("1527199");

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    instruction.setId(Long.valueOf("1494"));
    instruction.setContainer(null);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(deliveryNumber);
    instruction.setGtin("00000943037204");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("Sample Item");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction.setMove(null);
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140005");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(10);
    instruction.setProviderId("DA");
    instructionList.add(instruction);
  }

  @Test
  public void testDeleteInstructionList() throws ReceivingException {
    when(instructionRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(instructionList);
    doNothing().when(instructionRepository).deleteAll(instructionList);

    instructionService.deleteInstructionList(deliveryNumber);

    verify(instructionRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    verify(instructionRepository, times(1)).deleteAll(instructionList);
    reset(instructionRepository);
  }

  @Test
  public void testDeleteInstructionListWithNullResponse() {
    when(instructionRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(null);

    try {
      instructionService.deleteInstructionList(deliveryNumber);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_INSTRUCTIONS_FOR_DELIVERY);
    }

    verify(instructionRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    reset(instructionRepository);
  }

  @Test
  public void testDeleteInstructionListWithEmptyResponse() {
    when(instructionRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(new ArrayList<>());

    try {
      instructionService.deleteInstructionList(deliveryNumber);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.NO_INSTRUCTIONS_FOR_DELIVERY);
    }

    verify(instructionRepository, times(1)).findByDeliveryNumber(deliveryNumber);
    reset(instructionRepository);
  }
}
