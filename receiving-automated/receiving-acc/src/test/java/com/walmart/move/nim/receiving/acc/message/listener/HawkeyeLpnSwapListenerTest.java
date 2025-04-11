package com.walmart.move.nim.receiving.acc.message.listener;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.acc.entity.LpnSwapEntity;
import com.walmart.move.nim.receiving.acc.model.HawkeyeLpnPayload;
import com.walmart.move.nim.receiving.acc.repositories.LPNSwapRepository;
import com.walmart.move.nim.receiving.acc.service.HawkeyeLpnSwapService;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class HawkeyeLpnSwapListenerTest {

  @InjectMocks private HawkeyeLpnSwapListener hawkeyeLpnSwapListener;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Spy @InjectMocks private HawkeyeLpnSwapService hawkeyeLpnSwapService;

  @Mock private HawkeyeLpnSwapService hawkeyeLpnSwapService1;

  @Mock private LPNSwapRepository lpnSwapRepository;
  @Mock private ContainerService containerService;

  @Mock private ContainerRepository containerRepository;

  private String eventMessage;

  @BeforeClass
  public void setRootUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    String dataPath;
    try {

      dataPath =
          new File("../../receiving-test/src/main/resources/json/Hawkeye_lpn_swap_payload.json")
              .getCanonicalPath();
      eventMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }

    TenantContext.setFacilityNum(6561);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(hawkeyeLpnSwapService);
    reset(hawkeyeLpnSwapService1);
    reset(lpnSwapRepository);
  }

  @Test
  public void testListenerWithData() {
    hawkeyeLpnSwapListener.listen(eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(hawkeyeLpnSwapService1, times(1)).swapAndProcessLpn(any());
  }

  @Test
  public void testListenerWithoutData() {
    String emptyString = "";
    hawkeyeLpnSwapListener.listen(emptyString, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(hawkeyeLpnSwapService, times(0)).swapAndProcessLpn(any());
  }

  @Test
  public void testSwapAndProcessLpn() throws ReceivingException {
    when(containerService.getContainerByTrackingId(any())).thenReturn(getContainerInfo());
    HawkeyeLpnPayload hawkeyeLpnPayload = getAccPaLpnPayload();
    when(lpnSwapRepository.save(any())).thenReturn(getLpnSwapEntityFromPayload(hawkeyeLpnPayload));
    doNothing().when(hawkeyeLpnSwapService).eventProcess(any(), any());

    hawkeyeLpnSwapService.swapAndProcessLpn(hawkeyeLpnPayload);
    verify(containerService, times(2)).getContainerByTrackingId(any());
    verify(hawkeyeLpnSwapService, times(1)).saveLPNSwapEntry(any());
    verify(hawkeyeLpnSwapService, times(1)).swapContainers(any(), any());
    verify(hawkeyeLpnSwapService, times(1)).saveContainersAfterSwap(any(), any());
    verify(hawkeyeLpnSwapService, times(1)).updateLPNSwapEntry(any());
    verify(hawkeyeLpnSwapService, times(1)).eventProcess(any(), any());
  }

  private HawkeyeLpnPayload getAccPaLpnPayload() {
    return JacksonParser.convertJsonToObject(eventMessage, HawkeyeLpnPayload.class);
  }

  public static Container getContainerInfo() {
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(1L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setInboundChannelMethod("CROSSU");

    List<Distribution> distributionList = new ArrayList<>();
    containerItem.setDistributions(distributionList);

    containerItems.add(containerItem);
    container.setDeliveryNumber(1234L);
    container.setCreateUser("sysadmin");
    container.setTrackingId("a329870000000000000000001");
    container.setContainerStatus("");
    container.setContainerItems(containerItems);
    Map<String, String> destinationMap = new HashMap<>();
    destinationMap.put("buNumber", "6066");
    destinationMap.put("countryCode", "US");
    container.setDestination(destinationMap);
    return container;
  }

  public LpnSwapEntity getLpnSwapEntityFromPayload(HawkeyeLpnPayload hawkeyeLpnPayload) {
    return LpnSwapEntity.builder()
        .lpn(hawkeyeLpnPayload.getLpn())
        .swappedLPN(hawkeyeLpnPayload.getSwappedLpn())
        .destination(hawkeyeLpnPayload.getDestination())
        .swappedDestination(hawkeyeLpnPayload.getSwappedDestination())
        .groupNumber(hawkeyeLpnPayload.getGroupNumber())
        .itemNumber(hawkeyeLpnPayload.getItemNumber())
        .poNumber(hawkeyeLpnPayload.getPoNumber())
        .poType(hawkeyeLpnPayload.getPoType())
        .build();
  }
}
