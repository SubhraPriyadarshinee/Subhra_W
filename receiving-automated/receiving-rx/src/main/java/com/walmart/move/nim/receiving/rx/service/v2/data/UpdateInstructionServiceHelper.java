package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Sgtin;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ShipmentsContainersV2Request;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rx.builders.RxContainerItemBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.UPDATE_INSTRUCTION_SCAN_VALID_CASE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.UTC_TIME_ZONE;
import static java.util.Optional.ofNullable;

@Component
public class UpdateInstructionServiceHelper {

    @Autowired
    private Gson gson;

    @Resource
    private RxDeliveryServiceImpl rxDeliveryServiceImpl;

    @Autowired private RxInstructionHelperService rxInstructionHelperService;

    @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
    private LPNCacheService lpnCacheService;

    @Autowired private ReceiptService receiptService;

    @Autowired private ContainerService containerService;

    @Autowired private RxContainerItemBuilder containerItemBuilder;

    @Autowired private ContainerItemService containerItemService;

    @Autowired private UpdateInstructionServiceHelper updateInstructionServiceHelper;

    @Autowired private RxInstructionService rxInstructionService;

    @Autowired InstructionStateValidator instructionStateValidator;

    @Autowired
    UpdateInstructionDataValidator updateInstructionDataValidator;


    @Autowired InstructionPersisterService instructionPersisterService;
    @Autowired private TenantSpecificConfigReader configUtils;


    private static final String DB_PROCESS_TIME_LOG_MESSAGE =
            "Time take to complete update instruction DB Tx is {} ms and correlation id is {}";

    public ItemData getAdditionalInfoFromDeliveryDoc(Instruction instruction4mDB) {
        ItemData additionalInfo = null;
        if (null!= instruction4mDB.getDeliveryDocument()) {
            DeliveryDocument deliveryDocument = gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
            if (deliveryDocument.getDeliveryDocumentLines()!= null &&!deliveryDocument.getDeliveryDocumentLines().isEmpty()) {
                additionalInfo = deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo();
            }
        }
        return additionalInfo;
    }

