package com.walmart.move.nim.receiving.core.model.instruction;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;
import org.springframework.http.HttpHeaders;

@Data
@ToString(callSuper = true)
public class InstructionDownloadMessageDTO extends MessageData {

  private Long deliveryNumber;
  private String poNumber;
  private Integer purchaseReferenceLineNumber;
  private Long itemNumber;
  private List<InstructionDownloadBlobStorageDTO> blobStorage;
  private List<String> trackingIds;
  private HttpHeaders httpHeaders;
  private Map<String, InstructionDownloadBlobDataDTO> miscOfflineRcvInfoMap;
  private String messageId;
}
