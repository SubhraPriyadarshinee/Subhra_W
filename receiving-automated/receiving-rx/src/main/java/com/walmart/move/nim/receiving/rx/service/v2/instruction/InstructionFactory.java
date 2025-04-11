package com.walmart.move.nim.receiving.rx.service.v2.instruction;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.service.v2.CreateInstructionService;
import com.walmart.move.nim.receiving.core.service.v2.EpcisPostingService;
import com.walmart.move.nim.receiving.core.service.v2.ProcessInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.create.*;
import com.walmart.move.nim.receiving.rx.service.v2.posting.*;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.update.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.*;


@Component
public class InstructionFactory {
    @Autowired CaseCreateInstructionService caseCreateInstructionService;
    @Autowired MultiSkuCreateInstructionService multiSkuCreateInstructionService;
    @Autowired PalletCreateInstructionService palletCreateInstructionService;
    @Autowired PartialCaseCreateInstructionService partialCaseCreateInstructionService;
    @Autowired PalletFromMultiSkuCreateInstruction palletFromMultiSkuCreateInstructionService;
    @Autowired ProblemPalletCreateInstructionService problemPalletCreateInstructionService;
    @Autowired ProblemCaseCreateInstructionService problemCaseCreateInstructionService;
    @Autowired ProblemPalletFromMultiSkuCreateInstruction problemPalletFromMultiSkuCreateInstruction;
    @Autowired ProblemPartialCaseCreateInstructionService problemPartialCaseCreateInstructionService;

    // epcis posting services
    @Resource private PalletReceivedPostingService palletReceivedPostingService;
    @Resource private CaseReceivedPostingService caseReceivedPostingService;
    @Resource private PartialCaseReceivedPostingService partialCaseReceivedPostingService;
    @Resource private FloorLoadedCaseReceivedPostingService floorLoadedCaseReceivedPostingService;
    @Resource private HndlAsCspkFloorLoadedCaseReceivedPostingService hndlAsCspkFloorLoadedCaseReceivedPostingService;
    @Resource private HndlAsCspkReceivedPostingService hndlAsCspkReceivedPostingService;
    @Resource private DefaultReceivedPostingService defaultReceivedPostingService;

    @Autowired
    PalletProcessInstructionService palletProcessInstructionService;
    @Autowired
    CaseProcessInstructionService caseProcessInstructionService;
    @Autowired
    FloorLoadedCaseProcessInstructionService floorLoadedCaseProcessInstructionService;
    @Autowired
    PalletFromMultiSkuProcessInstructionService palletFromMultiSkuProcessInstructionService;
    @Autowired
    PartialCaseProcessInstructionService partialCaseProcessInstructionService;

    Map<String, CreateInstructionService> createInstructionServiceMap = new HashMap<>();

    Map<String, ProcessInstructionService> updateInstructionServiceMap = new HashMap<>();
    Map<String, EpcisPostingService> epcisPostingServiceMap = new HashMap<>();

