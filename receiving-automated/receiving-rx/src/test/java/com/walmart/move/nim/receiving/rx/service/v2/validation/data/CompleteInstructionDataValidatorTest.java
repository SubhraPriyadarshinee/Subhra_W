package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CompleteInstructionDataValidatorTest {

    @Mock private InstructionPersisterService instructionPersisterService;
    @Mock private RxInstructionService rxInstructionService;

    @InjectMocks private CompleteInstructionDataValidator completeInstructionDataValidator;
    private Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(instructionPersisterService);
        Mockito.reset(rxInstructionService);

    }
    @Test
    public void testValidateAndGetInstruction() throws ReceivingException {
        Instruction instruction = MockInstruction.getInstruction();
        instruction.setLastChangeUserId("testUser");
        instruction.setCreateUserId("testUser");
        Mockito.doNothing().when(rxInstructionService).validateInstructionCompleted(any());
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Instruction returnedValue = completeInstructionDataValidator.validateAndGetInstruction(123L, "testUser");
        Assert.assertNotNull(returnedValue);

        instruction.setLastChangeUserId("");
        instruction.setCreateUserId("testUser");
        Mockito.doNothing().when(rxInstructionService).validateInstructionCompleted(any());
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
        Instruction returnedValue1 = completeInstructionDataValidator.validateAndGetInstruction(123L, "testUser");
        Assert.assertNotNull(returnedValue1);

    }

    @Test
    public void testIsEpcisSmartReceivingFlow_true() {
        Instruction instruction = MockInstruction.getInstruction();
        instruction.setInstructionCode("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        deliveryDocumentLine.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(true);
        Assert.assertTrue(completeInstructionDataValidator.isEpcisSmartReceivingFlow(instruction, deliveryDocument));

    }

    @Test
    public void testIsEpcisSmartReceivingFlow_false() {
        Instruction instruction = MockInstruction.getInstruction();
        instruction.setInstructionCode("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        deliveryDocumentLine.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(false);
        Assert.assertFalse(completeInstructionDataValidator.isEpcisSmartReceivingFlow(instruction, deliveryDocument));

    }
}