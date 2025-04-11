package com.walmart.move.nim.receiving.rx.common;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RxLpnUtils {
  private static final Logger logger = LoggerFactory.getLogger(RxLpnUtils.class);

  @Resource(name = RxConstants.RX_LPN_CACHE_SERVICE)
  private LPNCacheService rxLpnCacheService;

  public List<String> get18DigitLPNs(int count, HttpHeaders headers) {
    List<String> lpns = new ArrayList<>();
    try {
      lpns = rxLpnCacheService.getLPNSBasedOnTenant(count, headers);
    } catch (ReceivingException e) {
      logger.error("Error returned from rdcLpnCacheService Error: {}", e.getErrorResponse());
    }
    if (CollectionUtils.isEmpty(lpns)) {
      throw new ReceivingInternalException(
          ReceivingConstants.LPNS_NOT_FOUND,
          String.valueOf(HttpStatus.NOT_FOUND),
          ExceptionCodes.LPNS_NOT_FOUND);
    }
    return lpns;
  }
}
