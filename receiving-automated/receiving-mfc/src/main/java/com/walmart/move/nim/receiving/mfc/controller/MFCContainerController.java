package com.walmart.move.nim.receiving.mfc.controller;

import com.walmart.move.nim.receiving.mfc.model.controller.ContainerRequestPayload;
import com.walmart.move.nim.receiving.mfc.model.controller.ContainerResponse;
import com.walmart.move.nim.receiving.mfc.model.controller.InvoiceNumberDetectionRequest;
import com.walmart.move.nim.receiving.mfc.model.controller.InvoiceNumberDetectionResponse;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.mfc.app:false}")
@RestController
@RequestMapping("/mfc/containers")
public class MFCContainerController {

  @Autowired private MFCContainerService mfcContainerService;

  @PutMapping
  public ContainerResponse performContainerUnicity(
      @Valid @RequestBody ContainerRequestPayload containerRequestPaylod,
      @RequestParam(name = "includeAllDelivery", required = false) boolean includeAllDelivery) {
    return mfcContainerService.processDuplicateContainer(
        containerRequestPaylod, includeAllDelivery);
  }

  @PutMapping("/publish")
  public ContainerResponse publishSelectedContainer(
      @Valid @RequestBody ContainerRequestPayload containerRequestPayload) {
    return mfcContainerService.publishSelectedContainer(containerRequestPayload);
  }

  @PutMapping("/detect-invoice")
  public InvoiceNumberDetectionResponse detectInvoiceNumber(
      @Valid @RequestBody InvoiceNumberDetectionRequest invoiceNumberDetectionRequest) {
    return mfcContainerService.detectInvoiceNumber(invoiceNumberDetectionRequest);
  }
}
