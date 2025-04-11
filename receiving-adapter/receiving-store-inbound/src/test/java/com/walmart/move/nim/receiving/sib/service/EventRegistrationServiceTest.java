package com.walmart.move.nim.receiving.sib.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.event.processing.EventProcessingResolver;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.SIBTestUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EventRegistrationServiceTest extends ReceivingTestBase {

  @InjectMocks private EventRegistrationService eventRegistrationService;

  @Mock private EventProcessingResolver eventProcessingResolver;

  @Mock private EventRepository eventRepository;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private SIBManagedConfig sibManagedConfig;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(5504);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(tenantSpecificConfigReader);
    Mockito.reset(sibManagedConfig);
    Mockito.reset(eventProcessingResolver);
    Mockito.reset(eventRepository);
  }

  @Test
  public void processNGRParityCreditInvoice() {
    ASNDocument asnDocument =
        SIBTestUtils.getASNDocument("src/test/resource/asn/creditInvoiceAsn.json");
    ArgumentCaptor<List<EIEvent>> eventListCaptor = ArgumentCaptor.forClass(List.class);
    when(eventRepository.findAllByDeliveryNumberAndFacilityNumAndFacilityCountryCode(
            any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sibManagedConfig.getNgrEventForCreditInvoice()).thenReturn("SHORTAGE");
    when(sibManagedConfig.getCorrectionalInvoiceEventSubType()).thenReturn("INVOICE_CORRECTION");
    when(sibManagedConfig.getAssortmentTypes()).thenReturn(new ArrayList<>());
    Set<String> trackingIds =
        eventRegistrationService.processNGRParity(
            asnDocument, Collections.emptySet(), (pack, item) -> Constants.STORE);
    Assert.assertNotNull(trackingIds);
  }
}