    @PostConstruct
    public void getCreateInstructionService(){
        createInstructionServiceMap.put(FULL_PALLET, palletCreateInstructionService);
        createInstructionServiceMap.put(CASE, caseCreateInstructionService);
        createInstructionServiceMap.put(PARTIAL_CASE, partialCaseCreateInstructionService);
        createInstructionServiceMap.put(MULTI_SKU, multiSkuCreateInstructionService);
        createInstructionServiceMap.put(PALLET_FROM_MULTI_SKU, palletFromMultiSkuCreateInstructionService);
        createInstructionServiceMap.put(FLOOR_LOADED_CASE, caseCreateInstructionService);
        createInstructionServiceMap.put(HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD,palletFromMultiSkuCreateInstructionService);
        createInstructionServiceMap.put(HNDL_AS_CSPK_FLOOR_LOADED_CASE,palletFromMultiSkuCreateInstructionService);
        createInstructionServiceMap.put(HNDL_AS_CSPK_FULL_PALLET, palletCreateInstructionService);
        createInstructionServiceMap.put(HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, palletFromMultiSkuCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE,problemPalletFromMultiSkuCreateInstruction);
        createInstructionServiceMap.put(PROBLEM_FULL_PALLET,problemPalletCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_CASE,problemCaseCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_FLOOR_LOADED_CASE,problemCaseCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_PARTIAL_CASE,problemPartialCaseCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_PALLET_FROM_MULTI_SKU,problemPalletFromMultiSkuCreateInstruction);
        createInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD,problemPalletFromMultiSkuCreateInstruction);
        createInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_FULL_PALLET, problemPalletCreateInstructionService);
        createInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, problemPalletFromMultiSkuCreateInstruction);

        updateInstructionServiceMap.put(FULL_PALLET, palletProcessInstructionService);
        updateInstructionServiceMap.put(CASE, caseProcessInstructionService);
        updateInstructionServiceMap.put(PALLET_RECEIVED_WITH_CASE_SCANS, palletProcessInstructionService);
        updateInstructionServiceMap.put(PARTIAL_CASE, partialCaseProcessInstructionService);
        updateInstructionServiceMap.put(CASE_RECEIVED_WITH_UNIT_SCANS, caseProcessInstructionService);
        updateInstructionServiceMap.put(FLOOR_LOADED_CASE, floorLoadedCaseProcessInstructionService);
        updateInstructionServiceMap.put(PALLET_FROM_MULTI_SKU, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(HNDL_AS_CSPK_FLOOR_LOADED_CASE, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(HNDL_AS_CSPK_FULL_PALLET, palletProcessInstructionService);
        updateInstructionServiceMap.put(HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_CASE, caseProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_FULL_PALLET, palletProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_PARTIAL_CASE, partialCaseProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS, palletProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_FLOOR_LOADED_CASE, floorLoadedCaseProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS, caseProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_PALLET_FROM_MULTI_SKU, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD, palletFromMultiSkuProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_FULL_PALLET, palletProcessInstructionService);
        updateInstructionServiceMap.put(PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, palletFromMultiSkuProcessInstructionService);



        // epcisPostingServiceMap
        // pallet posting
        epcisPostingServiceMap.put(FULL_PALLET, palletReceivedPostingService);
        epcisPostingServiceMap.put(HNDL_AS_CSPK_FULL_PALLET, hndlAsCspkReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_FULL_PALLET, palletReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_HNDL_AS_CSPK_FULL_PALLET, hndlAsCspkReceivedPostingService);

        // case posting
        epcisPostingServiceMap.put(CASE, caseReceivedPostingService);
        epcisPostingServiceMap.put(PALLET_FROM_MULTI_SKU, caseReceivedPostingService);
        epcisPostingServiceMap.put(PALLET_RECEIVED_WITH_CASE_SCANS, caseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_CASE, caseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_PALLET_FROM_MULTI_SKU, caseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS, caseReceivedPostingService);

        //compliance packs posting
        epcisPostingServiceMap.put(HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD, hndlAsCspkReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD, hndlAsCspkReceivedPostingService);
        epcisPostingServiceMap.put(HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, hndlAsCspkReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU, hndlAsCspkReceivedPostingService);

        // partial case posting
        epcisPostingServiceMap.put(PARTIAL_CASE, partialCaseReceivedPostingService);
        epcisPostingServiceMap.put(CASE_RECEIVED_WITH_UNIT_SCANS, partialCaseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_PARTIAL_CASE, partialCaseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS, partialCaseReceivedPostingService);

        // floor loaded case posting
        epcisPostingServiceMap.put(FLOOR_LOADED_CASE, floorLoadedCaseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_FLOOR_LOADED_CASE, floorLoadedCaseReceivedPostingService);

        //floor loaded compliance packs posting
        epcisPostingServiceMap.put(HNDL_AS_CSPK_FLOOR_LOADED_CASE, hndlAsCspkFloorLoadedCaseReceivedPostingService);
        epcisPostingServiceMap.put(PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE, hndlAsCspkFloorLoadedCaseReceivedPostingService);
    }

    public CreateInstructionService getCreateInstructionService(String name) throws ReceivingException {
        CreateInstructionService createInstructionService = createInstructionServiceMap.get(name);
        if(Objects.isNull(createInstructionService)){
            throw new ReceivingException("No handler found for create instruction of "+name);
        }
        return createInstructionService;
    }

    public ProcessInstructionService getUpdateInstructionService(String name) throws ReceivingException {
        ProcessInstructionService updateInstructionService = updateInstructionServiceMap.get(name);
        if(Objects.isNull(updateInstructionService)){
            throw new ReceivingException("No handler found for update instruction of "+name);
        }
        return updateInstructionService;
    }

    public EpcisPostingService getEpcisPostingService(String receivingMethod) {
        return epcisPostingServiceMap.getOrDefault(receivingMethod, defaultReceivedPostingService);
    }
}
