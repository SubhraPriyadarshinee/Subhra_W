package com.walmart.move.nim.receiving.core.message.common;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Core Model to transfer data b/w receiving server Sample Payload : @<code>
 * {
 *   "name" : "ccm_look_up",
 *   "processor" : "process_executer_name",
 *   "payload" : "stringify_process_payload_goes_here",
 *   "key" : "kafka_key_goes_here",
 *   "additionalAttributes":{
 *
 *    }
 *  }</code>
 */
@Data
@Builder
public class ReceivingEvent {
  /** ccm name of the executor */
  private String name;

  /** Processor Executor bean name . This will take precedence over ccm name */
  private String processor;

  /** Process Payload */
  private String payload;

  /** Key for kafka message (if async enabled) */
  private String key;

  /** Additional Attributes if needed */
  private Map<String, Object> additionalAttributes;
}
