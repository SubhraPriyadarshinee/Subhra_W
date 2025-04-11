package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.model.acl.notification.HawkEyeDeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HawkeyeDeliveryLinkServiceTest {

  @InjectMocks private HawkEyeDeliveryLinkService hawkEyeDeliveryLinkService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private GenericLabelGeneratorService genericLabelGeneratorService;
  @Mock private LabelDataService labelDataService;
  @Mock private HawkEyeService hawkeyeService;

  private DeliveryAndLocationMessage deliveryAndLocationMessage;
  private DeliveryAndLocationMessage deliveryAndLocationMessage2;
  private HawkEyeDeliveryAndLocationMessage hawkEyeDeliveryAndLocationMessage;
  private HawkEyeDeliveryAndLocationMessage hawkEyeDeliveryAndLocationMessage2;

  @BeforeClass
  public void setupRoot() {
    MockitoAnnotations.initMocks(this);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(genericLabelGeneratorService);

    deliveryAndLocationMessage = new DeliveryAndLocationMessage();
    deliveryAndLocationMessage.setUserId("sysadmin");
    deliveryAndLocationMessage.setLocation("247");
    deliveryAndLocationMessage.setDeliveryNbr("40375082");

    deliveryAndLocationMessage2 = new DeliveryAndLocationMessage();
    deliveryAndLocationMessage2.setUserId("rcvuser");
    deliveryAndLocationMessage2.setLocation("248");
    deliveryAndLocationMessage2.setDeliveryNbr("40375083");

    hawkEyeDeliveryAndLocationMessage = new HawkEyeDeliveryAndLocationMessage();
    hawkEyeDeliveryAndLocationMessage.setUserId("sysadmin");
    hawkEyeDeliveryAndLocationMessage.setLocation("247");
    hawkEyeDeliveryAndLocationMessage.setDeliveryNumber(40375082L);

    hawkEyeDeliveryAndLocationMessage2 = new HawkEyeDeliveryAndLocationMessage();
    hawkEyeDeliveryAndLocationMessage2.setUserId("rcvuser");
    hawkEyeDeliveryAndLocationMessage2.setLocation("248");
    hawkEyeDeliveryAndLocationMessage2.setDeliveryNumber(40375083L);
  }

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void cleanup() {
    reset(genericLabelGeneratorService);
    reset(labelDataService);
    reset(hawkeyeService);
  }

  @Test
  public void testUpdateDeliveryLinkEmptyResponse() {
    when(hawkeyeService.deliveryLink(any())).thenReturn(Collections.emptyList());
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        Collections.singletonList(deliveryAndLocationMessage), MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1))
        .deliveryLink(eq(Collections.singletonList(hawkEyeDeliveryAndLocationMessage)));
    verify(labelDataService, times(0)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(0)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkSuccessfulLinkAndCount() {
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(784);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    ACLLabelCount aclLabelCount =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    when(hawkeyeService.deliveryLink(any())).thenReturn(Collections.singletonList(aclLabelCount));
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        Collections.singletonList(deliveryAndLocationMessage), MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1))
        .deliveryLink(eq(Collections.singletonList(hawkEyeDeliveryAndLocationMessage)));
    verify(labelDataService, times(1)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(0)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkSuccessfulLinkAndCount_MultiManifest() {
    when(labelDataService.countByDeliveryNumber(eq(40375082L))).thenReturn(784);
    when(labelDataService.countByDeliveryNumber(eq(40375023L))).thenReturn(114);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    List<DeliveryAndLocationMessage> deliveryAndLocationMessages = new ArrayList<>();
    deliveryAndLocationMessages.add(deliveryAndLocationMessage);
    deliveryAndLocationMessages.add(deliveryAndLocationMessage2);
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);
    ACLLabelCount aclLabelCount1 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    ACLLabelCount aclLabelCount2 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount2.setDeliveryNumber(40375023L);
    aclLabelCount2.setLabelsCount(114);
    List<ACLLabelCount> aclLabelCounts = new ArrayList<>();
    aclLabelCounts.add(aclLabelCount1);
    aclLabelCounts.add(aclLabelCount2);
    when(hawkeyeService.deliveryLink(any())).thenReturn(aclLabelCounts);
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        deliveryAndLocationMessages, MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1)).deliveryLink(eq(hawkEyeDeliveryAndLocationMessages));
    verify(labelDataService, times(2)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(0)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkSuccessfulLinkAndCount_MultiManifest_Partial() {
    when(labelDataService.countByDeliveryNumber(eq(40375082L))).thenReturn(784);
    when(labelDataService.countByDeliveryNumber(eq(40375023L))).thenReturn(110);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    List<DeliveryAndLocationMessage> deliveryAndLocationMessages = new ArrayList<>();
    deliveryAndLocationMessages.add(deliveryAndLocationMessage);
    deliveryAndLocationMessages.add(deliveryAndLocationMessage2);
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);
    ACLLabelCount aclLabelCount1 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    ACLLabelCount aclLabelCount2 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount2.setDeliveryNumber(40375023L);
    aclLabelCount2.setLabelsCount(114);
    List<ACLLabelCount> aclLabelCounts = new ArrayList<>();
    aclLabelCounts.add(aclLabelCount1);
    aclLabelCounts.add(aclLabelCount2);
    when(hawkeyeService.deliveryLink(any())).thenReturn(aclLabelCounts);
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        deliveryAndLocationMessages, MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1)).deliveryLink(eq(hawkEyeDeliveryAndLocationMessages));
    verify(labelDataService, times(2)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(1)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkSuccessfulLinkMismatchCount_MultiManifest() {
    when(labelDataService.countByDeliveryNumber(eq(40375082L))).thenReturn(780);
    when(labelDataService.countByDeliveryNumber(eq(40375023L))).thenReturn(110);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    List<DeliveryAndLocationMessage> deliveryAndLocationMessages = new ArrayList<>();
    deliveryAndLocationMessages.add(deliveryAndLocationMessage);
    deliveryAndLocationMessages.add(deliveryAndLocationMessage2);
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);
    ACLLabelCount aclLabelCount1 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    ACLLabelCount aclLabelCount2 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount2.setDeliveryNumber(40375023L);
    aclLabelCount2.setLabelsCount(114);
    List<ACLLabelCount> aclLabelCounts = new ArrayList<>();
    aclLabelCounts.add(aclLabelCount1);
    aclLabelCounts.add(aclLabelCount2);
    when(hawkeyeService.deliveryLink(any())).thenReturn(aclLabelCounts);
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        deliveryAndLocationMessages, MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1)).deliveryLink(eq(hawkEyeDeliveryAndLocationMessages));
    verify(labelDataService, times(2)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(2)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkFailedLink_MultiManifest_Partial() {
    when(labelDataService.countByDeliveryNumber(eq(40375082L))).thenReturn(784);
    when(labelDataService.countByDeliveryNumber(eq(40375023L))).thenReturn(114);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    List<DeliveryAndLocationMessage> deliveryAndLocationMessages = new ArrayList<>();
    deliveryAndLocationMessages.add(deliveryAndLocationMessage);
    deliveryAndLocationMessages.add(deliveryAndLocationMessage2);
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);
    ACLLabelCount aclLabelCount1 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    ACLLabelCount aclLabelCount2 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount2.setDeliveryNumber(40375023L);
    aclLabelCount2.setLabelsCount(114);
    aclLabelCount2.setEquipmentName("NOT_LINKED");
    List<ACLLabelCount> aclLabelCounts = new ArrayList<>();
    aclLabelCounts.add(aclLabelCount1);
    aclLabelCounts.add(aclLabelCount2);
    when(hawkeyeService.deliveryLink(any())).thenReturn(aclLabelCounts);
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        deliveryAndLocationMessages, MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1)).deliveryLink(eq(hawkEyeDeliveryAndLocationMessages));
    verify(labelDataService, times(2)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(1)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkFailedLink_MultiManifest_Complete() {
    when(labelDataService.countByDeliveryNumber(eq(40375082L))).thenReturn(780);
    when(labelDataService.countByDeliveryNumber(eq(40375023L))).thenReturn(110);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    List<DeliveryAndLocationMessage> deliveryAndLocationMessages = new ArrayList<>();
    deliveryAndLocationMessages.add(deliveryAndLocationMessage);
    deliveryAndLocationMessages.add(deliveryAndLocationMessage2);
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);
    ACLLabelCount aclLabelCount1 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    ACLLabelCount aclLabelCount2 =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount2.setDeliveryNumber(40375023L);
    aclLabelCount2.setLabelsCount(114);
    aclLabelCount2.setEquipmentName("NOT_LINKED");
    aclLabelCount1.setEquipmentName("NOT_LINKED");
    List<ACLLabelCount> aclLabelCounts = new ArrayList<>();
    aclLabelCounts.add(aclLabelCount1);
    aclLabelCounts.add(aclLabelCount2);
    when(hawkeyeService.deliveryLink(any())).thenReturn(aclLabelCounts);
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        deliveryAndLocationMessages, MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1)).deliveryLink(eq(hawkEyeDeliveryAndLocationMessages));
    verify(labelDataService, times(2)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(2)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkSuccessfulLinkMismatchCount() {
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(800);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    ACLLabelCount aclLabelCount =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    when(hawkeyeService.deliveryLink(any())).thenReturn(Collections.singletonList(aclLabelCount));
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        Collections.singletonList(deliveryAndLocationMessage), MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1))
        .deliveryLink(eq(Collections.singletonList(hawkEyeDeliveryAndLocationMessage)));
    verify(labelDataService, times(1)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(1)).publishACLLabelDataForDelivery(anyLong(), any());
  }

  @Test
  public void testUpdateDeliveryLinkFailedLink() {
    when(labelDataService.countByDeliveryNumber(anyLong())).thenReturn(784);
    String eventMessage = MockACLMessageData.getHawkeyeDeliveryLinkEvent();
    ACLLabelCount aclLabelCount =
        JacksonParser.convertJsonToObject(eventMessage, ACLLabelCount.class);
    aclLabelCount.setEquipmentName("NOT_LINKED");
    when(hawkeyeService.deliveryLink(any())).thenReturn(Collections.singletonList(aclLabelCount));
    hawkEyeDeliveryLinkService.updateDeliveryLink(
        Collections.singletonList(deliveryAndLocationMessage), MockHttpHeaders.getHeaders());
    verify(hawkeyeService, times(1))
        .deliveryLink(eq(Collections.singletonList(hawkEyeDeliveryAndLocationMessage)));
    verify(labelDataService, times(1)).countByDeliveryNumber(anyLong());
    verify(genericLabelGeneratorService, times(1)).publishACLLabelDataForDelivery(anyLong(), any());
  }
}
