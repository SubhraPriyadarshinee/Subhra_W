package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcVendorValidatorTest {

  @Mock private RdcManagedConfig rdcManagedConfig;
  @InjectMocks private RdcVendorValidator rdcVendorValidator;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testIsPilotVendorForAsnReceiving() {
    when(rdcManagedConfig.getAsnEnabledVendorsList()).thenReturn(Arrays.<String>asList("vendorId"));
    when(rdcManagedConfig.isAsnVendorCheckEnabled()).thenReturn(true);

    boolean result = rdcVendorValidator.isPilotVendorForAsnReceiving("vendorId");
    Assert.assertEquals(result, true);
  }

  @Test
  public void testIsAsnReceivingEnabled() {
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);

    boolean result = rdcVendorValidator.isAsnReceivingEnabled();
    Assert.assertEquals(result, true);
  }

  @Test
  public void testGetInternalAsnSourceTypes() {
    when(rdcManagedConfig.getInternalAsnSourceTypes()).thenReturn(Arrays.<String>asList("String"));

    List<String> result = rdcVendorValidator.getInternalAsnSourceTypes();
    Assert.assertEquals(result, Arrays.<String>asList("String"));
  }

  @Test
  public void testGetAsnEnabledVendorsList() {
    when(rdcManagedConfig.getAsnEnabledVendorsList()).thenReturn(Arrays.<String>asList("String"));

    List<String> result = rdcVendorValidator.getAsnEnabledVendorsList();
    Assert.assertEquals(result, Arrays.<String>asList("String"));
  }

  @Test
  public void testIsAsnVendorCheckEnabled() {
    when(rdcManagedConfig.isAsnVendorCheckEnabled()).thenReturn(true);

    boolean result = rdcVendorValidator.isAsnVendorCheckEnabled();
    Assert.assertTrue(result);
  }

  @Test
  public void testIsAsnVendorCheckEnabled_DSDC() {
    when(rdcManagedConfig.isDsdcAsnVendorCheckEnabled()).thenReturn(true);
    when(rdcManagedConfig.getDsdcAsnEnabledVendorsList())
        .thenReturn(Arrays.asList("32322", "4432323"));
    boolean result = rdcVendorValidator.isPilotVendorForDsdcAsnReceiving("4432323");
    Assert.assertTrue(result);
  }

  @Test
  public void testGetAutoPopulateReceiveQtyVendorList() {
    when(rdcManagedConfig.getAutoPopulateReceiveQtyVendorList())
        .thenReturn(Arrays.<String>asList("String"));

    List<String> result = rdcVendorValidator.getAutoPopulateReceiveQtyVendorList();
    Assert.assertEquals(result, Arrays.<String>asList("String"));
  }
}
