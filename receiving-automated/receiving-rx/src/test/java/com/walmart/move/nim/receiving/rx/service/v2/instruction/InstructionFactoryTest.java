package com.walmart.move.nim.receiving.rx.service.v2.instruction;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.service.v2.CreateInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.create.*;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.update.*;
import com.walmart.move.nim.receiving.rx.service.v2.posting.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.*;
import static org.testng.Assert.*;

public class InstructionFactoryTest {

    @Mock
    CaseCreateInstructionService caseCreateInstructionService;
    @Mock
    MultiSkuCreateInstructionService multiSkuCreateInstructionService;
    @Mock
    PalletCreateInstructionService palletCreateInstructionService;
    @Mock
    PartialCaseCreateInstructionService partialCaseCreateInstructionService;
    @Mock
    PalletFromMultiSkuCreateInstruction palletFromMultiSkuCreateInstructionService;
    @Mock ProblemPalletCreateInstructionService problemPalletCreateInstructionService;
    @Mock ProblemCaseCreateInstructionService problemCaseCreateInstructionService;
    @Mock ProblemPalletFromMultiSkuCreateInstruction problemPalletFromMultiSkuCreateInstruction;
    @Mock ProblemPartialCaseCreateInstructionService problemPartialCaseCreateInstructionService;

    // epcis posting services
    @Mock private PalletReceivedPostingService palletReceivedPostingService;
    @Mock private CaseReceivedPostingService caseReceivedPostingService;
    @Mock private PartialCaseReceivedPostingService partialCaseReceivedPostingService;
    @Mock private FloorLoadedCaseReceivedPostingService floorLoadedCaseReceivedPostingService;
    @Mock private HndlAsCspkFloorLoadedCaseReceivedPostingService hndlAsCspkFloorLoadedCaseReceivedPostingService;
    @Mock private HndlAsCspkReceivedPostingService hndlAsCspkReceivedPostingService;
    @Mock private DefaultReceivedPostingService defaultReceivedPostingService;

    @Mock
    PalletProcessInstructionService palletProcessInstructionService;
    @Mock
    CaseProcessInstructionService caseProcessInstructionService;
    @Mock
    FloorLoadedCaseProcessInstructionService floorLoadedCaseProcessInstructionService;
    @Mock
    PalletFromMultiSkuProcessInstructionService palletFromMultiSkuProcessInstructionService;
    @Mock
    PartialCaseProcessInstructionService partialCaseProcessInstructionService;

