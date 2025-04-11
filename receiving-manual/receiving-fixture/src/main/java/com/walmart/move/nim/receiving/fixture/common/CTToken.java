package com.walmart.move.nim.receiving.fixture.common;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class CTToken {
  private String token;
  private Date expires;
}
