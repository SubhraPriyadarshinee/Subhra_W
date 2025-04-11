package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.VendorUpcUpdateRequest;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.nio.charset.Charset;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACLServiceTest extends ReceivingTestBase {

  @Mock private SimpleRestConnector simpleRestConnector;

  @Mock private ACCManagedConfig accManagedConfig;

  @InjectMocks private ACLService aclService;

  private Gson gson;

  private VendorUpcUpdateRequest vendorUpcUpdateRequestToAcl;

  @BeforeMethod()
  public void setup() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(aclService, "gson", gson);
    vendorUpcUpdateRequestToAcl =
        VendorUpcUpdateRequest.builder()
            .deliveryNumber("87654321")
            .itemNumber("567898765")
            .locationId("100")
            .catalogGTIN("20000943037194")
            .build();
  }

  @AfterMethod()
  public void resetMocks() {
    reset(simpleRestConnector);
    reset(accManagedConfig);
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = ACCConstants.SYSTEM_BUSY)
  public void testUpdateVendorUpcWhenAclServiceIsDown() {
    doThrow(new ResourceAccessException(ACCConstants.SYSTEM_BUSY))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    aclService.updateVendorUpc(vendorUpcUpdateRequestToAcl, MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    fail("Service down exception is supposed to be thrown");
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid request for upc update, delivery = 87654321, item = 567898765")
  public void testUpdateVendorUpcBadRequest() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    aclService.updateVendorUpc(vendorUpcUpdateRequestToAcl, MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    fail("Bad request exception is supposed to be thrown");
  }

  @Test
  public void testUpdateVendorUpcSuccessful() {
    doReturn(new ResponseEntity<String>("", HttpStatus.OK))
        .when(simpleRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    aclService.updateVendorUpc(vendorUpcUpdateRequestToAcl, MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }
}
