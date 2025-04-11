package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class PalletHistory {
  private static final Logger LOG = LoggerFactory.getLogger(PalletHistory.class);
  private String trackingId;
  private Long itemNumber;
  private int quantity;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date rotateDate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date createdTimeStamp;

  @JsonDeserialize(using = MapDeserializer.class)
  private Object destination;
}