    @InjectMocks private InstructionFactory instructionFactory;

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
        instructionFactory.getCreateInstructionService();
    }

    @AfterMethod
    public void tearDown() {
        Mockito.reset(caseCreateInstructionService);
        Mockito.reset(multiSkuCreateInstructionService);
        Mockito.reset(palletCreateInstructionService);
        Mockito.reset(partialCaseCreateInstructionService);
        Mockito.reset(palletFromMultiSkuCreateInstructionService);
        Mockito.reset(problemPalletCreateInstructionService);
        Mockito.reset(problemCaseCreateInstructionService);
        Mockito.reset(problemPalletFromMultiSkuCreateInstruction);
        Mockito.reset(problemPartialCaseCreateInstructionService);
        Mockito.reset(palletReceivedPostingService);
        Mockito.reset(caseReceivedPostingService);
        Mockito.reset(partialCaseReceivedPostingService);
        Mockito.reset(floorLoadedCaseReceivedPostingService);
        Mockito.reset(hndlAsCspkFloorLoadedCaseReceivedPostingService);
        Mockito.reset(hndlAsCspkReceivedPostingService);
        Mockito.reset(defaultReceivedPostingService);
        Mockito.reset(palletProcessInstructionService);
        Mockito.reset(caseProcessInstructionService);
        Mockito.reset(floorLoadedCaseProcessInstructionService);
        Mockito.reset(palletFromMultiSkuProcessInstructionService);
        Mockito.reset(partialCaseProcessInstructionService);

    }


    @Test
    public void testGetCreateInstructionService() {
        Assert.assertTrue(instructionFactory.createInstructionServiceMap.size() > 0);
    }

    @Test
    public void testTestGetCreateInstructionService() throws ReceivingException {
        Assert.assertEquals(palletCreateInstructionService, instructionFactory.getCreateInstructionService("FULL-PALLET"));
        Assert.assertEquals(caseCreateInstructionService, instructionFactory.getCreateInstructionService("PLT-UNPACKED-AND-CASES-RCVD"));
        Assert.assertEquals(partialCaseCreateInstructionService, instructionFactory.getCreateInstructionService("PARTIAL-CASE"));
        Assert.assertEquals(multiSkuCreateInstructionService, instructionFactory.getCreateInstructionService("MULTI-SKU"));
        Assert.assertEquals(palletFromMultiSkuCreateInstructionService, instructionFactory.getCreateInstructionService("MULTI-SKU-PLT-UNPACKED-AND-RCVD"));
        Assert.assertEquals(caseCreateInstructionService, instructionFactory.getCreateInstructionService("FLOOR-LOADED-CASE"));
        Assert.assertEquals(palletFromMultiSkuCreateInstructionService, instructionFactory.getCreateInstructionService("HNDL-AS-CSPK-PLT-UNPACKED-AND-CASES-RCVD"));
        Assert.assertEquals(palletFromMultiSkuCreateInstructionService, instructionFactory.getCreateInstructionService("HNDL-AS-CSPK-FLOOR-LOADED-CASE"));
        Assert.assertEquals(palletCreateInstructionService, instructionFactory.getCreateInstructionService("HNDL-AS-CSPK-FULL-PALLET"));
        Assert.assertEquals(problemPalletFromMultiSkuCreateInstruction, instructionFactory.getCreateInstructionService("PROBLEM-HNDL-AS-CSPK-FLOOR-LOADED-CASE"));
        Assert.assertEquals(problemPalletCreateInstructionService, instructionFactory.getCreateInstructionService("PROBLEM-FULL-PALLET"));
        Assert.assertEquals(problemCaseCreateInstructionService, instructionFactory.getCreateInstructionService("PROBLEM-PLT-UNPACKED-AND-CASES-RCVD"));
        Assert.assertEquals(problemCaseCreateInstructionService, instructionFactory.getCreateInstructionService("PROBLEM-FLOOR-LOADED-CASE"));
        Assert.assertEquals(problemPartialCaseCreateInstructionService, instructionFactory.getCreateInstructionService("PROBLEM-PARTIAL-CASE"));
        Assert.assertEquals(problemPalletFromMultiSkuCreateInstruction, instructionFactory.getCreateInstructionService("PROBLEM-MULTI-SKU-PLT-UNPACKED-AND-RCVD"));
        Assert.assertEquals(problemPalletFromMultiSkuCreateInstruction, instructionFactory.getCreateInstructionService("PROBLEM-HNDL-AS-CSPK-PLT-UNPACKED-AND-CASES-RCVD"));
        Assert.assertEquals(problemPalletCreateInstructionService, instructionFactory.getCreateInstructionService("PROBLEM-HNDL-AS-CSPK-FULL-PALLET"));
        Assert.assertEquals(palletFromMultiSkuCreateInstructionService, instructionFactory.getCreateInstructionService("HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD"));
        Assert.assertEquals(problemPalletFromMultiSkuCreateInstruction, instructionFactory.getCreateInstructionService("PROBLEM-HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD"));

        String name = "INVALID_NAME";
        Throwable exception = Assertions.assertThrows(ReceivingException.class,
                () -> instructionFactory.getCreateInstructionService(name));
        Assertions.assertEquals(  "No handler found for create instruction of "+name, exception.getMessage());



    }

    @Test
    public void testGetUpdateInstructionService() throws ReceivingException {
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(FULL_PALLET));
        Assert.assertEquals(caseProcessInstructionService, instructionFactory.getUpdateInstructionService(CASE));
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(PALLET_RECEIVED_WITH_CASE_SCANS));
        Assert.assertEquals(partialCaseProcessInstructionService, instructionFactory.getUpdateInstructionService(PARTIAL_CASE));
        Assert.assertEquals(caseProcessInstructionService, instructionFactory.getUpdateInstructionService(CASE_RECEIVED_WITH_UNIT_SCANS));
        Assert.assertEquals(floorLoadedCaseProcessInstructionService, instructionFactory.getUpdateInstructionService(FLOOR_LOADED_CASE));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(HNDL_AS_CSPK_FLOOR_LOADED_CASE));
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(HNDL_AS_CSPK_FULL_PALLET));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE));
        Assert.assertEquals(caseProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_CASE));
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_FULL_PALLET));
        Assert.assertEquals(partialCaseProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_PARTIAL_CASE));
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS));
        Assert.assertEquals(floorLoadedCaseProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_FLOOR_LOADED_CASE));
        Assert.assertEquals(caseProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD));
        Assert.assertEquals(palletProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_HNDL_AS_CSPK_FULL_PALLET));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(palletFromMultiSkuProcessInstructionService, instructionFactory.getUpdateInstructionService(PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU));

        String name = "INVALID_NAME";
        Throwable exception = Assertions.assertThrows(ReceivingException.class,
                () -> instructionFactory.getUpdateInstructionService(name));
        Assertions.assertEquals(  "No handler found for update instruction of "+name, exception.getMessage());


    }

    @Test
    public void testGetEpcisPostingService() {
        Assert.assertEquals(palletReceivedPostingService, instructionFactory.getEpcisPostingService(FULL_PALLET));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(HNDL_AS_CSPK_FULL_PALLET));
        Assert.assertEquals(palletReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_FULL_PALLET));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_HNDL_AS_CSPK_FULL_PALLET));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(CASE));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(PALLET_RECEIVED_WITH_CASE_SCANS));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_CASE));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(caseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD));
        Assert.assertEquals(partialCaseReceivedPostingService, instructionFactory.getEpcisPostingService(PARTIAL_CASE));
        Assert.assertEquals(partialCaseReceivedPostingService, instructionFactory.getEpcisPostingService(CASE_RECEIVED_WITH_UNIT_SCANS));
        Assert.assertEquals(partialCaseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_PARTIAL_CASE));
        Assert.assertEquals(partialCaseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS));
        Assert.assertEquals(floorLoadedCaseReceivedPostingService, instructionFactory.getEpcisPostingService(FLOOR_LOADED_CASE));
        Assert.assertEquals(floorLoadedCaseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_FLOOR_LOADED_CASE));
        Assert.assertEquals(hndlAsCspkFloorLoadedCaseReceivedPostingService, instructionFactory.getEpcisPostingService(HNDL_AS_CSPK_FLOOR_LOADED_CASE));
        Assert.assertEquals(hndlAsCspkFloorLoadedCaseReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU));
        Assert.assertEquals(hndlAsCspkReceivedPostingService, instructionFactory.getEpcisPostingService(PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU));

        // INVALID
        Assert.assertEquals(defaultReceivedPostingService, instructionFactory.getEpcisPostingService("INVALID_NAME"));
    }
}