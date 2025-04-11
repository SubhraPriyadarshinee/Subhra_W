package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.acc.mock.data.MockDockTag;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.model.docktag.ReceiveDockTagRequest;
import com.walmart.move.nim.receiving.core.service.DockTagPersisterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RoboDepalEventServiceTest {
  @InjectMocks private RoboDepalEventService roboDepalEventService;
  @Mock private ACCDockTagService dockTagService;
  @Mock private DockTagPersisterService dockTagPersisterService;

  private ReceiveDockTagRequest receiveDockTagRequest;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");

    receiveDockTagRequest = new ReceiveDockTagRequest();
    receiveDockTagRequest.setMappedFloorLineLocation("CP04FL_C");
    receiveDockTagRequest.setWorkstationLocation("CP04FL_C-DEPAL-1");
    receiveDockTagRequest.setDockTagId("e328880000000000000000001");
  }

  @AfterMethod
  public void resetMocks() {
    reset(dockTagService);
    reset(dockTagPersisterService);
  }

  @Test
  public void testProcessDepalAckEvent() {
    when(dockTagPersisterService.getDockTagByDockTagId(anyString()))
        .thenReturn(MockDockTag.getDockTag());
    roboDepalEventService.processDepalAckEvent(
        receiveDockTagRequest, MockHttpHeaders.getHeaders("32888", "US"));
    verify(dockTagService, times(1))
        .validateDockTagFromDb(
            any(DockTag.class),
            anyString(),
            eq(ReceivingConstants.DOCK_TAG_NOT_FOUND_MESSAGE),
            any(HttpHeaders.class));
    verify(dockTagService, times(1))
        .updateDockTagStatusAndPublish(
            any(ReceiveDockTagRequest.class), any(DockTag.class), any(HttpHeaders.class));
  }

  @Test
  public void testProcessDepalFinishEvent() {
    roboDepalEventService.processDepalFinishEvent(
        receiveDockTagRequest, MockHttpHeaders.getHeaders("32888", "US"));
    verify(dockTagService, times(1)).completeDockTag(anyString(), any(HttpHeaders.class));
  }
}
