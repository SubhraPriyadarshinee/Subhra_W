package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;

@Slf4j
@Service
public class MultiSkuCreateInstructionService extends DefaultBaseCreateInstructionService  {

    @Resource
    private CreateInstructionDataValidator rxValidationsService;
    @Resource
    private CreateInstructionServiceHelper createInstructionServiceHelper;


    @Override
    public InstructionResponse serveInstruction(InstructionRequest instructionRequest, DataHolder dataHolder, HttpHeaders httpHeaders) {

        InstructionResponse instructionResponse = new InstructionResponseImplNew();

        log.info("Create Instruction request for Multi-Sku - {}", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

        List<DeliveryDocument> deliveryDocuments = dataHolder.getDeliveryDocuments();
        SsccScanResponse.Container gdmContainer = dataHolder.getContainer();


        List<SsccScanResponse.Container.ItemInfo> itemInfos = gdmContainer.getItemInfo();
        // set pack count for matching items
        deliveryDocuments.forEach(
                deliveryDocument -> {
                    deliveryDocument
                            .getDeliveryDocumentLines()
                            .forEach(
                                    line -> {
                                        Optional<SsccScanResponse.Container.ItemInfo> itemDetails =
                                                itemInfos
                                                        .stream()
                                                        .filter(
                                                                itemInfo ->
                                                                        itemInfo
                                                                                .getItemNumber()
                                                                                .equalsIgnoreCase(line.getItemNbr().toString()))
                                                        .findFirst();
                                        itemDetails.ifPresent(
                                                itemInfo ->{
                                                    line.setPalletSSCC(instructionRequest.getSscc());                                                    line.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(true);
                                                    line.getAdditionalInfo()
                                                            .setPackCountInEaches(
                                                                    ReceivingUtils.conversionToEaches(
                                                                            itemInfo.getTotalUnitQty().intValue(),
                                                                            WHPK,
                                                                            line.getVendorPack(),
                                                                            line.getWarehousePack()));});
                                    });


                });

        // create multi sku pallet instruction
        Instruction instruction = new Instruction();
        instruction.setInstructionMsg(RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionMsg());
        instruction.setSsccNumber(instructionRequest.getSscc());
        instruction.setInstructionCode(
                RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType());
        instructionResponse.setInstruction(instruction);
        instructionResponse.setDeliveryDocuments(deliveryDocuments);

        return instructionResponse;
    }

    @Override
    public void validateData(DataHolder dataHolder) {
        rxValidationsService.validateNodesReceivingStatus(dataHolder.getContainer().getReceivingStatus());
    }
}
