package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.repositories.PurgeDataRepository;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PurgeServiceTest extends ReceivingTestBase {

  @Autowired private PurgeDataRepository purgeDataRepository;
  @InjectMocks private PurgeService purgeService;
  @Mock private InstructionPersisterService instructionPersisterService;

  @Mock private PurgeConfig purgeConfig;
  @Mock private ApplicationContext applicationContext;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(purgeService, "purgeDataRepository", purgeDataRepository);
  }

  @AfterMethod
  public void cleanup() {
    purgeDataRepository.deleteAll();
    reset(instructionPersisterService);
  }

  @Test
  public void testCreateEntities() {
    List<PurgeData> entities =
        purgeService.createEntities(Arrays.asList(PurgeEntityType.INSTRUCTION));
    assertNotNull(entities);
    assertEquals(entities.size(), 1);
    assertEquals(entities.get(0).getPurgeEntityType(), PurgeEntityType.INSTRUCTION);
    assertEquals(entities.get(0).getLastDeleteId(), Long.valueOf(0L));
    assertEquals(entities.get(0).getEventTargetStatus(), EventTargetStatus.PENDING);
    assertNotNull(entities.get(0).getCreateTs());
    assertNull(entities.get(0).getLastChangeTs());
    assertEquals(purgeDataRepository.findAll().size(), 1);
  }

  public void createEntity(PurgeEntityType purgeEntityType, EventTargetStatus eventTargetStatus) {
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(purgeEntityType)
            .lastDeleteId(0L)
            .eventTargetStatus(eventTargetStatus)
            .build();
    purgeDataRepository.save(purgeData);
  }

  @Test
  public void testPurgeWhenAllEntitiesWithAllPending() {
    // createEntity
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.PENDING);
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.PENDING);

    when(purgeConfig.getDisabledEntities()).thenReturn(null);
    String batchSizeJson =
        "{\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"INSTRUCTION\": 30,\n"
            + "  \"default\": 90\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(10L);
    purgeService.purge();
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    ArgumentCaptor<PageRequest> pageRequestArgumentCaptor =
        ArgumentCaptor.forClass(PageRequest.class);
    verify(instructionPersisterService, times(1))
        .purge(captor.capture(), pageRequestArgumentCaptor.capture(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);
    assertEquals(pageRequestArgumentCaptor.getValue().getPageSize(), 100);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(10L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }

  @Test
  public void testPurgeWhenAllEntitiesWithMixedStatus() {
    // createEntity
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.DELETE);
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.PENDING);

    when(purgeConfig.getDisabledEntities()).thenReturn(null);
    String batchSizeJson =
        "{\n"
            + "  \"INSTRUCTION\": 200,\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"NOTIFICATION_LOG\": 30,\n"
            + "  \"default\": 30\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(0L);
    purgeService.purge();
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    verify(instructionPersisterService, times(1)).purge(captor.capture(), any(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(0L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }

  @Test
  public void testPurgeWhenAllEntitiesAreComplete() {
    // createEntity
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.DELETE);
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.DELETE);

    when(purgeConfig.getDisabledEntities()).thenReturn(null);

    String batchSizeJson =
        "{\n"
            + "  \"INSTRUCTION\": 200,\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"NOTIFICATION_LOG\": 30,\n"
            + "  \"default\": 30\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(10L);
    purgeService.purge();
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    ArgumentCaptor<PageRequest> pageRequestArgumentCaptor =
        ArgumentCaptor.forClass(PageRequest.class);
    verify(instructionPersisterService, times(1))
        .purge(captor.capture(), pageRequestArgumentCaptor.capture(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);
    assertEquals(pageRequestArgumentCaptor.getValue().getPageSize(), 200);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(10L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }

  @Test
  public void testPurgeWhenSomeEntitiesAreDisabled() {
    // createEntity
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.PENDING);
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.PENDING);

    when(purgeConfig.getDisabledEntities()).thenReturn(Arrays.asList(PurgeEntityType.CONTAINER));
    String batchSizeJson =
        "{\n"
            + "  \"INSTRUCTION\": 200,\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"NOTIFICATION_LOG\": 30,\n"
            + "  \"default\": 30\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(10L);
    purgeService.purge();
    // assert INSTRUCTION is picked up for purge
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    verify(instructionPersisterService, times(1)).purge(captor.capture(), any(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(10L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }

  @Test
  public void testPurgeWhenOneCompleteOneDisabled() {
    // createEntity
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.DELETE);
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.DELETE);

    when(purgeConfig.getDisabledEntities()).thenReturn(Arrays.asList(PurgeEntityType.CONTAINER));
    String batchSizeJson =
        "{\n"
            + "  \"INSTRUCTION\": 200,\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"NOTIFICATION_LOG\": 30,\n"
            + "  \"default\": 30\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(10L);
    purgeService.purge();
    // assert INSTRUCTION is picked up for purge
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    verify(instructionPersisterService, times(1)).purge(captor.capture(), any(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(10L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }

  @Test
  public void testPurgeWhenOneCompleteOneDisabledPending() {
    // createEntity
    createEntity(PurgeEntityType.INSTRUCTION, EventTargetStatus.DELETE);
    createEntity(PurgeEntityType.CONTAINER, EventTargetStatus.PENDING);

    when(purgeConfig.getDisabledEntities()).thenReturn(Arrays.asList(PurgeEntityType.CONTAINER));
    String batchSizeJson =
        "{\n"
            + "  \"INSTRUCTION\": 200,\n"
            + "  \"CONTAINER\": 500,\n"
            + "  \"RECEIPT\": 50,\n"
            + "  \"PRINTJOB\": 400,\n"
            + "  \"PROBLEM\": 60,\n"
            + "  \"DELIVERY_METADATA\": 10,\n"
            + "  \"DOCK_TAG\": 400,\n"
            + "  \"ITEM_CATALOG_UPDATE_LOG\": 1000,\n"
            + "  \"JMS_EVENT_RETRY\": 800,\n"
            + "  \"DELIVERY_EVENT\": 10,\n"
            + "  \"LABEL_DATA\": 1000,\n"
            + "  \"NOTIFICATION_LOG\": 1000,\n"
            + "  \"default\": 100\n"
            + "}";
    Map<String, Integer> batchSizeMap = JacksonParser.convertJsonToObject(batchSizeJson, Map.class);
    when(purgeConfig.getBatchSizeMap()).thenReturn(batchSizeMap);
    when(applicationContext.getBean(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE))
        .thenReturn(instructionPersisterService);
    String retentionPolicyJson =
        "{\n"
            + "  \"JMS_EVENT_RETRY\": 40,\n"
            + "  \"NOTIFICATION_LOG\": 30,\n"
            + "  \"default\": 30\n"
            + "}";
    Map<String, Integer> retentionPolicyMap =
        JacksonParser.convertJsonToObject(retentionPolicyJson, Map.class);
    when(purgeConfig.getRetentionPolicyMap()).thenReturn(retentionPolicyMap);
    when(instructionPersisterService.purge(any(), any(), eq(30))).thenReturn(10L);
    purgeService.purge();
    // assert INSTRUCTION is picked up for purge
    ArgumentCaptor<PurgeData> captor = ArgumentCaptor.forClass(PurgeData.class);
    verify(instructionPersisterService, times(1)).purge(captor.capture(), any(), eq(30));
    assertEquals(captor.getValue().getPurgeEntityType(), PurgeEntityType.INSTRUCTION);

    List<PurgeData> purgeDataList = purgeDataRepository.findAll();
    List<PurgeData> filteredList =
        purgeDataList
            .stream()
            .filter(
                purgeData1 -> purgeData1.getPurgeEntityType().equals(PurgeEntityType.INSTRUCTION))
            .collect(Collectors.toList());
    assertEquals(filteredList.size(), 1);
    assertEquals(filteredList.get(0).getEventTargetStatus(), EventTargetStatus.DELETE);
    assertEquals(filteredList.get(0).getLastDeleteId(), Long.valueOf(10L));
    assertNotNull(filteredList.get(0).getLastChangeTs());
  }
}
