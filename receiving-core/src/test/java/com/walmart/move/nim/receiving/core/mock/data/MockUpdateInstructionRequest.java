package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** @author lkotthi */
public class MockUpdateInstructionRequest {

  public static final String UNLOADER = "UNLR"; // WFT to use this user Role for Performance for GDC

  public static UpdateInstructionRequest getUpdateInstructionRequest() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(50001001L);
    updateInstructionRequest.setDoorNumber("101");

    DocumentLine documentLine = new DocumentLine();
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseReferenceNumber("4166030001");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setPoDCNumber("32612");
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setPurchaseCompanyId(1);
    documentLine.setDeptNumber(14);
    documentLine.setPoDeptNumber("14");
    documentLine.setGtin("00028000114602");
    documentLine.setItemNumber(573170821L);
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setVendorPackCost(15.88);
    documentLine.setWhpkSell(16.98);
    documentLine.setMaxOverageAcceptQty(10L);
    documentLine.setExpectedQty(100L);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");
    documentLine.setRotateDate(new Date());
    documentLine.setVnpkWgtQty(12.36F);
    documentLine.setVnpkWgtUom("lb");
    documentLine.setVnpkcbqty(0.533F);
    documentLine.setVnpkcbuomcd("CF");
    documentLine.setDescription("TEST ITEM DESCR   ");
    documentLine.setWarehouseMinLifeRemainingToReceive(7);

    List<DocumentLine> documentLines = new ArrayList<>();
    documentLines.add(documentLine);

    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static UpdateInstructionRequest getInvalidUpdateInstructionRequest() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(50001001L);
    updateInstructionRequest.setDoorNumber("101");

    DocumentLine documentLine = new DocumentLine();
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseReferenceNumber("4166030001");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setPoDCNumber("32612");
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setPurchaseCompanyId(1);
    documentLine.setDeptNumber(14);
    documentLine.setPoDeptNumber("14");
    documentLine.setGtin("00028000114602");
    documentLine.setItemNumber(573170821L);
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setVendorPackCost(15.88);
    documentLine.setWhpkSell(16.98);
    documentLine.setMaxOverageAcceptQty(10L);
    documentLine.setExpectedQty(100L);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");
    documentLine.setRotateDate(new Date());
    documentLine.setVnpkWgtQty(12.36F);
    documentLine.setVnpkWgtUom("lb");
    documentLine.setVnpkcbqty(0.533F);
    documentLine.setVnpkcbuomcd("CF");
    documentLine.setDescription("TEST ITEM DESCR   ");
    documentLine.setWarehouseMinLifeRemainingToReceive(null);

    List<DocumentLine> documentLines = new ArrayList<>();
    documentLines.add(documentLine);

    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static UpdateInstructionRequest getUpdateInstructionRequestWithContainerType() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(Long.parseLong("300001"));
    updateInstructionRequest.setDoorNumber("101");
    updateInstructionRequest.setContainerType("Chep Pallet");
    // RCV client will send userRole to RCV BE which will be sent to WTD in update Instruction
    updateInstructionRequest.setUserRole(UNLOADER);

    DocumentLine documentLine = new DocumentLine();
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseReferenceNumber("4166030001");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setPoDCNumber("32612");
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setPurchaseCompanyId(1);
    documentLine.setDeptNumber(14);
    documentLine.setPoDeptNumber("14");
    documentLine.setGtin("00028000114602");
    documentLine.setItemNumber(573170821L);
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setVendorPackCost(15.88);
    documentLine.setWhpkSell(16.98);
    documentLine.setMaxOverageAcceptQty(10L);
    documentLine.setExpectedQty(100L);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");

    List<DocumentLine> documentLines = new ArrayList<>();
    documentLines.add(documentLine);

    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static UpdateInstructionRequest getUpdateInstructionRequestWithoutContainerType() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(Long.parseLong("300001"));
    updateInstructionRequest.setDoorNumber("101");

    DocumentLine documentLine = new DocumentLine();
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseReferenceNumber("4166030001");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setPoDCNumber("32612");
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setPurchaseCompanyId(1);
    documentLine.setDeptNumber(14);
    documentLine.setPoDeptNumber("14");
    documentLine.setGtin("00028000114602");
    documentLine.setItemNumber(573170821L);
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setVendorPackCost(15.88);
    documentLine.setWhpkSell(16.98);
    documentLine.setMaxOverageAcceptQty(10L);
    documentLine.setExpectedQty(100L);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");

    List<DocumentLine> documentLines = new ArrayList<>();
    documentLines.add(documentLine);

    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }
}
