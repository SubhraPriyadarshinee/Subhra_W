package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.model.HawkeyeLpnPayload;
import com.walmart.move.nim.receiving.acc.model.TestSelectPoLineAndReceiveRequest;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.acc.service.*;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.functional.Optional;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("automated/test")
public class ACCTestController {

  @Resource(name = ReceivingConstants.ACC_NOTIFICATION_SERVICE)
  private ACLNotificationService aclNotificationService;

  @Autowired private UserLocationService userLocationService;

  @Autowired private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Autowired private LabelDataService labelDataService;

  @Autowired private DeliveryEventPersisterService deliveryEventPersisterService;

  @Autowired private DefaultItemCatalogService defaultItemCatalogService;

  @Resource(name = ACCConstants.ACC_FACILITY_MDM_SERVICE)
  private FacilityMDM facilityMDM;

  @Resource(name = ACCConstants.ACC_VERIFICATION_PROCESSOR)
  private ACLVerificationProcessor aclVerificationProcessor;

  @Resource private HawkeyeLpnSwapService hawkeyeLpnSwapService;

  @Resource(name = ReceivingConstants.ACC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private DockTagPersisterService dockTagPersisterService;

  @Autowired private ReceiptService receiptService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private DefaultReceiptSummaryProcessor defaultReceiptSummaryProcessor;

  @GetMapping(path = "/acl-logs/{locationId}")
  public List<NotificationLog> getAclLogs(@PathVariable String locationId) {
    return aclNotificationService.getAclNotificationLogsByLocation(
        locationId, PageRequest.of(0, 250, Sort.by("logTs").descending()));
  }

  @DeleteMapping(path = "/door/{locationId}")
  public void deleteByDoor(@PathVariable String locationId) {
    ReceivingUtils.validateApiAccessibility();
    aclNotificationService.deleteByLocation(locationId);
    userLocationService.deleteByLocation(locationId);
  }

  @DeleteMapping(path = "/delivery/{deliveryNumber}")
  public void deleteByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    dockTagPersisterService.deleteDockTagsForDelivery(deliveryNumber);
  }

