package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@Getter
public enum ProducerIdentifier {
  DC_PICKS_IDENTIFIER(11, ReceivingConstants.DC_PICKS),
  DC_RECEIVING_IDENTIFIER(12, ReceivingConstants.DC_RECEIVING),
  DC_VOID_IDENTIFIER(23, ReceivingConstants.DC_VOID),
  DC_TRUE_OUT_IDENTIFIER(23, ReceivingConstants.DC_TRUE_OUT),
  DC_SHIP_VOID_IDENTIFIER(23, ReceivingConstants.DC_SHIP_VOID),
  DC_XDK_VOID_IDENTIFIER(23, ReceivingConstants.DC_XDK_VOID);

  private final int value;
  private final String transformType;

  /**
   * Mapping product identifier
   *
   * @param transformType
   * @return
   */
  public static ProducerIdentifier mapProducerIdentifier(String transformType) {
    return Arrays.stream(ProducerIdentifier.values())
        .filter(
            producerIdentifier ->
                StringUtils.equalsIgnoreCase(producerIdentifier.getTransformType(), transformType))
        .findFirst()
        .orElse(null);
  }
}
