package com.walmart.move.nim.receiving.core.common;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class ContainerValidationUtilsTest extends AbstractTestNGSpringContextTests {

  @Test
  public void testValidContainerRequest() {
    try {
      ContainerValidationUtils.validateContainerRequest(MockContainer.getContainerRequest());
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test
  public void testContainerRequestInvalidTrackingId() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.setTrackingId(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "trackingId"));
    }
  }

  @Test
  public void testContainerRequestInvalidCtrType() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.setCtrType("CHEP");
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(e.getMessage(), ReceivingException.INVALID_CONTAINER_TYPE_ERROR_MSG);
    }
  }

  @Test
  public void testContainerRequestWithNoContents() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.setContents(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "contents"));
    }
  }

  @Test
  public void testContainerRequestInvalidPO() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setPurchaseReferenceNumber(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(
              ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "purchaseReferenceNumber"));
    }
  }

  @Test
  public void testContainerRequestInvalidPoNbr() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setPurchaseReferenceLineNumber(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(
              ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG,
              "purchaseReferenceLineNumber"));
    }
  }

  @Test
  public void testContainerRequestInvalidQuantity() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setQuantity(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "quantity"));
    }
  }

  @Test
  public void testContainerRequestInvalidQuantityUOM() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setQuantityUom(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "quantityUom"));
    }
  }

  @Test
  public void testContainerRequestInvalidVnpkQty() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setVnpkQty(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "vnpkQty"));
    }
  }

  @Test
  public void testContainerRequestInvalidWhpkQty() {
    ContainerRequest containerRequest = MockContainer.getContainerRequest();
    containerRequest.getContents().get(0).setWhpkQty(null);
    try {
      ContainerValidationUtils.validateContainerRequest(containerRequest);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "whpkQty"));
    }
  }
}
