package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class AuthenticateRequest {
  @NotNull private String userId;
  @NotNull private String password;
  @NotNull private String businessUnitId;
  @NotNull private String domainName;
}
