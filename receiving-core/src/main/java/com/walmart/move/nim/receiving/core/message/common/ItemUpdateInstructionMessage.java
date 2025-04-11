package com.walmart.move.nim.receiving.core.message.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemUpdateInstructionMessage extends MessageData {
  private Integer itemNumber;
  private List<Long> deliveryNumber;
  private String rejectCode;
  private String catalogGTIN;
}
