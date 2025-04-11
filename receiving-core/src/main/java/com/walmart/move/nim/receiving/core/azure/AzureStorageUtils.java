package com.walmart.move.nim.receiving.core.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.google.common.io.ByteSource;
import com.walmart.move.nim.receiving.core.common.AzureBlobConstants;
import com.walmart.move.nim.receiving.core.common.exception.AzureBlobErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.AzureBlobException;
import com.walmart.move.nim.receiving.core.service.BlobStorageException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class AzureStorageUtils {

  @Autowired(required = false)
  @Qualifier(ReceivingConstants.INSTRUCTION_DOWNLOAD_BLOB_SERVICE_CLIENT)
  private BlobServiceClient instructionDownloadBlobServiceClient;

  @Autowired private RetryTemplate retryTemplate;

  private static final Logger LOG = LoggerFactory.getLogger(AzureStorageUtils.class);

  /**
   * Upload to azure blob with given identifier in container
   *
   * @param container
   * @param bytes
   * @param identifier
   * @throws AzureBlobException
   */
  public void uploadWithRetry(String container, byte[] bytes, String identifier)
      throws AzureBlobException {
    retryTemplate.execute(
        upload -> {
          upload(container, bytes, identifier);
          return true;
        });
  }

  /**
   * Upload to azure blob with given identifier in container
   *
   * @param container
   * @param bytes
   * @param identifier
   * @throws AzureBlobException
   */
  public void upload(String container, byte[] bytes, String identifier) throws AzureBlobException {
    BlobClient blobClient =
        instructionDownloadBlobServiceClient
            .getBlobContainerClient(container)
            .getBlobClient(identifier);
    try (InputStream inputStream = ByteSource.wrap(bytes).openStream()) {
      blobClient.upload(inputStream, bytes.length, true);
    } catch (Exception ex) {
      LOG.error(AzureBlobConstants.UPLOAD_ERROR, container, identifier, ex);
      AzureBlobErrorCode errorCode =
          Optional.ofNullable(mapErrorCode(ex)).orElse(AzureBlobErrorCode.UPLOAD_FAILED);
      throw new AzureBlobException(
          errorCode, String.format(AzureBlobConstants.UPLOAD_ERROR_MSG, ex.getMessage()));
    }
  }

  /**
   * Download blob data from Azure Blob for the given container/identifier
   *
   * @param container
   * @param identifier
   * @return
   * @throws AzureBlobException
   */
  public byte[] downloadWithRetry(String container, String identifier) throws AzureBlobException {
    return retryTemplate.execute(
        retryContext -> {
          return download(container, identifier);
        });
  }

  /**
   * Download blob data from Azure Blob for the given container/identifier
   *
   * @param container
   * @param identifier
   * @return
   * @throws AzureBlobException
   */
  public byte[] download(String container, String identifier) throws AzureBlobException {
    BlobClient blobClient =
        instructionDownloadBlobServiceClient
            .getBlobContainerClient(container)
            .getBlobClient(identifier);
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      blobClient.download(outputStream);
      outputStream.flush();
      return outputStream.toByteArray();
    } catch (Exception ex) {
      LOG.error(AzureBlobConstants.DOWNLOAD_ERROR, container, identifier, ex);
      AzureBlobErrorCode errorCode =
          Optional.ofNullable(mapErrorCode(ex)).orElse(AzureBlobErrorCode.DOWNLOAD_FAILED);
      throw new AzureBlobException(
          errorCode, String.format(AzureBlobConstants.DOWNLOAD_ERROR_MSG, ex.getMessage()));
    }
  }

  /**
   * Delete azure blob with given identifier in container
   *
   * @param container
   * @param identifier
   * @throws AzureBlobException
   */
  public void deleteWithRetry(String container, String identifier) throws AzureBlobException {
    retryTemplate.execute(
        delete -> {
          delete(container, identifier);
          return true;
        });
  }

  /**
   * Delete azure blob with given identifier in container
   *
   * @param container
   * @param identifier
   * @throws AzureBlobException
   */
  public void delete(String container, String identifier) throws AzureBlobException {
    BlobClient blobClient =
        instructionDownloadBlobServiceClient
            .getBlobContainerClient(container)
            .getBlobClient(identifier);
    try {
      blobClient.delete();
    } catch (Exception ex) {
      LOG.error(AzureBlobConstants.DELETE_ERROR, container, identifier, ex);
      AzureBlobErrorCode errorCode =
          Optional.ofNullable(mapErrorCode(ex)).orElse(AzureBlobErrorCode.DELETE_FAILED);
      throw new AzureBlobException(
          errorCode, String.format(AzureBlobConstants.DELETE_ERROR_MSG, ex.getMessage()));
    }
  }

  /**
   * @param e
   * @return
   */
  private AzureBlobErrorCode mapErrorCode(Exception e) {
    AzureBlobErrorCode code = null;
    if (e instanceof BlobStorageException) {
      BlobStorageException ex = (BlobStorageException) e;
      String error = Optional.ofNullable(ex.getMessage()).orElse("");
      code =
          error.contains(AzureBlobConstants.BLOB_NOT_FOUND)
              ? AzureBlobErrorCode.BLOB_NOT_FOUND
              : error.contains(AzureBlobConstants.CONTAINER_NOT_FOUND)
                  ? AzureBlobErrorCode.CONTAINER_NOT_FOUND
                  : null;
    }
    return code;
  }
}
