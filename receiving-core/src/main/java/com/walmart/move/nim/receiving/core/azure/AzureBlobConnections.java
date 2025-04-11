package com.walmart.move.nim.receiving.core.azure;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.walmart.move.nim.receiving.core.common.AzureBlobConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("${enable.instruction.download.blob.client:false}")
public class AzureBlobConnections {

  @ManagedConfiguration private AzureBlobConfig azureBlobConfig;

  @Bean(ReceivingConstants.INSTRUCTION_DOWNLOAD_BLOB_SERVICE_CLIENT)
  public BlobServiceClient instructionDownloadBlobServiceClient() {
    StorageSharedKeyCredential credential =
        new StorageSharedKeyCredential(
            azureBlobConfig.getInstructionDownloadStorageAccountName(),
            azureBlobConfig.getInstructionDownloadStorageAccountKey());
    String endpoint =
        String.format(
            AzureBlobConstants.BLOB_ENDPOINT,
            azureBlobConfig.getInstructionDownloadStorageAccountName());
    return new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).buildClient();
  }
}
