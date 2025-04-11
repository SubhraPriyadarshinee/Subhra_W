package com.walmart.move.nim.receiving.core.azure;

import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AzureBlobConfigTest {

  @InjectMocks private AzureBlobConfig azureBlobConfig;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void azureBlobConfigTest() {
    ReflectionTestUtils.setField(azureBlobConfig, "instructionDownloadStorageAccountName", "test");
    ReflectionTestUtils.setField(azureBlobConfig, "instructionDownloadStorageAccountKey", "test");
    String storageAccountKey = azureBlobConfig.getInstructionDownloadStorageAccountKey();
    AssertJUnit.assertNotNull(storageAccountKey);
    AssertJUnit.assertEquals(storageAccountKey, "test");
    String storageAccountName = azureBlobConfig.getInstructionDownloadStorageAccountName();
    AssertJUnit.assertNotNull(storageAccountName);
    AssertJUnit.assertEquals(storageAccountName, "test");
  }
}
