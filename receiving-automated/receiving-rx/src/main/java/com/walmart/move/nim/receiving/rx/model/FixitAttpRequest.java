package com.walmart.move.nim.receiving.rx.model;

import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FixitAttpRequest {
  private List<ManufactureDetail> scannedDataList;

  private ExceptionInfo exceptionInfo;
}
