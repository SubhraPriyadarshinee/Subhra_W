package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import javax.persistence.Converter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Converter(autoApply = true)
public class ContainerMetaDataForDockTagLabel implements ContainerMetaData {
  private String trackingId;
  private String createUser;
  private Long deliveryNumber;
  private String location;
  @JsonIgnore private Date createTs;
}
