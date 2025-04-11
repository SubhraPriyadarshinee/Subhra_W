package com.walmart.move.nim.receiving.core.service;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.walmart.move.nim.receiving.core.config.BlobConfig;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.collections.map.HashedMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class BlobFileStorageService {
  private static final Logger LOGGER = LoggerFactory.getLogger(BlobFileStorageService.class);
  @ManagedConfiguration private BlobConfig blobConfig;
  @Autowired private RetryTemplate retryTemplate;

  public enum Container {
    DB_IMPORTS("db-imports"),
    RETURN_ITEM_IMAGES("return-item-images");
    private String name;

    Container(String name) {
      this.name = name;
    }

    public String getName() {
      return this.name;
    }
  };

  public void upload(MultipartFile file, Container container, String path, String fileName) {
    try {
      upload(file.getBytes(), container, path, fileName);
    } catch (IOException exception) {
      log.error("Exception occurred while uploading a file to Azure blob storage", exception);
      throw new BlobStorageException("Upload to Azure blob storage failed! ", exception);
    }
  }

  public void upload(byte[] bytes, Container container, String path, String fileName) {
    try {
      CloudBlobContainer blobContainer = getBlobContainer(container);
      blobContainer.createIfNotExists(
          BlobContainerPublicAccessType.CONTAINER,
          new BlobRequestOptions(),
          new OperationContext());
      log.debug("Uploading file: {} to blob container: {}/{}", fileName, container.name, path);
      blobContainer
          .getBlockBlobReference(path + File.separator + fileName)
          .uploadFromByteArray(bytes, 0, bytes.length);
    } catch (InvalidKeyException | StorageException | URISyntaxException | IOException exception) {
      log.error("Exception occurred while uploading a file to Azure blob storage", exception);
      throw new BlobStorageException("Upload to Azure blob storage failed! ", exception);
    }
  }

  public void uploadWithRetry(byte[] bytes, Container container, String path, String fileName) {
    retryTemplate.execute(
        retryContext -> {
          log.info(
              "Retrying to upload fileName: {} to {} with retry count: {}",
              fileName,
              blobConfig.getConnectionSpec(),
              retryContext.getRetryCount());
          upload(bytes, container, path, fileName);
          return true;
        });
  }

  public byte[] download(Container container, String path, String fileName) {
    try {
      CloudBlobContainer blobContainer = getBlobContainer(container);
      CloudBlockBlob blob = blobContainer.getBlockBlobReference(getFileReference(path, fileName));
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      blob.download(outputStream);
      return outputStream.toByteArray();
    } catch (InvalidKeyException | StorageException | URISyntaxException exception) {
      log.error("Exception occurred while downloading from Azure blob storage", exception);
      throw new BlobStorageException("Download from Azure blob storage failed! ", exception);
    }
  }

  public byte[] downloadWithRetryEnabled(Container container, String path, String fileName) {
    return retryTemplate.execute(
        retryContext -> {
          log.info(
              "Download fileName: {} with retryCount: {} from blob container: {}",
              fileName,
              retryContext.getRetryCount(),
              container.name);
          return download(container, path, fileName);
        });
  }

  Map<Container, CloudBlobContainer> containerMap = new HashedMap();

  private CloudBlobContainer getBlobContainer(Container container)
      throws URISyntaxException, InvalidKeyException, StorageException {
    if (!containerMap.containsKey(container)) {
      containerMap.put(
          container,
          CloudStorageAccount.parse(blobConfig.getConnectionSpec())
              .createCloudBlobClient()
              .getContainerReference(container.name));
    }
    return containerMap.get(container);
  }

  private String getFileReference(final String path, final String fileName) {
    String p = StringUtils.isEmpty(path) ? "" : path;
    p = p.startsWith("/") ? p.substring(1) : p;
    p = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    return p + File.separator + (fileName.startsWith("/") ? fileName.substring(1) : fileName);
  }
}
