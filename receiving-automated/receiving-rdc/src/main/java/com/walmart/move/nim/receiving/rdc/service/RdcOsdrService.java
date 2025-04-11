package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getOsdrDefaultSummaryResponse;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.osdr.CutOverType;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.AuditLogPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang3.exception.ExceptionUtils;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RdcOsdrService extends OsdrService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcOsdrService.class);

  @Autowired private NgrRestApiClient ngrClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private ReceiptCustomRepository receiptCustomRepository;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private AuditLogPersisterService auditLogPersisterService;

  public OsdrSummary getOsdrSummary(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    OsdrSummary osdrSummaryResponse;
    List<ReceiptSummaryVnpkResponse> atlasReceiptsSummaryList;
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_NGR_SERVICES_DISABLED, false)) {
      osdrSummaryResponse = ngrClient.getDeliveryReceipts(deliveryNumber, headers);
    } else {
      osdrSummaryResponse = getOsdrDefaultSummaryResponse(deliveryNumber);
    }
    OpenDockTagCount openDockTagCount = fetchOpenDocktags(deliveryNumber);
    osdrSummaryResponse.setOpenDockTags(openDockTagCount);
    // Update Audit details
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_DSDC_AUDIT_ENABLED,
        false)) {
      // if there's no Audit pending from Legacy RDS
      if (Objects.nonNull(osdrSummaryResponse.getAuditPending())
          && !osdrSummaryResponse.getAuditPending()) {
        PendingAuditTags pendingAuditTags = getCountOfAuditTagsByDeliveryNumber(deliveryNumber);
        osdrSummaryResponse.setPendingAuditTags(pendingAuditTags);
        osdrSummaryResponse.setAuditPending(pendingAuditTags.getCount() > 0);
      }
    }

    // combine receipts from RDS and Atlas
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false)) {
      if (CollectionUtils.isNotEmpty(osdrSummaryResponse.getSummary())) {
        // setting atlasConvertedItem to false for all summary retrieved from RDS
        osdrSummaryResponse
            .getSummary()
            .stream()
            .parallel()
            .forEach(
                summary -> {
                  summary
                      .getLines()
                      .forEach(
                          osdrPoLine -> {
                            osdrPoLine.setAtlasConvertedItem(false);
                          });
                });
      }

      atlasReceiptsSummaryList = receiptCustomRepository.receiptSummaryByDelivery(deliveryNumber);

      if (CollectionUtils.isNotEmpty(atlasReceiptsSummaryList)) {
        LOGGER.info("Receipts found in Atlas for delivery number {}", deliveryNumber);
        combineReceiptsSummary(osdrSummaryResponse, atlasReceiptsSummaryList);
        calculatePutawayQtyByContainer(osdrSummaryResponse, deliveryNumber);
        populateAtlasDsdcReceivedPacksCount(osdrSummaryResponse, deliveryNumber);
      }
      osdrSummaryResponse.getSummary().forEach(this::setCutOverTypes);
    }
    return osdrSummaryResponse;
  }

  /**
   * This method fetch putaway complete qty from container table for given delivery number &
   * combines the OSDR summary response (which is calculated earlier by RDS & Atlas Receipts table)
   * to update the putaway qty (orderFilledQty) for each Po/PoLine number
   *
   * @param osdrSummaryResponse
   * @param deliveryNumber
   */
  private void calculatePutawayQtyByContainer(
      OsdrSummary osdrSummaryResponse, Long deliveryNumber) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
        false)) {
      TenantContext.get().setFetchPutawayQtyByContainerDBStart(System.currentTimeMillis());
      List<ReceipPutawayQtySummaryByContainer> atlasReceiptPutawayQtySummaryResponse =
          containerPersisterService.getReceiptPutawayQtySummaryByDeliveryNumber(deliveryNumber);
      TenantContext.get().setFetchPutawayQtyByContainerDBEnd(System.currentTimeMillis());
      LOGGER.warn(
          "LatencyCheckDB: fetchPutawayQtyByContainer at ts={} time in totalTimeTakenToFetchPutawayQtyFromContainerDB={} for deliveryNumber={}, correlationId={}",
          TenantContext.get().getFetchPutawayQtyByContainerDBStart(),
          ReceivingUtils.getTimeDifferenceInMillis(
              TenantContext.get().getFetchPutawayQtyByContainerDBStart(),
              TenantContext.get().getFetchPutawayQtyByContainerDBEnd()),
          deliveryNumber,
          TenantContext.getCorrelationId());
      if (CollectionUtils.isNotEmpty(atlasReceiptPutawayQtySummaryResponse)) {
        combineReceiptsPutawayQtySummary(
            osdrSummaryResponse, atlasReceiptPutawayQtySummaryResponse);
      }
    }
  }

  public PendingAuditTags getCountOfAuditTagsByDeliveryNumber(Long deliveryNumber) {
    int count =
        auditLogPersisterService.getAuditTagCountByDeliveryNumberAndAuditStatus(
            deliveryNumber, AuditStatus.PENDING);
    return PendingAuditTags.builder().count(count).build();
  }

  @Override
  public OsdrSummary getOsdrDetails(
      Long deliveryNumber, List<Receipt> receipts, String uom, String userId) {
    try {
      return getOsdrSummary(deliveryNumber, ReceivingUtils.getHeaders());
    } catch (ReceivingException e) {
      LOGGER.error(
          "Unable to get OSDRSummary for delivery:{} from NGR with error:{}",
          deliveryNumber,
          ExceptionUtils.getStackTrace(e));
    }
    return null;
  }

  private OpenDockTagCount fetchOpenDocktags(Long deliveryNumber) {
    Integer countOfDockTags =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .countOfOpenDockTags(deliveryNumber);
    Integer count = Objects.nonNull(countOfDockTags) ? countOfDockTags : 0;
    return OpenDockTagCount.builder().count(count).build();
  }

  @Override
  protected OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  private boolean isPoAlreadyReceivedInRDS(
      OsdrSummary osdrSummaryResponse, ReceiptSummaryVnpkResponse receipt) {
    Optional<OsdrPo> isPoAlreadyExists =
        osdrSummaryResponse
            .getSummary()
            .stream()
            .parallel()
            .filter(
                summary ->
                    summary
                        .getPurchaseReferenceNumber()
                        .equals(receipt.getPurchaseReferenceNumber()))
            .findAny();
    return isPoAlreadyExists.isPresent();
  }

  private OsdrPoLine populateOsdrPoLine(
      ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse,
      boolean calculatePutawayQtyByContainer) {
    OsdrPoLine osdrPoLine = new OsdrPoLine();
    int orderFilledQty =
        calculatePutawayQtyByContainer
            ? 0
            : receiptSummaryVnpkResponse.getOrderFilledQty().intValue();
    osdrPoLine.setRcvdQty(receiptSummaryVnpkResponse.getReceivedQty().intValue());
    osdrPoLine.setOrderFilledQty(orderFilledQty);
    osdrPoLine.setLineNumber(
        Long.valueOf(receiptSummaryVnpkResponse.getPurchaseReferenceLineNumber()));
    osdrPoLine.setAtlasConvertedItem(true);
    if (receiptSummaryVnpkResponse.getQtyUOM().equals(ReceivingConstants.Uom.WHPK)) {
      osdrPoLine.setLessThanCaseRcvd(true);
      osdrPoLine.setRcvdQtyUom(ReceivingConstants.Uom.VNPK);
    } else {
      osdrPoLine.setRcvdQtyUom(receiptSummaryVnpkResponse.getQtyUOM());
    }
    return osdrPoLine;
  }

  private OsdrPo populateOsdrPo(
      ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse,
      boolean calculatePutawayQtyByContainer) {
    OsdrPo osdrPo = new OsdrPo();
    List<OsdrPoLine> poLines = new ArrayList<>();
    int orderFilledQty =
        calculatePutawayQtyByContainer
            ? 0
            : receiptSummaryVnpkResponse.getOrderFilledQty().intValue();
    osdrPo.setPurchaseReferenceNumber(receiptSummaryVnpkResponse.getPurchaseReferenceNumber());
    osdrPo.setRcvdQty(receiptSummaryVnpkResponse.getReceivedQty().intValue());
    osdrPo.setOrderFilledQty(orderFilledQty);
    osdrPo.setRcvdPackCount(receiptSummaryVnpkResponse.getRcvdPackCount());
    if (receiptSummaryVnpkResponse.getQtyUOM().equals(ReceivingConstants.Uom.WHPK)) {
      osdrPo.setLessThanCaseRcvd(true);
      osdrPo.setRcvdQtyUom(ReceivingConstants.Uom.VNPK);
    } else {
      osdrPo.setRcvdQtyUom(receiptSummaryVnpkResponse.getQtyUOM());
    }
    poLines.add(populateOsdrPoLine(receiptSummaryVnpkResponse, calculatePutawayQtyByContainer));
    osdrPo.setLines(poLines);
    return osdrPo;
  }

  /**
   * This method combines delivery receipts from both RDS and Atlas as atlas converted items
   * received only in atlas. When the same poNumber received in RDS and Atlas it aggregates the
   * received qty for all po lines for the same poNumber from both the system.If any poLine is not
   * available in either of the system it will add as a new poLine to the poNumber & updates the
   * receipts in the osdr summary.
   *
   * @param osdrSummaryResponse
   * @param receiptSummaryVnpkResponseList
   */
  private void combineReceiptsSummary(
      OsdrSummary osdrSummaryResponse,
      List<ReceiptSummaryVnpkResponse> receiptSummaryVnpkResponseList) {
    boolean calculatePutawayQtyByContainer =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false);
    for (ReceiptSummaryVnpkResponse receipt : receiptSummaryVnpkResponseList) {
      boolean isPoAlreadyExists = isPoAlreadyReceivedInRDS(osdrSummaryResponse, receipt);
      if (isPoAlreadyExists) {
        List<OsdrPo> updatedReceiptsSummary =
            osdrSummaryResponse
                .getSummary()
                .stream()
                .parallel()
                .map(
                    summary -> {
                      if (summary
                          .getPurchaseReferenceNumber()
                          .equals(receipt.getPurchaseReferenceNumber())) {
                        Integer totalReceivedQtyByPo =
                            summary.getRcvdQty() + receipt.getReceivedQty().intValue();
                        summary.setRcvdQty(totalReceivedQtyByPo);
                        summary.setOrderFilledQty(
                            getTotalOrderFilledQty(
                                receipt, summary, calculatePutawayQtyByContainer));
                        summary
                            .getLines()
                            .add(populateOsdrPoLine(receipt, calculatePutawayQtyByContainer));
                        if (receipt.getQtyUOM().equals(ReceivingConstants.Uom.WHPK)) {
                          summary.setRcvdQtyUom(ReceivingConstants.Uom.VNPK);
                          summary.setLessThanCaseRcvd(true);
                        }
                      }
                      return summary;
                    })
                .collect(Collectors.toList());
        osdrSummaryResponse.setSummary(updatedReceiptsSummary);
      } else {
        // add po to the receipt summary when it doesn't exist in RDS
        List<OsdrPo> osdrPoList = new ArrayList<>();
        OsdrPo osdrPo = populateOsdrPo(receipt, calculatePutawayQtyByContainer);
        if (CollectionUtils.isEmpty(osdrSummaryResponse.getSummary())) {
          osdrPoList.add(osdrPo);
          osdrSummaryResponse.setSummary(osdrPoList);
        } else {
          osdrSummaryResponse.getSummary().add(osdrPo);
        }
      }
    }
  }

  private void combineReceiptsPutawayQtySummary(
      OsdrSummary osdrSummaryResponse,
      List<ReceipPutawayQtySummaryByContainer> receipPutawayQtySummaryByContainers) {
    for (ReceipPutawayQtySummaryByContainer receiptSummaryByContainer :
        receipPutawayQtySummaryByContainers) {
      List<OsdrPo> updatedOrderFilledQtySummary =
          osdrSummaryResponse
              .getSummary()
              .stream()
              .parallel()
              .map(
                  summary -> {
                    if (summary
                        .getPurchaseReferenceNumber()
                        .equals(receiptSummaryByContainer.getPurchaseReferenceNumber())) {
                      int orderFilledQtyByPo =
                          summary.getOrderFilledQty()
                              + receiptSummaryByContainer.getPutawayQty().intValue();
                      summary.setOrderFilledQty(orderFilledQtyByPo);
                      summary.setLines(
                          populatePutawayQtyByPoLine(summary, receiptSummaryByContainer));
                    }
                    return summary;
                  })
              .collect(Collectors.toList());
      osdrSummaryResponse.setSummary(updatedOrderFilledQtySummary);
    }
  }

  /**
   * @param osdrPoSummary
   * @param receiptSummaryByContainer
   */
  private List<OsdrPoLine> populatePutawayQtyByPoLine(
      OsdrPo osdrPoSummary, ReceipPutawayQtySummaryByContainer receiptSummaryByContainer) {
    return osdrPoSummary
        .getLines()
        .stream()
        .map(
            osdrPoLine -> {
              if (osdrPoLine.getLineNumber().intValue()
                      == receiptSummaryByContainer.getPurchaseReferenceLineNumber()
                  && osdrPoLine.isAtlasConvertedItem()) {
                int osdrPoOrderFilledQty =
                    Objects.nonNull(osdrPoLine.getOrderFilledQty())
                        ? osdrPoLine.getOrderFilledQty()
                        : 0;
                int orderFilledQtyByPoLine =
                    osdrPoOrderFilledQty + receiptSummaryByContainer.getPutawayQty().intValue();
                osdrPoLine.setOrderFilledQty(orderFilledQtyByPoLine);
              }
              return osdrPoLine;
            })
        .collect(Collectors.toList());
  }

  private int getTotalOrderFilledQty(
      ReceiptSummaryVnpkResponse receipt,
      OsdrPo osdrRdsSummaryResponse,
      boolean calculatePutawayQtyByContainer) {
    int totalOrderFilledQtyByPo;
    if (calculatePutawayQtyByContainer) {
      totalOrderFilledQtyByPo =
          Objects.nonNull(osdrRdsSummaryResponse.getOrderFilledQty())
              ? osdrRdsSummaryResponse.getOrderFilledQty()
              : 0;
    } else {
      totalOrderFilledQtyByPo =
          osdrRdsSummaryResponse.getOrderFilledQty() + receipt.getOrderFilledQty().intValue();
    }
    return totalOrderFilledQtyByPo;
  }

  /**
   * set the cutOver type based on isAtlasConvertedItem in OsdrPoLine cutOverType - Atlas, if all of
   * the poLines received only in Atlas Or same poLine received in both Atlas RDS cutOverType -
   * Non-Atlas, if all of the poLines received only in RDS cutOverType - Mixed, if poLines received
   * in both Atlas & RDS
   */
  private void setCutOverTypes(OsdrPo osdrPo) {
    combinePoLineReceivedQty(osdrPo);
    long atlasConvertedLinesCount =
        osdrPo.getLines().stream().parallel().filter(OsdrPoLine::isAtlasConvertedItem).count();
    if (atlasConvertedLinesCount == (long) osdrPo.getLines().size()) {
      osdrPo.setCutOverType(CutOverType.ATLAS.getType());
    } else if (atlasConvertedLinesCount == 0L) {
      osdrPo.setCutOverType(CutOverType.NON_ATLAS.getType());
    } else {
      osdrPo.setCutOverType(CutOverType.MIXED.getType());
    }
  }

  private void combinePoLineReceivedQty(OsdrPo osdrPo) {
    Map<Long, OsdrPoLine> osdrPoLineMap = new HashMap<>();
    for (OsdrPoLine osdrPoLine : osdrPo.getLines()) {
      if (osdrPoLineMap.containsKey(osdrPoLine.getLineNumber())) {
        OsdrPoLine alreadyReceivedPoLine = osdrPoLineMap.get(osdrPoLine.getLineNumber());
        alreadyReceivedPoLine.setRcvdQty(
            alreadyReceivedPoLine.getRcvdQty() + osdrPoLine.getRcvdQty());
        int orderFilledQtyForPoLine =
            Objects.nonNull(alreadyReceivedPoLine.getOrderFilledQty())
                ? alreadyReceivedPoLine.getOrderFilledQty()
                : 0;
        alreadyReceivedPoLine.setOrderFilledQty(
            orderFilledQtyForPoLine + osdrPoLine.getOrderFilledQty());
        alreadyReceivedPoLine.setAtlasConvertedItem(true);
        if (osdrPoLine.isLessThanCaseRcvd() || alreadyReceivedPoLine.isLessThanCaseRcvd()) {
          alreadyReceivedPoLine.setLessThanCaseRcvd(true);
        }
      } else {
        osdrPoLineMap.put(osdrPoLine.getLineNumber(), osdrPoLine);
      }
    }
    osdrPo.setLines(new ArrayList<>(osdrPoLineMap.values()));
  }

  /**
   * This method aggregates received pack count for all POs in
   * ReceivedPackCountSummaryByDeliveryAndPo with the OsdrSummary for the same PO number.
   *
   * @param osdrSummaryResponse
   * @param deliveryNumber
   * @return
   */
  private void populateAtlasDsdcReceivedPacksCount(
      OsdrSummary osdrSummaryResponse, Long deliveryNumber) {
    boolean isDsdcSsccPackAvailableInGdm =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false);
    if (isDsdcSsccPackAvailableInGdm) {
      List<ReceiptSummaryVnpkResponse> atlasReceivedPackCountSummaryByDeliveryAndPo =
          receiptCustomRepository.getReceivedPackCountSummaryByDeliveryNumber(deliveryNumber);
      for (ReceiptSummaryVnpkResponse atlasReceivedPackCountByDeliveryAndPo :
          atlasReceivedPackCountSummaryByDeliveryAndPo) {
        List<OsdrPo> updatedReceivedPackCountSummary =
            osdrSummaryResponse
                .getSummary()
                .stream()
                .parallel()
                .map(
                    summary -> {
                      if (summary
                          .getPurchaseReferenceNumber()
                          .equals(
                              atlasReceivedPackCountByDeliveryAndPo.getPurchaseReferenceNumber())) {
                        if (Objects.isNull(summary.getRcvdPackCount())) {
                          summary.setRcvdPackCount(
                              atlasReceivedPackCountByDeliveryAndPo.getRcvdPackCount());
                        } else {
                          summary.setRcvdPackCount(
                              summary.getRcvdPackCount()
                                  + atlasReceivedPackCountByDeliveryAndPo.getRcvdPackCount());
                        }
                      }
                      return summary;
                    })
                .collect(Collectors.toList());
        updatedReceivedPackCountSummary.forEach(
            summary -> {
              if (Objects.isNull(summary.getRcvdPackCount())) {
                summary.setRcvdPackCount(0);
              }
            });
        osdrSummaryResponse.setSummary(updatedReceivedPackCountSummary);
      }
    }
  }
}
