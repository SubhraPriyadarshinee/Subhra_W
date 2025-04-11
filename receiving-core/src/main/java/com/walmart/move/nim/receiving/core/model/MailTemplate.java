package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Model for Mail template
 *
 * @author pcr000m
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MailTemplate {

  private String reportFromAddress;

  private List<String> reportToAddresses;

  private String mailSubject;

  private Workbook attachment;

  private String mailHtmlTemplate;

  private String mailReportFileName;
}
