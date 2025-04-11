package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockLabelMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.core.model.LabelIdAndTrackingIdPair;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LabelServiceImplTest extends ReceivingTestBase {

  @InjectMocks LabelServiceImpl labelServiceImpl;
  @Mock AppConfig appConfig;
  @Mock ContainerRepository containerRepository;
  @Spy ContainerService containerService;
  @Mock ContainerPersisterService containerPersisterService;
  @Mock LabelPersisterService labelPersisterService;
  @Mock ApplicationContext applicationContext;
  @Mock CcDaNonConPalletLabelProcessor ccDaNonConPalletLabelProcessor;
  @Mock TenantSpecificConfigReader configUtils;
  @InjectMocks private ResourceBundleMessageSource resourceBundleMessageSource;

  @Autowired private Gson gson;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
    TenantContext.setFacilityCountryCode("US");
    ReflectionTestUtils.setField(containerService, "containerRepository", containerRepository);
    ReflectionTestUtils.setField(labelServiceImpl, "gson", gson);
    ReflectionTestUtils.setField(
        labelServiceImpl, "resourceBundleMessageSource", resourceBundleMessageSource);
  }

  @AfterTest
  @AfterMethod
  public void resetMocks() {
    reset(containerRepository);
    reset(applicationContext);
    reset(containerPersisterService);
    reset(labelPersisterService);
    reset(configUtils);
    reset(appConfig);
  }

  List<ReprintLabelData> getReprintLabelDataList() {
    List<ReprintLabelData> reprintLabelDataList = new ArrayList<>();
    ReprintLabelData reprintLabelData1 =
        new ReprintLabelData(
            "78347348",
            "dummy Description 1",
            "sysadmin",
            new Date(),
            null,
            "78347349",
            "00059845600456412103");
    ReprintLabelData reprintLabelData2 =
        new ReprintLabelData(
            "78347349", null, "sysadmin", new Date(), null, null, "00059845600456412104");
    ReprintLabelData reprintLabelData3 =
        new ReprintLabelData(
            "78347348", null, "rcvuser", new Date(), "DT", null, "00059845600456412105");
    ReprintLabelData reprintLabelData4 =
        new ReprintLabelData(
            "78347348",
            "dummy Description 1",
            "rcvuser",
            new Date(),
            null,
            null,
            "00059845600456412106");
    reprintLabelDataList.add(reprintLabelData1);
    reprintLabelDataList.add(reprintLabelData2);
    reprintLabelDataList.add(reprintLabelData3);
    reprintLabelDataList.add(reprintLabelData4);
    return reprintLabelDataList;
  }

  @Test
  public void testReprintLabelsByAllUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumber(any(), any(), any(), any(), any()))
        .thenReturn(getReprintLabelDataList());
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", false);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumber(any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 4);
  }

  @Test
  public void testReprintLabelsBySingleUser_success() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(
            getReprintLabelDataList()
                .stream()
                .filter(temp -> temp.getCreateUser().equalsIgnoreCase("sysAdmin"))
                .collect(Collectors.toList()));
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumberByUserId(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 2);
  }

  @Test
  public void testReprintLabelsByLimitingRecords_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(4);
    when(containerRepository.getLabelDataByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(getReprintLabelDataList());

    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumberByUserId(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(argumentCaptor.getValue().getPageSize(), 4);
  }

  @Test
  public void testReprintLabelsWhenNoRecordFound_success() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(new ArrayList<>());
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumberByUserId(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 0);
  }

  @Test
  public void testGetReprintLabelData_Success() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(12345);
    Set<String> trackingIds = new HashSet<>();
    trackingIds.add("F32835000020050054");
    List<LabelIdAndTrackingIdPair> labelIdAndTrackingIdPairList = new ArrayList<>();
    labelIdAndTrackingIdPairList.add(new LabelIdAndTrackingIdPair("12345", 101));
    labelIdAndTrackingIdPairList.add(new LabelIdAndTrackingIdPair("45678", 101));
    labelIdAndTrackingIdPairList.add(new LabelIdAndTrackingIdPair("34567", 102));

    ContainerMetaDataForPalletLabel containerMetaDataForPalletLabel =
        new ContainerMetaDataForPalletLabel();
    containerMetaDataForPalletLabel.setTrackingId("12345");
    containerMetaDataForPalletLabel.setTrackingId("103");

    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    when(labelPersisterService.getLabelMetaDataByLabelIdsIn(any()))
        .thenReturn(MockLabelMetaData.getLabelMetaData());
    when(containerRepository.getLabelIdsByTrackingIdsWhereLabelIdNotNull(any()))
        .thenReturn(labelIdAndTrackingIdPairList);
    when(applicationContext.getBean(anyString())).thenReturn(ccDaNonConPalletLabelProcessor);
    when(ccDaNonConPalletLabelProcessor.populateLabelData(any(), any()))
        .thenReturn(gson.toJson(getLabelPrintRequest()));
    try {
      labelServiceImpl.getReprintLabelData(trackingIds, MockHttpHeaders.getHeaders());
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testGetReprintLabelData_Exception() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(12345);
    Set<String> trackingIds = new HashSet<>();
    trackingIds.add("F32835000020050054");
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    containers.get(0).setTrackingId("F32835000020050054");
    containers.get(0).setLabelId(LabelFormatId.CC_DA_NON_CON_PALLET_LABEL_FORMAT.getLabelId());

    LabelMetaData labelMetaData = getLabelMetaData();
    labelMetaData.setLabelName(null);

    when(applicationContext.getBean(anyString())).thenReturn(ccDaNonConPalletLabelProcessor);
    when(ccDaNonConPalletLabelProcessor.populateLabelData(any(), any()))
        .thenReturn(gson.toJson(getLabelPrintRequest()));

    try {
      labelServiceImpl.getReprintLabelData(trackingIds, MockHttpHeaders.getHeaders());
    } catch (Exception e) {
      assertTrue(true);
    }
    verify(applicationContext, times(0)).getBean(anyString());
    verify(ccDaNonConPalletLabelProcessor, times(0)).populateLabelData(any(), any());
  }

  @Test
  public void testGetReprintLabelDataForBackoutContainer_NoData() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(12345);
    Set<String> trackingIds = new HashSet<>();
    trackingIds.add("F32835000020050054");
    List<Container> containers = Arrays.asList(MockContainer.getContainer());
    containers.get(0).setTrackingId("F32835000020050054");
    containers.get(0).setLabelId(null);
    containers.get(0).setContainerStatus("backout");

    LabelMetaData labelMetaData = getLabelMetaData();
    labelMetaData.setLabelName(null);

    try {
      labelServiceImpl.getReprintLabelData(trackingIds, MockHttpHeaders.getHeaders());
    } catch (Exception e) {
      fail();
    }
    verify(applicationContext, times(0)).getBean(anyString());
    verify(ccDaNonConPalletLabelProcessor, times(0)).populateLabelData(any(), any());
  }

  private LabelMetaData getLabelMetaData() {
    LabelMetaData labelMetaData = new LabelMetaData();
    labelMetaData.setId(1L);
    labelMetaData.setLabelId(101);
    labelMetaData.setLabelName(LabelFormatId.CC_DA_NON_CON_PALLET_LABEL_FORMAT);
    labelMetaData.setLpaasFormatName("pallet_lpn_format");
    labelMetaData.setJsonTemplate(
        "{\"labelIdentifier\": \"${trackingId}\",\"formatName\": \"pallet_lpn_format\",\"data\": [{\"key\": \"LPN\",\"value\": \"${trackingId}\"},{\"key\": \"TYPE\",\"value\": \"DA_NC\"},{\"key\": \"DESTINATION\",\"value\": \"${destination}\"},{\"key\": \"ITEM\",\"value\": \"${itemNumber}\"},{\"key\": \"UPCBAR\",\"value\": \"${gtin}\"},{\"key\": \"DESC1\",\"value\": \"${description}\"},{\"key\": \"FULLUSERID\",\"value\": \"${userId}\"},{\"key\": \"ORIGIN\",\"value\": \"${origin}\"},{\"key\": \"DELIVERY\",\"value\": \"${deliveryNumber}\"},{\"key\": \"DOOR\",\"value\": \"${location}\"},{\"key\": \"QTY\",\"value\": \"${qty}\"}],\"ttlInHours\": 72.0}");
    return labelMetaData;
  }

  private PrintLabelRequest getLabelPrintRequest() {
    PrintLabelRequest labelPrintRequest = new PrintLabelRequest();
    labelPrintRequest.setTtlInHours(72);
    labelPrintRequest.setFormatName("pallet_lpn_format");
    labelPrintRequest.setLabelIdentifier("F32835000020050054");

    List<LabelData> datum = new ArrayList<>();
    LabelData lpn = new LabelData();
    lpn.setKey("lpn");
    lpn.setValue("F32835000020050054");
    datum.add(lpn);

    LabelData type = new LabelData();
    type.setKey("TYPE");
    type.setValue("DA_NC");
    datum.add(type);

    LabelData origin = new LabelData();
    origin.setKey("ORIGIN");
    origin.setValue("12345");
    datum.add(origin);

    LabelData item = new LabelData();
    item.setKey("ITEMNUMBER");
    item.setValue("12345678");
    datum.add(item);

    labelPrintRequest.setData(datum);
    return labelPrintRequest;
  }

  @Test
  public void testReprintLabelsByAllUser_DACon() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumber(any(), any(), any(), any(), any()))
        .thenReturn(getReprintLabelDataList());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", false);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumber(any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 3);
  }

  @Test
  public void testReprintLabelsBySingleUser_DACon() {
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(
            getReprintLabelDataList()
                .stream()
                .filter(temp -> temp.getCreateUser().equalsIgnoreCase("sysAdmin"))
                .collect(Collectors.toList()));
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumberByUserId(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 1);
  }

  @Test
  public void testReprintLabelsWhenNoRecordFound_DACon() {
    ArgumentCaptor<Pageable> argumentCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(appConfig.getFetchLabelsLimit()).thenReturn(5);
    when(containerRepository.getLabelDataByDeliveryNumberByUserId(
            any(), any(), any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(new ArrayList<>());
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(appConfig.getMaxAllowedReprintLabels()).thenReturn(100);
    List<PrintJobResponse> responses = labelServiceImpl.getLabels(22112211L, "sysadmin", true);
    verify(containerRepository, times(1))
        .getLabelDataByDeliveryNumberByUserId(any(), any(), any(), any(), any(), any());
    Assert.assertEquals(responses.size(), 0);
  }
}
