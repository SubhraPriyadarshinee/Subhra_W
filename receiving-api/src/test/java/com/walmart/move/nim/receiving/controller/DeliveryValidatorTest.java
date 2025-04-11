package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testng.annotations.Test;

@AutoConfigureMockMvc
@RunWith(MockitoJUnitRunner.Silent.class)
public class DeliveryValidatorTest extends TenantSpecificConfigReaderTestBase {

  @InjectMocks @Autowired private DeliveryValidator deliveryValidator;

  public static final String EXPECTED_MESSAGE = "Delivery status should be OPN or WRK";

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = EXPECTED_MESSAGE)
  public void testValidateDeliveryStatus() throws ReceivingException {
    // MockitoAnnotations.initMocks(this);
    doReturn(true).when(tenantSpecificConfigReader).getConfiguredFeatureFlag(anyString());
    deliveryValidator.validateDeliveryStatus(new DeliveryDocument());
  }
}
