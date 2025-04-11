package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class DateTimeRange {

  @NotNull private Date fromDate;

  @NotNull private Date toDate;
}