  @GetMapping(path = "/dockTags/{deliveryNumber}")
  public List<DockTag> getDockTagsByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    return dockTagPersisterService.getDockTagsByDelivery(deliveryNumber);
  }

  @GetMapping(path = "/location-users/{userId:.+}")
  public List<UserLocation> getUsers(@PathVariable String userId) {
    return userLocationService.getByUser(userId);
  }

  /**
   * Generate store friendly pre labels based on delivery update message
   *
   * @param deliveryUpdateMessage delivery update message
   * @param httpHeaders http headers
   */
  @PostMapping(path = "/prelabel", consumes = "application/json")
  public void preLabel(
      @RequestBody DeliveryUpdateMessage deliveryUpdateMessage,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
  }

  @DeleteMapping(path = "/delivery-event/{deliveryNumber}")
  public void deleteDeliveryEventByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    deliveryEventPersisterService.deleteByDeliveryNumber(deliveryNumber);
  }

  @DeleteMapping(path = "/delivery-event/{deliveryNumber}/{eventType}")
  public void deleteDeliveryEventByDelivery(
      @PathVariable Long deliveryNumber, @PathVariable String eventType) {
    ReceivingUtils.validateApiAccessibility();
    deliveryEventPersisterService.deleteByDeliveryNumberAndEventType(deliveryNumber, eventType);
  }

  @GetMapping(path = "/delivery-event/{deliveryNumber}")
  public List<DeliveryEvent> getDeliveryEventsByDeliveryNumber(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    return deliveryEventPersisterService.getDeliveryEventsByDeliveryNumber(deliveryNumber);
  }

  @DeleteMapping(path = "/label-data/{deliveryNumber}")
  public void deleteLabelDataByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    labelDataService.deleteByDeliveryNumber(deliveryNumber);
  }

  @DeleteMapping(path = "/label-data/{deliveryNumber}/{po}/{pol}")
  public void deleteLabelDataByDelivery(
      @PathVariable Long deliveryNumber, @PathVariable String po, @PathVariable Integer pol) {
    ReceivingUtils.validateApiAccessibility();
    labelDataService.deleteByDeliveryNumberPoPoLine(deliveryNumber, po, pol);
  }

  @GetMapping(path = "/label-data/{deliveryNumber}/{po}/{pol}")
  public List<LabelData> findLabelDataByDeliveryPOPOL(
      @PathVariable Long deliveryNumber, @PathVariable String po, @PathVariable Integer pol) {
    ReceivingUtils.validateApiAccessibility();
    return labelDataService.findAllLabelDataByDeliveryPOPOL(deliveryNumber, po, pol);
  }

  @GetMapping(path = "/label-data/{deliveryNumber}")
  public List<LabelData> getLabelDataByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    return labelDataService.getLabelDataByDeliveryNumber(deliveryNumber);
  }

  /**
   * Receive a case by LPN that was pre-generated
   *
   * @param aclVerificationEventMessage verification scan message
   * @param httpHeaders http headers
   */
  @PostMapping(path = "/receiveByLpn", consumes = "application/json")
  public void receiveByLpn(
      @RequestBody ACLVerificationEventMessage aclVerificationEventMessage,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    aclVerificationProcessor.processEvent(aclVerificationEventMessage);
  }

  @PostMapping(path = "/swapLpn", consumes = "application/json")
  public void swapLPN(
      @RequestBody HawkeyeLpnPayload hawkeyeLpnPayload, @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    hawkeyeLpnSwapService.swapAndProcessLpn(hawkeyeLpnPayload);
  }

  @GetMapping(path = "/deliveries/{deliveryNumber}")
  public DeliveryMetaData getDeliveryMetaDataByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    return deliveryMetaDataService.findByDeliveryNumber(deliveryNumber.toString()).orElse(null);
  }

  @DeleteMapping(path = "/deliveries/{deliveryNumber}")
  public void deleteDeliveryMetaDataByDelivery(@PathVariable Long deliveryNumber) {
    ReceivingUtils.validateApiAccessibility();
    deliveryMetaDataService.deleteByDeliveryNumber(deliveryNumber.toString());
  }

  /**
   * This API is used to delete item catalog update logs based on delivery number which are created
   * for integration test
   *
   * @param deliveryNumber
   * @throws com.walmart.move.nim.receiving.core.common.ReceivingException
   */
  @DeleteMapping(path = "/itemCatalog/{deliveryNumber}")
  public void deleteItemCatalogUpdateLogsByDeliveryNumber(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber) throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    defaultItemCatalogService.deleteItemCatalogUpdatelogs(deliveryNumber);
  }

  /**
   * This API is used to get item catalog update logs based on delivery number which are created for
   * integration testitemCatalog
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @GetMapping(path = "/itemCatalog/{deliveryNumber}")
  public List<ItemCatalogUpdateLog> getItemCatalogUpdateLogsByDeliveryNumber(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber) throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    return defaultItemCatalogService.getItemCatalogUpdatelogs(deliveryNumber);
  }

  /**
   * Get store to DC alignment
   *
   * @param storeList list of stores
   * @param httpHeaders headers
   * @return return a map of store and DC mapping
   * @throws ReceivingException
   */
  @PostMapping(path = "/store/dc/alignment")
  public Map<String, Integer> getStoreToDCAlignment(
      @RequestBody List<String> storeList, @RequestHeader HttpHeaders httpHeaders) {
    ReceivingUtils.validateApiAccessibility();
    return facilityMDM.getStoreToDCMapping(storeList, httpHeaders);
  }

  @PostMapping(path = "/delivery/autoComplete")
  public void autoCompleteDelivery(@RequestHeader HttpHeaders httpHeaders) {
    Integer facilityNumber = TenantContext.getFacilityNum();
    try {
      log.info("autoCompleteAPI: Auto-complete delivery started for facility {}.", facilityNumber);
      tenantSpecificConfigReader
          .getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.COMPLETE_DELIVERY_PROCESSOR,
              CompleteDeliveryProcessor.class)
          .autoCompleteDeliveries(facilityNumber);
      log.info(
          "autoCompleteAPI: Auto-complete delivery successfully done for facility {}.",
          facilityNumber);
    } catch (Exception ex) {
      log.error(
          "autoCompleteAPI:  Failed for facility number {} with exception {}",
          facilityNumber,
          ExceptionUtils.getStackTrace(ex));
    }
  }

  @PostMapping(path = "/delivery-link")
  public ResponseEntity<?> updateDeliveryLink(
      @RequestBody List<DeliveryAndLocationMessage> deliveryAndLocationMessages,
      @RequestHeader HttpHeaders httpHeaders) {
    tenantSpecificConfigReader
        .getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_LINK_SERVICE,
            DeliveryLinkService.class)
        .updateDeliveryLink(deliveryAndLocationMessages, httpHeaders);
    return ResponseEntity.ok().build();
  }

  @PostMapping(path = "/imports/selectPoLineAndReceive/")
  public ResponseEntity<Map<String, Object>> selectPoLineAndPersistReceipts(
      @RequestBody TestSelectPoLineAndReceiveRequest testSelectPoLineAndReceiveRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    // Result Map
    Map<String, Object> results = new TreeMap<>();

    // fetch po lines
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            testSelectPoLineAndReceiveRequest.getDeliveryNumber(),
            testSelectPoLineAndReceiveRequest.getGtin(),
            httpHeaders);

    // select po lines
    Pair<DeliveryDocument, Long> selectedDeliveryDocumentPair =
        instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(deliveryDocuments, 1);

    Optional<Pair<DeliveryDocument, Long>> selectedPOAndQtyOptional =
        Optional.ofNullable(selectedDeliveryDocumentPair);
    if (selectedPOAndQtyOptional.isPresent()) {
      DeliveryDocument selectedPO = selectedPOAndQtyOptional.get().getKey();
      Long receivedQty = selectedPOAndQtyOptional.get().getValue();
      if (Objects.isNull(selectedPO)) {
        results.put("result", "NO PO/PO Line Selected");
        return new ResponseEntity<>(results, HttpStatus.INTERNAL_SERVER_ERROR);
      }
      DeliveryDocumentLine selectedPOLine = selectedPO.getDeliveryDocumentLines().get(0);
      results.put("_selectedPO", selectedPO.getPurchaseReferenceNumber());
      results.put("_selectedPOLine", selectedPOLine.getPurchaseReferenceLineNumber());
      results.put("_alreadyReceivedQty", receivedQty);

      if (testSelectPoLineAndReceiveRequest.isCreateReceiptsEnabled()) {
        // create receipts, and persist
        Receipt receipt1 = new Receipt();
        receipt1.setDeliveryNumber(selectedPO.getDeliveryNumber());
        receipt1.setDoorNumber(testSelectPoLineAndReceiveRequest.getDoorNumber());
        receipt1.setPurchaseReferenceNumber(selectedPO.getPurchaseReferenceNumber());
        receipt1.setPurchaseReferenceLineNumber(selectedPOLine.getPurchaseReferenceLineNumber());
        receipt1.setQuantity(1);
        receipt1.setQuantityUom(ReceivingConstants.Uom.VNPK);
        receipt1.setVnpkQty(selectedPOLine.getVendorPack());
        receipt1.setWhpkQty(selectedPOLine.getWarehousePack());
        receipt1.setEachQty(
            ReceivingUtils.conversionToEaches(
                1,
                ReceivingConstants.Uom.VNPK,
                selectedPOLine.getVendorPack(),
                selectedPOLine.getWarehousePack()));
        receipt1.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        receipt1.setProblemId(null);
        receiptService.saveReceipt(receipt1);
      }

      // add receipts summary (total quantity by po and pol for delivery)
      if (testSelectPoLineAndReceiveRequest.isShowReceiptsAfterReceiving()) {
        List<ReceiptSummaryResponse> receiptSummaryResponseList =
            defaultReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(
                testSelectPoLineAndReceiveRequest.getDeliveryNumber());
        Map<String, Long> receivedQtyMap =
            receiptSummaryResponseList
                .stream()
                .collect(
                    Collectors.toMap(
                        receiptSummaryResponse ->
                            receiptSummaryResponse.getPurchaseReferenceNumber()
                                + ReceivingConstants.DELIM_DASH
                                + receiptSummaryResponse.getPurchaseReferenceLineNumber(),
                        ReceiptSummaryResponse::getReceivedQty));
        results.put("receiptSummary", receivedQtyMap);
      }
      return new ResponseEntity<>(results, HttpStatus.CREATED);
    }

    results.put("result", "NO PO Line Selected");
    return new ResponseEntity<>(results, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
