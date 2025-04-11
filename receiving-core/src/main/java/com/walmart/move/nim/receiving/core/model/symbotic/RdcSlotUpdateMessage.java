package com.walmart.move.nim.receiving.core.model.symbotic;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdcSlotUpdateMessage extends MessageData {
  private Long itemNbr;
  private String asrsAlignment;
  private String primeSlotId;
  private Date createDate;
  private HttpHeaders httpHeaders;
}
