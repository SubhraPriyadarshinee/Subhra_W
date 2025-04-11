package com.walmart.move.nim.receiving.wfs.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.core.model.delivery.TCLFreeDeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.utils.WFSUtility;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class WFSTclFreeHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(WFSTclFreeHandler.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryServiceImpl;

  private Gson gsonForDate;

  @Autowired ReceiptCustomRepository receiptCustomRepository;
  @Autowired private TenantSpecificConfigReader configUtils;

  public WFSTclFreeHandler() {
    gsonForDate =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd"))
            .create();
  }

  public long getDeliveryNumberByTCLFree(
      InstructionRequest instructionRequest, long deliveryNumber, String poNumber, String facility)
      throws ReceivingException {
    if (deliveryNumber == 0) {
      LOGGER.info("TCL Free : Entering TCL flow to fetch Delivery Number");

      // Fetch Delivery Numbers in Working Condition
      List<String> deliveryStatusList =
          configUtils.getTenantConfigurationAsList(WFSConstants.TCL_FREE_ACCEPTABLE_DELIVERY_CODES);
      LOGGER.info(
          "List of acceptable delivery status from CCM: {}, correlation id: {}",
          deliveryStatusList,
          TenantContext.getCorrelationId());

      List<String> poNumberList = new ArrayList<>();
      if (Objects.nonNull(poNumber) && StringUtils.hasLength(poNumber)) {
        poNumberList.add(poNumber);
      }

      ConsolidatedDeliveryList listOfDeliveries = null;

      try {
        String response =
            deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
                deliveryStatusList, instructionRequest.getUpcNumber(), facility, 0, poNumberList);
        listOfDeliveries = gsonForDate.fromJson(response, ConsolidatedDeliveryList.class);
      } catch (ReceivingException e) {
        LOGGER.error("TCL Free : Error while calling GDM service get by status api");
        throw e;
      }

      // Check if only one delivery in accepted status
      if (listOfDeliveries != null && WFSUtility.isSingleDeliveryOnly(listOfDeliveries)) {
        deliveryNumber = listOfDeliveries.getData().get(0).getDeliveryNumber();
        LOGGER.error(
            "TCL Free : Only Single Delivery In Accepted Status Delivery: {}", deliveryNumber);
        return deliveryNumber;
      }
      if (!Objects.isNull(listOfDeliveries)) {
        for (ConsolidatedDelivery delivery : listOfDeliveries.getData()) {
          for (ConsolidatedPurchaseOrder purchaseOrder : delivery.getPurchaseOrders()) {
            for (ConsolidatedPurchaseOrderLine line : purchaseOrder.getLines()) {
              ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
                  receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(
                      delivery.getDeliveryNumber(),
                      purchaseOrder.getPoNumber(),
                      line.getPoLineNumber());
              if (line.getReceived() != null) {
                line.getReceived()
                    .setQuantity(
                        Math.toIntExact(receiptSummaryQtyByPoAndPoLineResponse.getReceivedQty()));
              }
            }
          }
        }
      }
      TCLFreeDeliveryDetails tclFreeDeliveryDetails =
          WFSUtility.findDeliveryForTCLFreeReceiving(listOfDeliveries.getData());
      if (!StringUtils.isEmpty(poNumber)) {
        List<ConsolidatedDelivery> filteredDeliveries = new ArrayList<>();
        for (ConsolidatedDelivery delivery : listOfDeliveries.getData()) {
          for (ConsolidatedPurchaseOrder purchaseOrder : delivery.getPurchaseOrders()) {
            if (purchaseOrder.getPoNumber().equals(poNumber)) {
              filteredDeliveries.add(delivery);
            }
          }
        }
        LOGGER.info(
            "List of deliveries after filtering for poNumber: {}: {}, correlation id: {}",
            poNumber,
            filteredDeliveries,
            TenantContext.getCorrelationId());
        if (CollectionUtils.isEmpty(filteredDeliveries)) {
          LOGGER.error(
              "TCL Free : Entered po number : {} is not valid for this UPC : {}",
              poNumber,
              instructionRequest.getUpcNumber());
          throw new ReceivingException(
              "TCL Free :Invalid PO, Not able to decide delivery",
              HttpStatus.NOT_FOUND,
              "TCL.404.NOT_FOUND");
        }
        if (!CollectionUtils.isEmpty(filteredDeliveries) && filteredDeliveries.size() == 1) {
          LOGGER.info(
              "TCL Free : Multiple Sellers, Selected Delivery based on PO Number : {}", poNumber);
          return filteredDeliveries.get(0).getDeliveryNumber();
        }
        listOfDeliveries.setData(filteredDeliveries);
      }
      WFSUtility.findDeliveryByPriorityAndScore(
          listOfDeliveries.getData(),
          tclFreeDeliveryDetails,
          instructionRequest.getUpcNumber(),
          deliveryStatusList);
      if (tclFreeDeliveryDetails != null && tclFreeDeliveryDetails.getDeliveryNumber() != null) {
        LOGGER.info(
                "TCL Free : Based on Priority and Score, for UPC : {} and poNumber: {}, Selected Delivery Number is : {}", instructionRequest.getUpcNumber(), poNumber, tclFreeDeliveryDetails.getDeliveryNumber()
        );
        deliveryNumber = tclFreeDeliveryDetails.getDeliveryNumber();
      }
    }
    return deliveryNumber;
  }

  long tclFreeReceive(
      InstructionRequest instructionRequest, long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      // Checking If TCL Free Flow Enabled
      String facility = httpHeaders.getFirst(ReceivingConstants.FACILITY_NUM);
      boolean isTCLFreeReceivingEnabled =
          configUtils.getConfiguredFeatureFlag(
              facility, WFSConstants.IS_TCL_FREE_RECEIVING_ENABLED);
      if (isTCLFreeReceivingEnabled) {
        String poNumber = "";
        if (ReceivingType.GS1
            .getReceivingType()
            .equalsIgnoreCase(instructionRequest.getReceivingType())) {
          poNumber =
              WFSUtility.getPoFromScannedDataMap(
                  WFSUtility.getScannedDataMap(instructionRequest.getScannedDataList()));
        } else {
          if (Objects.nonNull(instructionRequest.getAdditionalParams())
              && Objects.nonNull(
                  instructionRequest
                      .getAdditionalParams()
                      .get(ReceivingConstants.PURCHASE_REFERENCE_NUMBER)))
            poNumber =
                instructionRequest
                    .getAdditionalParams()
                    .get(ReceivingConstants.PURCHASE_REFERENCE_NUMBER)
                    .toString();
        }
        deliveryNumber =
            getDeliveryNumberByTCLFree(instructionRequest, deliveryNumber, poNumber, facility);
      }
      return deliveryNumber;
    } catch (ReceivingException receivingException) {
      throw receivingException;
    } catch (Exception e) {
      LOGGER.error("TCL Free : Exception when figuring out delivery number {}", e.getMessage());
      throw new ReceivingException(
          "Not able to find delivery number by UPC, proceed with docktag",
          HttpStatus.NOT_FOUND,
          "TCL.404.NOT_FOUND");
    }
  }
}
