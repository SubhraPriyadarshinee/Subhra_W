package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DocumentMeta;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PoLineDetails;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseReferenceLineMeta;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeliveryMetaService is for work with DeliveryMeta process logic around get/set/create and
 * transforms objects etc.
 *
 * @author vn50o7n
 */
@Service(ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
public class WitronDeliveryMetaDataService extends DeliveryMetaDataService {

  private final Logger log = LoggerFactory.getLogger(WitronDeliveryMetaDataService.class);
  @Autowired Gson gson;

  /**
   * checks DeliveryMetaData and returns if specific given override is ignored marked as Deprecated
   * to use isManagerOverrideV2
   *
   * @param deliveryNumber
   * @param poNumber
   * @param poLineNumber
   * @param override
   * @return isManagerOverride
   */
  @Deprecated
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public boolean isManagerOverride(
      String deliveryNumber, String poNumber, Integer poLineNumber, String override) {
    log.info(
        "get DeliveryMetaData for deliveryNumber ={} , poNumber ={} poLineNumber ={}, override ={}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        override);
    boolean isManagerOverride = false;
    final Optional<DeliveryMetaData> optionalDeliveryMeta = findByDeliveryNumber(deliveryNumber);
    if (optionalDeliveryMeta.isPresent()) {
      final DeliveryMetaData data = optionalDeliveryMeta.get();
      if (isNull(data)) return false;
      final PoLineDetails poLineDetails =
          gson.fromJson(data.getPoLineDetails(), PoLineDetails.class);
      if (isNull(poLineDetails)) return false;

      final List<DocumentMeta> documents = poLineDetails.getDocuments();
      if (isNull(documents)) return false;

      for (DocumentMeta document : documents) {
        if (poNumber.equals(document.getPurchaseReferenceNumber())
            && nonNull(document.getLines())) {
          for (PurchaseReferenceLineMeta line : document.getLines()) {
            if (APPROVED_HACCP.equals(override)
                && TRUE_STRING.equalsIgnoreCase(line.getApprovedHaccp())) {
              log.info(
                  "approved Haccp for deliveryNumber={} , poNumber={} poLineNumber={}",
                  deliveryNumber,
                  poNumber,
                  poLineNumber);
              return true;
            }

            if (poLineNumber.equals(line.getPurchaseReferenceLineNumber())) {
              if (IGNORE_EXPIRY.equals(override)) {
                if (TRUE_STRING.equalsIgnoreCase(line.getIgnoreExpiry())) {
                  isManagerOverride = true;
                }
              } else if (IGNORE_OVERAGE.equals(override)) {
                if (TRUE_STRING.equalsIgnoreCase(line.getIgnoreOverage())) {
                  isManagerOverride = true;
                }
              }
            }
          }
        }
      }
    }

    log.info(
        "override for deliveryNumber={} , poNumber={} poLineNumber={}, override ={}, isOverride={}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        override,
        isManagerOverride);

    return isManagerOverride;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public boolean isManagerOverrideV2(
      String deliveryNumber, String poNumber, Integer poLineNumber, String override) {
    log.info(
        "get DeliveryMetaData for deliveryNumber ={} , poNumber ={} poLineNumber ={}, override ={}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        override);
    final Optional<DeliveryMetaData> optionalDeliveryMeta = findByDeliveryNumber(deliveryNumber);
    if (optionalDeliveryMeta.isPresent()) {
      final DeliveryMetaData data = optionalDeliveryMeta.get();
      if (isNull(data)) return false;
      final PoLineDetails poLineDetails =
          gson.fromJson(data.getPoLineDetails(), PoLineDetails.class);
      if (isNull(poLineDetails)) return false;
      final List<DocumentMeta> documents = poLineDetails.getDocuments();
      if (isNull(documents)) return false;
      return documents
          .parallelStream()
          .filter(
              document ->
                  poNumber.equals(document.getPurchaseReferenceNumber())
                      && nonNull(document.getLines()))
          .flatMap(document -> document.getLines().stream())
          .anyMatch(
              line ->
                  isManagerOverrideApproved(
                      deliveryNumber, poNumber, poLineNumber, override, line));
    }
    return false;
  }

  private boolean isManagerOverrideApproved(
      String deliveryNumber,
      String poNumber,
      Integer poLineNumber,
      String override,
      PurchaseReferenceLineMeta line) {
    boolean isManagerOverride = false;
    switch (override) {
      case APPROVED_HACCP:
        if (TRUE_STRING.equalsIgnoreCase(line.getApprovedHaccp())) {
          isManagerOverride = true;
        }
        break;
      case IGNORE_EXPIRY:
        if (poLineNumber.equals(line.getPurchaseReferenceLineNumber())
            && TRUE_STRING.equalsIgnoreCase(line.getIgnoreExpiry())) {
          isManagerOverride = true;
        }
        break;
      case IGNORE_OVERAGE:
        if (poLineNumber.equals(line.getPurchaseReferenceLineNumber())
            && TRUE_STRING.equalsIgnoreCase(line.getIgnoreOverage())) {
          isManagerOverride = true;
        }
        break;
      default:
        log.error(
            "return false for unimplemented override for deliveryNumber={}, poNumber={}, poLineNumber={}, {}",
            deliveryNumber,
            poNumber,
            poLineNumber,
            override);
    }
    log.info(
        "override for deliveryNumber={}, poNumber={}, poLineNumber={}, {}={}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        override,
        isManagerOverride);

    return isManagerOverride;
  }

  public PurchaseReferenceLineMeta getPurchaseReferenceLineMeta(
      String deliveryNumber, String poNumber, int poLineNumber) {
    log.info(
        "PoLine Meta for deliveryNumber={} , poNumber={} poLineNumber={} is=",
        deliveryNumber,
        poNumber,
        poLineNumber);

    if (isNull(poNumber)) return null;

    final Optional<DeliveryMetaData> optionalDeliveryMeta = findByDeliveryNumber(deliveryNumber);
    if (!optionalDeliveryMeta.isPresent()) {
      return null;
    }
    final DeliveryMetaData data = optionalDeliveryMeta.get();
    if (isNull(data)) return null;
    final PoLineDetails poLineDetails = gson.fromJson(data.getPoLineDetails(), PoLineDetails.class);
    if (isNull(poLineDetails)) return null;
    final List<DocumentMeta> documents = poLineDetails.getDocuments();
    if (isNull(documents)) return null;

    // read matching po & poLine level DocumentMeta data
    final PurchaseReferenceLineMeta poLineMeta =
        documents
            .stream()
            .filter(
                poDoc ->
                    poNumber.equals(poDoc.getPurchaseReferenceNumber())
                        && nonNull(poDoc.getLines()))
            .flatMap(poDoc -> poDoc.getLines().stream())
            .filter(line -> nonNull(line) & poLineNumber == line.getPurchaseReferenceLineNumber())
            .findFirst()
            .orElse(null);

    log.info(
        "PoLine Meta for deliveryNumber={} , poNumber={} poLineNumber={} is={}",
        deliveryNumber,
        poNumber,
        poLineNumber,
        poLineMeta);
    return poLineMeta;
  }

  /**
   * overrides meta data for specific
   *
   * @param userId
   * @param deliveryNumber
   * @param poNumber
   * @param poLineNumber
   * @param ignore
   */
  public DeliveryMetaData doManagerOverride(
      String userId, String deliveryNumber, String poNumber, Integer poLineNumber, String ignore) {

    log.info(
        "saving DeliveryMeta override user={}, deliveryNumber={} , poNumber={} poLineNumber={}, ignore={}",
        userId,
        deliveryNumber,
        poNumber,
        poLineNumber,
        ignore);
    final Optional<DeliveryMetaData> oDeliveryMetaData = findByDeliveryNumber(deliveryNumber);
    DeliveryMetaData deliveryMetaData;
    if (oDeliveryMetaData.isPresent()) {
      deliveryMetaData =
          updateDeliveryMetaData(userId, poNumber, poLineNumber, ignore, oDeliveryMetaData.get());
    } else {
      deliveryMetaData =
          newDeliveryMetaData(userId, deliveryNumber, poNumber, poLineNumber, ignore);
    }
    save(deliveryMetaData);
    log.info(
        "Saved DeliveryMeta Override user={} deliveryNumber={} poNumber={} poLineNumber={} ignore={}",
        userId,
        deliveryNumber,
        poNumber,
        poLineNumber,
        ignore);
    return deliveryMetaData;
  }

  private DeliveryMetaData updateDeliveryMetaData(
      String userId,
      String poNumber,
      Integer poLineNumber,
      String ignoreKey,
      DeliveryMetaData data) {
    String poLineDetailsJson = data.getPoLineDetails();
    PoLineDetails poLineDetails = gson.fromJson(poLineDetailsJson, PoLineDetails.class);
    List<DocumentMeta> documents;
    if (Objects.isNull(poLineDetails)) {
      poLineDetails = new PoLineDetails();
      documents = new LinkedList<>();
    } else {
      documents = poLineDetails.getDocuments();
    }
    final boolean isUpdated = updateDocument(userId, poNumber, poLineNumber, ignoreKey, documents);
    if (!isUpdated) {
      documents.add(newDocumentMeta(userId, poNumber, poLineNumber, ignoreKey));
    }
    poLineDetails.setDocuments(documents);
    final String toJson = gson.toJson(poLineDetails, PoLineDetails.class);
    data.setPoLineDetails(toJson);
    return data;
  }

  private boolean updateDocument(
      String userId,
      String poNumber,
      Integer poLineNumber,
      String ignoreKey,
      List<DocumentMeta> documents) {
    for (DocumentMeta document : documents) {
      if (poNumber.equals(document.getPurchaseReferenceNumber())) {
        if (nonNull(document.getLines())) {
          List<PurchaseReferenceLineMeta> lines = document.getLines();
          for (PurchaseReferenceLineMeta line : lines) {
            if (poLineNumber.equals(line.getPurchaseReferenceLineNumber())) {
              updatePoLine(userId, ignoreKey, TRUE_STRING, line);
              return true;
            }
          }
          lines.add(newPoLine(userId, poLineNumber, ignoreKey, TRUE_STRING));
        } else {
          document.setLines(newLines(userId, poLineNumber, ignoreKey));
        }
        return true;
      }
    }
    return false;
  }

  private DeliveryMetaData newDeliveryMetaData(
      String userId,
      String deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String ignoreKey) {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber(deliveryNumber);
    PoLineDetails poLineDetails = new PoLineDetails();
    List<DocumentMeta> documents = new LinkedList<>();
    DocumentMeta document =
        newDocumentMeta(userId, purchaseReferenceNumber, purchaseReferenceLineNumber, ignoreKey);
    documents.add(document);
    poLineDetails.setDocuments(documents);
    deliveryMetaData.setPoLineDetails(gson.toJson(poLineDetails, PoLineDetails.class));
    return deliveryMetaData;
  }

  private DocumentMeta newDocumentMeta(
      String userId,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String ignoreKey) {
    DocumentMeta document = new DocumentMeta();
    document.setPurchaseReferenceNumber(purchaseReferenceNumber);
    List<PurchaseReferenceLineMeta> lines =
        newLines(userId, purchaseReferenceLineNumber, ignoreKey);
    document.setLines(lines);
    return document;
  }

  private List<PurchaseReferenceLineMeta> newLines(
      String userId, Integer purchaseReferenceLineNumber, String ignoreKey) {
    List<PurchaseReferenceLineMeta> lines = new LinkedList<>();
    PurchaseReferenceLineMeta line =
        newPoLine(userId, purchaseReferenceLineNumber, ignoreKey, TRUE_STRING);
    lines.add(line);
    return lines;
  }

  private PurchaseReferenceLineMeta newPoLine(
      String userId, Integer purchaseReferenceLineNumber, String ignoreKey, String ignoreValue) {
    PurchaseReferenceLineMeta line = new PurchaseReferenceLineMeta();
    line.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
    updatePoLine(userId, ignoreKey, ignoreValue, line);
    return line;
  }

  private void updatePoLine(
      String userId, String ignoreKey, String ignoreValue, PurchaseReferenceLineMeta line) {
    if (IGNORE_EXPIRY.equals(ignoreKey)) {
      line.setIgnoreExpiry(ignoreValue);
      line.setIgnoreExpiryBy(userId);
    } else if (IGNORE_OVERAGE.equals(ignoreKey)) {
      line.setIgnoreOverage(ignoreValue);
      line.setIgnoreOverageBy(userId);
    } else if (APPROVED_HACCP.equals(ignoreKey)) {
      line.setApprovedHaccp(ignoreValue);
      line.setApprovedHaccpBy(userId);
    }
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    log.info("not implemented method doing nothing");
  }

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    log.info("not implemented method doing nothing");
  }

  @Override
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    log.info("not implemented method returning null");
    return null;
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    log.info("not implemented method doing nothing");
    return false;
  }

  @Override
  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    log.info("not implemented method doing nothing");
    return 0;
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    log.info("not implemented method returning null");
    return null;
  }

  /**
   * create DELIVERY_METADATA
   *
   * @param delivery
   */
  public DeliveryMetaData createDeliveryMetaData(DeliveryWithOSDRResponse delivery) {
    List<DocumentMeta> documents = new ArrayList<>();
    for (PurchaseOrderWithOSDRResponse purchaseOrder : delivery.getPurchaseOrders()) {
      DocumentMeta document = new DocumentMeta();
      document.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
      document.setPoType(purchaseOrder.getLegacyType().toString());
      documents.add(document);
    }

    PoLineDetails poLineDetails = new PoLineDetails();
    poLineDetails.setDocuments(documents);

    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber(delivery.getDeliveryNumber().toString());
    deliveryMetaData.setPoLineDetails(gson.toJson(poLineDetails, PoLineDetails.class));
    deliveryMetaData.setLastUpdatedDate(new Date());

    log.info("Create metadata for DeliveryWithOSDR :{}", deliveryMetaData);
    return save(deliveryMetaData);
  }

  /**
   * update DELIVERY_METADATA
   *
   * @param deliveryMetaData
   * @param delivery_gdm
   */
  public DeliveryMetaData updateDeliveryMetaData(
      DeliveryMetaData deliveryMetaData, DeliveryWithOSDRResponse delivery_gdm) {

    PoLineDetails poLineDetails =
        gson.fromJson(deliveryMetaData.getPoLineDetails(), PoLineDetails.class);
    List<DocumentMeta> documents;
    if (nonNull(poLineDetails)) {
      documents = poLineDetails.getDocuments();
    } else {
      poLineDetails = new PoLineDetails();
      documents = new ArrayList<>();
    }
    List<PurchaseOrderWithOSDRResponse> newPurchaseOrders = new ArrayList<>();
    for (PurchaseOrderWithOSDRResponse purchaseOrder : delivery_gdm.getPurchaseOrders()) {
      if (!isMatchPO(documents, purchaseOrder)) {
        newPurchaseOrders.add(purchaseOrder);
      }
    }

    for (PurchaseOrderWithOSDRResponse newPurchaseOrder : newPurchaseOrders) {
      DocumentMeta document = new DocumentMeta();
      document.setPurchaseReferenceNumber(newPurchaseOrder.getPoNumber());
      document.setPoType(newPurchaseOrder.getLegacyType().toString());
      documents.add(document);
      log.info("Add new document for DeliveryWithOSDR :{}", document);
    }

    // save updated DELIVERY_METADATA
    poLineDetails.setDocuments(documents);
    deliveryMetaData.setPoLineDetails(gson.toJson(poLineDetails, PoLineDetails.class));

    deliveryMetaData.setLastUpdatedDate(new Date());
    return save(deliveryMetaData);
  }

  public boolean isMatchPO(
      List<DocumentMeta> documents, PurchaseOrderWithOSDRResponse purchaseOrder) {
    for (DocumentMeta document : documents) {
      if (document.getPurchaseReferenceNumber().equalsIgnoreCase(purchaseOrder.getPoNumber())) {
        document.setPoType(purchaseOrder.getLegacyType().toString());
        log.info("Update existing document for DeliveryWithOSDR :{}", document);
        return true;
      }
    }
    return false;
  }

  /**
   * find document details for a given delivery and PO
   *
   * @param deliveryNumber
   * @param purchaseOrder
   * @return DocumentMeta
   */
  public DocumentMeta findPurchaseOrderDetails(String deliveryNumber, String purchaseOrder) {
    Optional<DeliveryMetaData> deliveryMetaData = findByDeliveryNumber(deliveryNumber);
    if (deliveryMetaData.isPresent()) {
      PoLineDetails poLineDetails =
          gson.fromJson(deliveryMetaData.get().getPoLineDetails(), PoLineDetails.class);
      List<DocumentMeta> documents = poLineDetails.getDocuments();
      for (DocumentMeta document : documents) {
        if (purchaseOrder.equals(document.getPurchaseReferenceNumber())) {
          log.info("Found metada for given delivery :{} PO :{}", deliveryNumber, purchaseOrder);
          return document;
        }
      }
    }
    log.info("No metadata for given delivery :{} PO :{}", deliveryNumber, purchaseOrder);
    return null;
  }
}
