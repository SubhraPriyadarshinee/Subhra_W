package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

import java.util.List;
import org.springframework.util.CollectionUtils;

public class FixitCreateExceptionRequest {

  private static final String DETAILS = "<DETAILS>";
  private static final String DELIVERY = "<DELIVERY>";
  private static final String STORE = "<STORE>";
  private static final String EXCEPTION_TYPE = "<EXCEPTION_TYPE>";
  private static final String ITEMS = "<ITEMS>";
  private static final String INVOICES = "<INVOICES>";
  private static final String PURCHASE_ORDERS = "<PURCHASE_ORDERS>";
  private static final String USER_ID = "<USER_ID>";

  private String request =
      "{\"query\":\"mutation { createException( input: { details: ["
          + DETAILS
          + "] delivery:[ "
          + DELIVERY
          + "] invoices: ["
          + INVOICES
          + "] purchaseOrders: ["
          + PURCHASE_ORDERS
          + "] items: ["
          + ITEMS
          + "] comment: \\\""
          + EXCEPTION_TYPE
          + "\\\" userInfo: { userId: \\\""
          + STORE
          + "\\\", userName: \\\""
          + USER_ID
          + "\\\" } } ) { exceptionId identifier } }\",\"variables\":{}}";

  public String getRequest() {
    return request.replaceAll("<[A-Z_]*>", "");
  }

  public void setDetails(List<Detail> details) {
    StringBuilder listString = new StringBuilder();
    if (!CollectionUtils.isEmpty(details)) {
      details.forEach(detail -> listString.append(detail.getGraphQLString()).append(","));
    }
    request = request.replaceAll(DETAILS, listString.toString());
  }

  public void setDelivery(List<Delivery> deliveries) {
    StringBuilder listString = new StringBuilder();
    if (!CollectionUtils.isEmpty(deliveries)) {
      deliveries.forEach(delivery -> listString.append(delivery.getGraphQLString()).append(","));
    }
    request = request.replaceAll(DELIVERY, listString.toString());
  }

  public void setStore(String store) {
    request = request.replaceAll(STORE, store);
  }

  public void setItems(List<Item> items) {
    StringBuilder listString = new StringBuilder();
    if (!CollectionUtils.isEmpty(items)) {
      items.forEach(item -> listString.append(item.getGraphQLString()).append(","));
    }
    request = request.replaceAll(ITEMS, listString.toString());
  }

  public void setInvoices(List<Invoice> invoices) {
    StringBuilder listString = new StringBuilder();
    if (!CollectionUtils.isEmpty(invoices)) {
      invoices.forEach(invoice -> listString.append(invoice.getGraphQLString()).append(","));
    }
    request = request.replaceAll(INVOICES, listString.toString());
  }

  public void setPurchaseOrders(List<PurchaseOrder> purchaseOrders) {
    StringBuilder listString = new StringBuilder();
    if (!CollectionUtils.isEmpty(purchaseOrders)) {
      purchaseOrders.forEach(
          purchaseOrder -> listString.append(purchaseOrder.getGraphQLString()).append(","));
    }
    request = request.replaceAll(PURCHASE_ORDERS, listString.toString());
  }

  public void setExceptionType(String exceptionType) {
    request = request.replaceAll(EXCEPTION_TYPE, exceptionType);
  }

  public void setUserId(String userId) {
    request = request.replaceAll(USER_ID, userId);
  }
}
