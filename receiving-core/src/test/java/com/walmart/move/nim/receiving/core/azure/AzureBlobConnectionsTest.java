package com.walmart.move.nim.receiving.core.azure;

import com.azure.storage.blob.BlobServiceClient;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AzureBlobConnectionsTest {

  @InjectMocks private AzureBlobConnections azureBlobConnections;

  @Mock private AzureBlobConfig azureBlobConfig;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(azureBlobConfig);
  }

  @Test
  public void instructionDownloadBlobServiceClientTest() {
    Mockito.when(azureBlobConfig.getInstructionDownloadStorageAccountKey()).thenReturn("test");
    Mockito.when(azureBlobConfig.getInstructionDownloadStorageAccountName()).thenReturn("test");
    BlobServiceClient blobServiceClient =
        azureBlobConnections.instructionDownloadBlobServiceClient();
    AssertJUnit.assertNotNull(blobServiceClient);
    Mockito.verify(azureBlobConfig, Mockito.times(1)).getInstructionDownloadStorageAccountKey();
    Mockito.verify(azureBlobConfig, Mockito.times(2)).getInstructionDownloadStorageAccountName();
  }

  @Test(expectedExceptions = Exception.class)
  public void instructionDownloadBlobServiceClientException() {
    Mockito.when(azureBlobConfig.getInstructionDownloadStorageAccountKey())
        .thenThrow(new Exception());
    Mockito.when(azureBlobConfig.getInstructionDownloadStorageAccountName()).thenReturn("test");
    BlobServiceClient blobServiceClient =
        azureBlobConnections.instructionDownloadBlobServiceClient();
    AssertJUnit.assertNotNull(blobServiceClient);
    Mockito.verify(azureBlobConfig, Mockito.times(1)).getInstructionDownloadStorageAccountKey();
    Mockito.verify(azureBlobConfig, Mockito.times(2)).getInstructionDownloadStorageAccountName();
  }
}
