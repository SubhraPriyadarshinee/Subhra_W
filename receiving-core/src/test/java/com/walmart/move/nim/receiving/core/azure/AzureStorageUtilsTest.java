package com.walmart.move.nim.receiving.core.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.walmart.move.nim.receiving.core.common.exception.AzureBlobException;
import com.walmart.move.nim.receiving.core.config.RetryConfig;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AzureStorageUtilsTest {

  @Mock private BlobServiceClient blobServiceClient;
  @Mock private BlobContainerClient blobContainerClient;
  @Mock private BlobClient blobClient;

  @InjectMocks private AzureStorageUtils azureStorageUtils;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        azureStorageUtils, "retryTemplate", new RetryConfig().retryTemplate());
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(blobServiceClient, blobContainerClient, blobClient);
  }

  @Test
  public void testUploadToBlobStorageSuccess() throws Exception {
    String container = "teststorage";
    byte blobData[] = getInstructionsDownloadData();
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doNothing()
        .when(blobClient)
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
    azureStorageUtils.uploadWithRetry(container, blobData, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1))
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testUploadToBlobStorageContainerNotFound() throws Exception {
    String container = "teststorage";
    byte blobData[] = getInstructionsDownloadData();
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("ContainerNotFound", null, null))
        .when(blobClient)
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
    azureStorageUtils.uploadWithRetry(container, blobData, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1))
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testUploadToBlobStorageBlobNotFound() throws Exception {
    String container = "teststorage";
    byte blobData[] = getInstructionsDownloadData();
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("BlobNotFound", null, null))
        .when(blobClient)
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
    azureStorageUtils.uploadWithRetry(container, blobData, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1))
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testUploadToBlobStorageUnknownError() throws Exception {
    String container = "teststorage";
    byte blobData[] = getInstructionsDownloadData();
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("Unknown", null, null))
        .when(blobClient)
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
    azureStorageUtils.uploadWithRetry(container, blobData, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1))
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testUploadToBlobStorageException() throws Exception {
    String container = "teststorage";
    byte blobData[] = getInstructionsDownloadData();
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(RuntimeException.class)
        .when(blobClient)
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
    azureStorageUtils.uploadWithRetry(container, blobData, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1))
        .upload(Mockito.any(InputStream.class), Mockito.anyLong(), Mockito.anyBoolean());
  }

  @Test
  public void testDownloadFromBlobStorageSuccess() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doNothing().when(blobClient).download(Mockito.any(ByteArrayOutputStream.class));
    byte blobDataRes[] = azureStorageUtils.downloadWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).download(Mockito.any(ByteArrayOutputStream.class));
    AssertJUnit.assertNotNull(blobDataRes);
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDownloadFromBlobStorageContainerNotFound() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("ContainerNotFound", null, null))
        .when(blobClient)
        .download(Mockito.any(ByteArrayOutputStream.class));
    byte blobDataRes[] = azureStorageUtils.downloadWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).download(Mockito.any(ByteArrayOutputStream.class));
    AssertJUnit.assertNotNull(blobDataRes);
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDownloadFromBlobStorageBlobNotFound() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("BlobNotFound", null, null))
        .when(blobClient)
        .download(Mockito.any(ByteArrayOutputStream.class));
    byte blobDataRes[] = azureStorageUtils.downloadWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).download(Mockito.any(ByteArrayOutputStream.class));
    AssertJUnit.assertNotNull(blobDataRes);
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDownloadFromBlobStorageUnknownError() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("Unknown", null, null))
        .when(blobClient)
        .download(Mockito.any(ByteArrayOutputStream.class));
    byte blobDataRes[] = azureStorageUtils.downloadWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).download(Mockito.any(ByteArrayOutputStream.class));
    AssertJUnit.assertNotNull(blobDataRes);
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDownloadFromBlobStorageException() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(RuntimeException.class)
        .when(blobClient)
        .download(Mockito.any(ByteArrayOutputStream.class));
    byte blobDataRes[] = azureStorageUtils.downloadWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).download(Mockito.any(ByteArrayOutputStream.class));
    AssertJUnit.assertNotNull(blobDataRes);
  }

  @Test
  public void testDeleteFromBlobStorageSuccess() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doNothing().when(blobClient).delete();
    azureStorageUtils.deleteWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).delete();
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDeleteFromBlobStorageContainerNotFound() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("ContainerNotFound", null, null))
        .when(blobClient)
        .delete();
    azureStorageUtils.deleteWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).delete();
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDeleteFromBlobStorageBlobNotFound() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("BlobNotFound", null, null)).when(blobClient).delete();
    azureStorageUtils.deleteWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).delete();
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDeleteFromBlobStorageUnknownError() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(new BlobStorageException("Unknown", null, null)).when(blobClient).delete();
    azureStorageUtils.deleteWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).delete();
  }

  @Test(expectedExceptions = AzureBlobException.class)
  public void testDeleteFromBlobStorageException() throws Exception {
    String container = "teststorage";
    String identifier = "instructions-download.json";
    Mockito.when(blobServiceClient.getBlobContainerClient(container))
        .thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(identifier)).thenReturn(blobClient);
    Mockito.doThrow(RuntimeException.class).when(blobClient).delete();
    azureStorageUtils.deleteWithRetry(container, identifier);
    Mockito.verify(blobServiceClient, Mockito.times(1))
        .getBlobContainerClient(ArgumentMatchers.eq(container));
    Mockito.verify(blobContainerClient, Mockito.times(1))
        .getBlobClient(ArgumentMatchers.eq(identifier));
    Mockito.verify(blobClient, Mockito.times(1)).delete();
  }

  /** @return */
  private byte[] getInstructionsDownloadData() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_CHILD_CONTAINERS
            .getBytes();
    return data;
  }
}
