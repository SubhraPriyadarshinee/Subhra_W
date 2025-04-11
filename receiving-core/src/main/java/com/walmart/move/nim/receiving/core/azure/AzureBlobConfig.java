package com.walmart.move.nim.receiving.core.azure;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "azureBlobConfig")
@Getter
public class AzureBlobConfig {

  @Property(propertyName = "instruction.download.storage.account.name")
  private String instructionDownloadStorageAccountName;

  @Property(propertyName = "instruction.download.storage.account.key")
  private String instructionDownloadStorageAccountKey;
}