    public List<String> getValidInstructionCodes() {
        return Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN,
                ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_BUILD_UNIT_SCAN, ReportingConstants.RX_SER_MULTI_SKU_PALLET);
    }

    public SsccScanResponse.Container convertToGDMContainer(Map<String, ScannedData> scannedDataMap){
        if (MapUtils.isEmpty(scannedDataMap)) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA);
        }
        SsccScanResponse.Container ssccScanContainer = new SsccScanResponse.Container();

        ScannedData gtinScannedData = scannedDataMap.get(ReceivingConstants.KEY_GTIN);
        if (null != gtinScannedData && StringUtils.isNotBlank(gtinScannedData.getValue())) {
            ssccScanContainer.setGtin(gtinScannedData.getValue());
        } else{
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_GTIN);
        }

        ScannedData lotScannedData = scannedDataMap.get(ReceivingConstants.KEY_LOT);
        if (null != lotScannedData && StringUtils.isNotBlank(lotScannedData.getValue())) {
            ssccScanContainer.setLotNumber(lotScannedData.getValue());
        } else{
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_LOT);
        }

        ScannedData keyScannedData = scannedDataMap.get(ReceivingConstants.KEY_SERIAL);
        if (null != keyScannedData && StringUtils.isNotBlank(keyScannedData.getValue())) {
            ssccScanContainer.setSerial(keyScannedData.getValue());
        } else{
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_SERIAL);
        }

        ScannedData expDateScannedData = scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE);
        if (null != expDateScannedData && StringUtils.isNotBlank(expDateScannedData.getValue())) {
            try {
                Date expDate =
                        DateUtils.parseDate(
                                expDateScannedData.getValue(), ReceivingConstants.EXPIRY_DATE_FORMAT);
                ssccScanContainer.setExpiryDate(
                        new SimpleDateFormat(ReceivingConstants.SIMPLE_DATE).format(expDate));
            } catch (ParseException e) {
                throw new ReceivingBadDataException(
                        ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
            }
        } else {
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
        }
        return ssccScanContainer;
    }

    public void validateInstructionOwner(HttpHeaders httpHeaders , DataHolder dataForUpdateInstruction) throws ReceivingException {
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        ReceivingUtils.verifyUser(dataForUpdateInstruction.getInstruction(), userId, RequestType.UPDATE);
    }

    public Map<ItemData, SsccScanResponse.Container> verifyScanned2DWithGDMData(
            Instruction instruction4mDB,
            ItemData additionalInfo,
            Map<String, ScannedData> scannedDataMap,
            int quantityToBeReceived,
            DeliveryDocument deliveryDocument4mDB,
            DeliveryDocumentLine deliveryDocumentLine4mDB,
            boolean setAuditQty,
            SsccScanResponse.Container gdmResponseForScannedData
    ) {

        Map<ItemData, SsccScanResponse.Container> selectedGDMInfoList = new HashMap<>();

        SsccScanResponse.Container containerScannedFromUI = convertToGDMContainer(scannedDataMap);

        boolean isValidationSuccess = true;
        String causeOfFailure = "";
        if (!gdmResponseForScannedData.getExpiryDate().equalsIgnoreCase(containerScannedFromUI.getExpiryDate()) &&
                !gdmResponseForScannedData.getLotNumber().equalsIgnoreCase(containerScannedFromUI.getLotNumber())) {
            isValidationSuccess = false;
            causeOfFailure = RxConstants.BOTH_EXPIRY_DATE_AND_LOT;
        }
        else if (!gdmResponseForScannedData.getExpiryDate().equalsIgnoreCase(containerScannedFromUI.getExpiryDate())) {
            isValidationSuccess = false;
            causeOfFailure = ReceivingConstants.KEY_EXPIRY_DATE;
        }
        else if (!gdmResponseForScannedData.getLotNumber().equalsIgnoreCase(containerScannedFromUI.getLotNumber())) {
            isValidationSuccess = false;
            causeOfFailure = ReceivingConstants.KEY_LOT;
        }

        if (!isValidationSuccess) {
            String instructionPackageInfo = instruction4mDB.getInstructionCreatedByPackageInfo();
            SsccScanResponse.Container instructionPack = gson.fromJson(instructionPackageInfo,  SsccScanResponse.Container.class);
            if (quantityToBeReceived != additionalInfo.getAuditQty() && setAuditQty) {
                if (CollectionUtils.containsAny(instructionPack.getHints(), RxConstants.GdmHints.HANDLE_AS_CASEPACK)) {
                    throw new ReceivingBadDataException(ExceptionCodes.UPDATE_INSTRUCTION_ERROR, UPDATE_INSTRUCTION_SCAN_VALID_CASE);
                }
                additionalInfo.setAuditQty(quantityToBeReceived);
                deliveryDocumentLine4mDB.setAdditionalInfo(additionalInfo);
                deliveryDocument4mDB.setDeliveryDocumentLines(
                        Collections.singletonList(deliveryDocumentLine4mDB));
                instruction4mDB.setDeliveryDocument(gson.toJson(deliveryDocument4mDB));

                String instructionCode = instruction4mDB.getInstructionCode();
                if (instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_BUILD_PALLET)){
                    instruction4mDB.setReceivingMethod(RxConstants.ReceivingTypes.PALLET_RECEIVED_WITH_CASE_SCANS);
                }
                else if (instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_CNTR_CASE_SCAN)
                        || instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT )){
                    instruction4mDB.setReceivingMethod(RxConstants.ReceivingTypes.CASE_RECEIVED_WITH_UNIT_SCANS);
                }
                rxInstructionHelperService.saveInstruction(instruction4mDB);

                throwExceptionBasedOnInvalidScanCause(ExceptionCodes.AUDIT_2D_DETAILS_DO_NOT_MATCH, causeOfFailure);

            } else {
                throwExceptionBasedOnInvalidScanCause(ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH, causeOfFailure);
            }
        } else {
            additionalInfo.setAuditCompletedQty(
                    additionalInfo.getAuditCompletedQty() + ReceivingConstants.ONE);
        }
        selectedGDMInfoList.put(additionalInfo, gdmResponseForScannedData);
        return selectedGDMInfoList;
    }

    private void throwExceptionBasedOnInvalidScanCause(String exceptionCode, String causeOfFailure) {
        String errorMessage = "Please quarantine this freight and submit a problem ticket.";
        switch (causeOfFailure) {
            case ReceivingConstants.KEY_EXPIRY_DATE:
                errorMessage = "Scanned expiry date does not match with EPCIS data. Please quarantine this freight and submit a problem ticket.";
                exceptionCode = ExceptionCodes.SCANNED_EXPIRY_DATE_DETAILS_DO_NOT_MATCH;
                break;
            case ReceivingConstants.KEY_LOT:
                errorMessage = "Scanned LOT does not match with EPCIS data. Please quarantine this freight and submit a problem ticket.";
                exceptionCode = ExceptionCodes.SCANNED_LOT_DETAILS_DO_NOT_MATCH;
                break;
            case RxConstants.BOTH_EXPIRY_DATE_AND_LOT:
                errorMessage = "Scanned Expiry Date and LOT do not match with EPCIS data. Please quarantine this freight and submit a problem ticket.";
                exceptionCode = ExceptionCodes.SCANNED_EXPIRY_DATE_AND_LOT_DETAILS_DO_NOT_MATCH;
                break;
            case RxConstants.PARENT_ID:
                errorMessage = "Scanned barcode does not belong to this pallet/case. Please quarantine this freight and submit a problem ticket.";
                exceptionCode = ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH_WITH_PARENT;
                break;
        }
        throw new ReceivingBadDataException(
                exceptionCode, errorMessage);
    }

    public Integer totalQuantityValueAfterReceiving(
            Integer receivedQuantity, Integer quantityToBeReceivied) {
        return receivedQuantity + quantityToBeReceivied;
    }
    
    public void createPalletContainer(List<Container> containers, List<ContainerItem> containerItems,
                                      Instruction instruction4mDB, DeliveryDocument deliveryDocument4mDB,
                                      UpdateInstructionRequest instructionUpdateRequestFromClient,
                                      HttpHeaders httpHeaders, String userId, SsccScanResponse.Container instructionPack, DocumentLine documentLine,
                                      Integer quantityToBeReceivedInEaches){

        Container parentContainer = null;
        if (Objects.isNull(instruction4mDB.getContainer())) {
            String parentContainerTrackingId = generateTrackingId(httpHeaders);
            ContainerDetails parentContainerDetails =
                    buildContainerDetails(deliveryDocument4mDB, parentContainerTrackingId);
            instruction4mDB.setContainer(parentContainerDetails);

            try {
                parentContainer =
                        containerService.constructContainer(
                                instruction4mDB,
                                parentContainerDetails,
                                Boolean.TRUE,
                                Boolean.TRUE,
                                instructionUpdateRequestFromClient);
            } catch (ReceivingException e) {
                throw new RuntimeException(e);
            }
            parentContainer.setLastChangedTs(new Date());
            parentContainer.setLastChangedUser(userId);

            String instructionCode = instruction4mDB.getInstructionCode();
            if (instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_BUILD_PALLET)){

                if (instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.FULL_PALLET)
                        || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_FULL_PALLET)
                        || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.HNDL_AS_CSPK_FULL_PALLET)
                        || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_HNDL_AS_CSPK_FULL_PALLET)){
                    parentContainer.setRcvgContainerType(RxConstants.ReceivingContainerTypes.FULL_PALLET);
                }

                populateContainerMiscInfoV2(
                        parentContainer, instruction4mDB, instructionPack);
            } else{
                populateContainerMiscInfoV2(
                        parentContainer, instruction4mDB, null);
            }

            containers.add(parentContainer);

            ContainerItem parentContainerItem =
                    containerItemBuilder.build(
                            parentContainerTrackingId,
                            instruction4mDB,
                            instructionUpdateRequestFromClient,
                            Collections.emptyMap());
            parentContainerItem.setRotateDate(parseAndGetRotateDate());
            parentContainerItem.setQuantity(quantityToBeReceivedInEaches);
            parentContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
            containerItems.add(parentContainerItem);
        } else {
            Integer quantityReceivedInEaches =
                    ReceivingUtils.conversionToEaches(
                            instruction4mDB.getReceivedQuantity(),
                            instruction4mDB.getReceivedQuantityUOM(),
                            documentLine.getVnpkQty(),
                            documentLine.getWhpkQty());
            Pair<Container, ContainerItem> updatedContainerContainerItem =
                    null;
            try {
                updatedContainerContainerItem = updateContainerQuantity(
                        instruction4mDB.getContainer().getTrackingId(), quantityReceivedInEaches, userId);
            } catch (ReceivingException e) {
                throw new RuntimeException(e);
            }
            containers.add(updatedContainerContainerItem.getKey());
            containerItems.add(updatedContainerContainerItem.getValue());
        }
    }


    public String generateTrackingId(HttpHeaders httpHeaders) {
        String trackingId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
        if (StringUtils.isBlank(trackingId)) {
            throw new ReceivingBadDataException(ExceptionCodes.INVALID_LPN, RxConstants.INVALID_LPN);
        }
        return trackingId;
    }


    private ContainerDetails buildContainerDetails(
            DeliveryDocument deliveryDocument4mDB, String trackingId) {

        Content content = InstructionUtils.createContent(deliveryDocument4mDB);
        content.setQtyUom(ReceivingConstants.Uom.EACHES);
        content.setRotateDate(DateFormatUtils.format(parseAndGetRotateDate(), ReceivingConstants.SIMPLE_DATE));

        ContainerDetails containerDetails = new ContainerDetails();
        containerDetails.setTrackingId(trackingId);
        containerDetails.setContents(Arrays.asList(content));

        return containerDetails;
    }

    private void populateContainerMiscInfoV2(
            Container container,
            Instruction instruction,
            SsccScanResponse.Container ssccScanContainer) {
        Map<String, Object> containerMiscInfo =
                Objects.isNull(container.getContainerMiscInfo())
                        ? new HashMap<>()
                        : container.getContainerMiscInfo();
        containerMiscInfo.put(RxConstants.INSTRUCTION_CODE, instruction.getInstructionCode());
        containerMiscInfo.put(ReceivingConstants.IS_EPCIS_ENABLED_VENDOR, true);
        if (Objects.nonNull(ssccScanContainer)) {
            containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, ssccScanContainer.getShipmentNumber());
            containerMiscInfo.put(RxConstants.GDM_CONTAINER_ID,ssccScanContainer.getId());
            containerMiscInfo.put(RxConstants.GDM_PARENT_CONTAINER_ID,ssccScanContainer.getParentId());
            containerMiscInfo.put(RxConstants.TOP_LEVEL_CONTAINER_SSCC,ssccScanContainer.getTopLevelContainerSscc());
            containerMiscInfo.put(ReceivingConstants.KEY_SSCC,ssccScanContainer.getSscc());
            containerMiscInfo.put(ReceivingConstants.KEY_GTIN,ssccScanContainer.getGtin());
            containerMiscInfo.put(ReceivingConstants.KEY_SERIAL,ssccScanContainer.getSerial());
            containerMiscInfo.put(ReceivingConstants.KEY_LOT,ssccScanContainer.getLotNumber());
            containerMiscInfo.put(ReceivingConstants.KEY_EXPIRY_DATE,ssccScanContainer.getExpiryDate());
            containerMiscInfo.put(RxConstants.UNIT_COUNT,ssccScanContainer.getUnitCount());
            containerMiscInfo.put(RxConstants.CHILD_COUNT,ssccScanContainer.getChildCount());
            containerMiscInfo.put(RxConstants.HINTS,ssccScanContainer.getHints());
            containerMiscInfo.put(RxConstants.TOP_LEVEL_CONTAINER_ID, ssccScanContainer.getTopLevelContainerId());
        }
        container.setContainerMiscInfo(containerMiscInfo);
    }



    private Pair<Container, ContainerItem> updateContainerQuantity(
            String trackingId, int quantityInEaches, String userId) throws ReceivingException {

        Container container = containerService.getContainerByTrackingId(trackingId);
        if (Objects.isNull(container)) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.CONTAINER_NOT_FOUND, ReceivingException.MATCHING_CONTAINER_NOT_FOUND);
        }

        container.setLastChangedTs(new Date());
        container.setLastChangedUser(userId);

        // create parent container item
        ContainerItem containerItem = container.getContainerItems().get(0);
        containerItem.setQuantity(quantityInEaches);
        return new Pair<>(container, containerItem);
    }

    public Date parseAndGetRotateDate() {
        String dcTimeZone;
        if (Objects.nonNull(TenantContext.getFacilityNum())) {
            dcTimeZone = configUtils.getDCTimeZone(TenantContext.getFacilityNum());
        } else {
            dcTimeZone = UTC_TIME_ZONE;
        }
        try {
            return DateUtils.parseDate(
                            (ReceivingUtils.getDCDateTime(dcTimeZone).toLocalDate().toString()),
                            RxConstants.ROTATE_DATE_FORMAT);
        } catch (ParseException e) {
            throw new ReceivingBadDataException(
                    ExceptionCodes.INVALID_ROTATE_DATE_ERROR_CODE,
                    RxConstants.INVALID_ROTATE_DATE);
        }
    }



    public String createCaseContainer (List<Container> containers, List<ContainerItem> containerItems,
                                 Instruction instruction4mDB, DeliveryDocument deliveryDocument4mDB,
                                 UpdateInstructionRequest instructionUpdateRequestFromClient,
                                 HttpHeaders httpHeaders, String userId, SsccScanResponse.Container instructionPack, DocumentLine documentLine,
                                 Map<String, ScannedData> scannedDataMap, Integer quantityToBeReceivedInEaches, String parentTrackingId){

        String caseContainerTrackingId =
                StringUtils.isNotBlank(parentTrackingId) ? parentTrackingId : null;

        if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(documentLine.getQuantityUOM())) {
            if (StringUtils.isBlank(parentTrackingId)) {
                caseContainerTrackingId = generateTrackingId(httpHeaders);
                ContainerDetails eachesParentContainerDetails =
                        buildContainerDetails(deliveryDocument4mDB, caseContainerTrackingId);
                eachesParentContainerDetails.setParentTrackingId(
                        instruction4mDB.getContainer().getTrackingId());

                Container eachesParentContainer =
                        null;
                try {
                    eachesParentContainer = containerService.constructContainer(
                            instruction4mDB,
                            eachesParentContainerDetails,
                            Boolean.TRUE,
                            Boolean.TRUE,
                            instructionUpdateRequestFromClient);
                } catch (ReceivingException e) {
                    throw new RuntimeException(e);
                }
                eachesParentContainer.setParentTrackingId(instruction4mDB.getContainer().getTrackingId());
                eachesParentContainer.setLastChangedTs(new Date());
                eachesParentContainer.setLastChangedUser(userId);

                String instructionCode = instruction4mDB.getInstructionCode();
                if (instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_CNTR_CASE_SCAN) || instructionCode.equalsIgnoreCase(ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT)){
                    if (!(instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.CASE_RECEIVED_WITH_UNIT_SCANS)
                    || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS))){
                        eachesParentContainer.setRcvgContainerType(RxConstants.ReceivingContainerTypes.CASE);
                    } else {
                        eachesParentContainer.setRcvgContainerType(RxConstants.ReceivingContainerTypes.PARTIAL_CASE);
                    }

                    populateContainerMiscInfoV2(
                            eachesParentContainer, instruction4mDB, instructionPack);
                } else{
                    if (instruction4mDB.getInstructionCode().equalsIgnoreCase(ReportingConstants.RX_SER_BUILD_UNIT_SCAN)){
                        eachesParentContainer.setRcvgContainerType(RxConstants.ReceivingContainerTypes.PARTIAL_CASE);
                    }

                    populateContainerMiscInfoV2(
                            eachesParentContainer, instruction4mDB, null);

                    Map<String, Object> containerMiscInfo =
                            eachesParentContainer.getContainerMiscInfo();
                    containerMiscInfo.put(RxConstants.GDM_CONTAINER_ID,instructionPack.getParentId());
                    eachesParentContainer.setContainerMiscInfo(containerMiscInfo);
                }

                containers.add(eachesParentContainer);

                ContainerItem eachesParentContainerItem =
                        containerItemBuilder.build(
                                caseContainerTrackingId,
                                instruction4mDB,
                                instructionUpdateRequestFromClient,
                                scannedDataMap);
                eachesParentContainerItem.setRotateDate(parseAndGetRotateDate());
                eachesParentContainerItem.setQuantity(quantityToBeReceivedInEaches);
                eachesParentContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

                SsccScanResponse.Container scannedCaseContainer = new SsccScanResponse.Container();

                scannedCaseContainer.setLotNumber(instructionPack.getLotNumber());
                scannedCaseContainer.setGtin(instructionPack.getGtin());
                scannedCaseContainer.setExpiryDate(instructionPack.getExpiryDate());
                scannedCaseContainer.setSerial(instructionPack.getSerial());

                if ((!StringUtils.isAnyBlank(
                        scannedCaseContainer.getExpiryDate(), scannedCaseContainer.getGtin(), scannedCaseContainer.getLotNumber(), scannedCaseContainer.getSerial()
                ))) {
                    eachesParentContainerItem.setGtin(
                            scannedCaseContainer.getGtin());
                    eachesParentContainerItem.setLotNumber(
                            scannedCaseContainer.getLotNumber());
                    eachesParentContainerItem.setSerial(
                            scannedCaseContainer.getSerial());
                    try {
                        eachesParentContainerItem.setExpiryDate(
                                DateUtils.parseDate(
                                        scannedCaseContainer.getExpiryDate(),
                                        ReceivingConstants.SIMPLE_DATE));
                    } catch (ParseException e) {
                        throw new ReceivingBadDataException(
                                ExceptionCodes.INVALID_SCANNED_DATA_EXPIRY_DATE,
                                RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
                    }
                }

                containerItems.add(eachesParentContainerItem);

                caseContainerTrackingId = eachesParentContainerDetails.getTrackingId();
                instruction4mDB
                        .getContainer()
                        .getContents()
                        .get(0)
                        .setQty(containerItems.get(0).getQuantity());
            } else {
                containerItems.add(
                        updateContainerItemQuantity(
                                parentTrackingId, instruction4mDB.getContainer().getContents().get(0).getQty()));
                instruction4mDB
                        .getContainer()
                        .getContents()
                        .get(0)
                        .setQty(containerItems.get(0).getQuantity());

            }
        }

        return caseContainerTrackingId;
    }

    private ContainerItem updateContainerItemQuantity(
            String parentTrackingId, Integer receivedEachQty) {

        ContainerItem parentContainerItem = null;
        List<ContainerItem> containerItems = containerItemService.findByTrackingId(parentTrackingId);
        if (!CollectionUtils.isEmpty(containerItems) && receivedEachQty > 0) {
            parentContainerItem = containerItems.get(0);
            parentContainerItem.setQuantity(receivedEachQty);
        }
        return parentContainerItem;
    }


    public ContainerItem createScannedContainer(List<Container> containers, List<ContainerItem> containerItems,
                                          Instruction instruction4mDB, DeliveryDocument deliveryDocument4mDB,
                                          UpdateInstructionRequest instructionUpdateRequestFromClient,
                                          String userId, DocumentLine documentLine, Map<String, ScannedData> scannedDataMap,
                                          Integer quantityToBeReceivedInEaches, String caseContainerTrackingId, String scannedCaseTrackingId,
                                          List<ContainerDetails> instructionChildContainers4mDB, SsccScanResponse.Container  gdmResponseForScannedData){

        Content content = InstructionUtils.createContent(deliveryDocument4mDB);
        content.setGtin(scannedDataMap.get(ReceivingConstants.KEY_GTIN).getValue());
        content.setLot(scannedDataMap.get(ReceivingConstants.KEY_LOT).getValue());
        content.setSerial(scannedDataMap.get(ReceivingConstants.KEY_SERIAL).getValue());
        content.setRotateDate(DateFormatUtils.format(parseAndGetRotateDate(), ReceivingConstants.SIMPLE_DATE));

        content.setQty(quantityToBeReceivedInEaches);
        content.setQtyUom(ReceivingConstants.Uom.EACHES);

        ContainerDetails scannedContainerDetails = new ContainerDetails();
        scannedContainerDetails.setTrackingId(scannedCaseTrackingId);
        scannedContainerDetails.setContents(Arrays.asList(content));

        String scannedCaseParentTrackingId =
                StringUtils.isNotEmpty(caseContainerTrackingId)
                        ? caseContainerTrackingId
                        : instruction4mDB.getContainer().getTrackingId();
        scannedContainerDetails.setParentTrackingId(scannedCaseParentTrackingId);

        instructionChildContainers4mDB.add(scannedContainerDetails);

        Container scannedContainer = null;
        try {
            scannedContainer = containerService.constructContainer(
                    instruction4mDB,
                    scannedContainerDetails,
                    Boolean.TRUE,
                    Boolean.TRUE,
                    instructionUpdateRequestFromClient);
        } catch (ReceivingException e) {
            throw new RuntimeException(e);
        }
        scannedContainer.setParentTrackingId(scannedCaseParentTrackingId);
        scannedContainer.setLastChangedTs(new Date());
        scannedContainer.setLastChangedUser(userId);
        scannedContainer.setSsccNumber(
                ofNullable(scannedDataMap.get(ApplicationIdentifier.SSCC.getKey()))
                        .map(s -> s.getApplicationIdentifier() + s.getValue())
                        .orElse(null));
        Map<String, Object> containerMiscInfo = new HashMap<>();
        containerMiscInfo.put(
                RxConstants.SHIPMENT_DOCUMENT_ID, gdmResponseForScannedData.getShipmentDocumentId());
        containerMiscInfo.put(RxConstants.INSTRUCTION_CODE, instruction4mDB.getInstructionCode());
        containerMiscInfo.put(ReceivingConstants.IS_AUDITED, true);
        scannedContainer.setAudited(true);
        scannedContainer.setContainerMiscInfo(containerMiscInfo);
        populateContainerMiscInfoV2(
                scannedContainer, instruction4mDB, gdmResponseForScannedData);

        if(!Objects.isNull(instruction4mDB.getContainer())
                && !ReceivingConstants.Uom.EACHES.equalsIgnoreCase(documentLine.getQuantityUOM())
                && Objects.isNull(scannedContainer.getRcvgContainerType())
                && !(instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.FULL_PALLET)
                    || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_FULL_PALLET)
                    || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.HNDL_AS_CSPK_FULL_PALLET)
                    || instruction4mDB.getReceivingMethod().equalsIgnoreCase(RxConstants.ReceivingTypes.PROBLEM_HNDL_AS_CSPK_FULL_PALLET))){
            scannedContainer.setRcvgContainerType(RxConstants.ReceivingContainerTypes.CASE);
        }

        containers.add(scannedContainer);

        ContainerItem scannedContainerItem =
                containerItemBuilder.build(
                        scannedCaseTrackingId,
                        instruction4mDB,
                        instructionUpdateRequestFromClient,
                        scannedDataMap);
        scannedContainerItem.setQuantity(quantityToBeReceivedInEaches);
        scannedContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
        containerItems.add(scannedContainerItem);

        return scannedContainerItem;
    }

    public void validateRequest(Instruction instructionUpdateRequestFromClient) throws ReceivingException {
        instructionStateValidator.validate(instructionUpdateRequestFromClient);
    }

    public void validateScannedContainer(UpdateInstructionRequest instructionUpdateRequestFromClient, Instruction instruction) throws ReceivingException {

        List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
        RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
        DeliveryDocument deliveryDocument4mDB =
                gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        String gtin = scannedDataMap.get(ReceivingConstants.KEY_GTIN).getValue();
        String serial = scannedDataMap.get(ReceivingConstants.KEY_SERIAL).getValue();

        updateInstructionDataValidator.validateContainerDoesNotAlreadyExist(
                deliveryDocument4mDB.getPoTypeCode(),
                gtin,
                serial,
                TenantContext.getFacilityNum(),
                TenantContext.getFacilityCountryCode()
        );
    }

    public SsccScanResponse.Container getCurrentContainer(List<DeliveryDocument> deliveryDocuments){
        if (deliveryDocuments == null || deliveryDocuments.isEmpty()) {
            return null;
        }
        DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
        if (deliveryDocument == null) {
            return null;
        }
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail = deliveryDocument.getGdmCurrentNodeDetail();
        if (gdmCurrentNodeDetail == null || gdmCurrentNodeDetail.getContainers() == null || gdmCurrentNodeDetail.getContainers().isEmpty()) {
            return null;
        }

        return gdmCurrentNodeDetail.getContainers().get(0);
    }

    public DataHolder getDataForUpdateInstruction(
            Long instructionId,
            UpdateInstructionRequest instructionUpdateRequestFromClient,
            String parentTrackingId) throws ReceivingException {

        Instruction instruction4mDB = instructionPersisterService.getInstructionById(instructionId);
        DocumentLine documentLine =
                instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
        DeliveryDocument deliveryDocument = getDeliveryDocFromInstruction(instruction4mDB);
        List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
        deliveryDocuments.add(deliveryDocument);

        DataHolder dataHolder = DataHolder.builder()
                .deliveryDocument(deliveryDocument)
                .documentLine(documentLine)
                .receivingFlow(instruction4mDB.getReceivingMethod())
                .instruction(instruction4mDB).build();
        if (CollectionUtils.isEmpty(
                instructionUpdateRequestFromClient.getUserEnteredDataList())){
            dataHolder.setContainer(getCurrentContainer(deliveryDocuments));
        }
        return dataHolder;
    }

    public boolean isEpcisSmartReceivingEnabled(Instruction instruction){

        ItemData additionalInfo = getAdditionalInfoFromInstruction(instruction);
        boolean isEpcisSmartReceivingEnabled = false;
        if (additionalInfo!= null) {
            isEpcisSmartReceivingEnabled = additionalInfo.getIsEpcisSmartReceivingEnabled();
        }
        String instructionCode = instruction.getInstructionCode();
        List<String> validCodes = Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN,
                ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT,ReportingConstants.RX_SER_BUILD_UNIT_SCAN, ReportingConstants.RX_SER_MULTI_SKU_PALLET);
        return (validCodes.contains(instructionCode) && isEpcisSmartReceivingEnabled);
    }

    private ItemData getAdditionalInfoFromInstruction(Instruction instruction4mDB) {
        ItemData additionalInfo = null;
        if (null!= instruction4mDB.getDeliveryDocument()) {
            DeliveryDocument deliveryDocument = gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
            if (deliveryDocument.getDeliveryDocumentLines()!= null &&!deliveryDocument.getDeliveryDocumentLines().isEmpty()) {
                additionalInfo = deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo();
            }
        }
        return additionalInfo;
    }

    private DeliveryDocument getDeliveryDocFromInstruction(Instruction instruction4mDB) {
        if (null!= instruction4mDB.getDeliveryDocument()) {
            return gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
        }
        return null;
    }

    public void callGDMCurrentNodeApi(UpdateInstructionRequest instructionUpdateRequestFromClient, HttpHeaders httpHeaders , Instruction instruction, DataHolder dataForUpdateInstruction) {

        List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
        SsccScanResponse.Container containerScannedFromUI = convertToGDMContainer(scannedDataMap);

        ShipmentsContainersV2Request shipmentsContainersV2Request = new ShipmentsContainersV2Request();
        shipmentsContainersV2Request.setDeliveryNumber(instructionUpdateRequestFromClient.getDeliveryNumber().toString());
        shipmentsContainersV2Request.setSgtin(new Sgtin(containerScannedFromUI.getSerial(), containerScannedFromUI.getGtin()));

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("includeOnlyContainers", "true");

        SsccScanResponse.Container gdmResponseForScannedData = rxDeliveryServiceImpl.getCurrentNode(
                shipmentsContainersV2Request, httpHeaders, queryParams).getContainers().get(0);
        dataForUpdateInstruction.setGdmResponseForScannedData(gdmResponseForScannedData);
    }
}
