package com.walmart.move.nim.receiving.core.common;

import org.apache.commons.lang3.EnumUtils;

public enum DocumentType {
  ASN("ASN"),
  MANUAL_BILLING_ASN("MANUAL_BILLING_ASN"),
  CHARGE_ASN("CHARGE_ASN"),
  CREDIT_ASN("CREDIT_ASN");

  private String docType;

  DocumentType(String docType) {
    this.docType = docType;
  }

  public boolean equalsType(String documentType) {
    return this.equals(getDocumentType(documentType));
  }

  public static DocumentType getDocumentType(String value) {
    return EnumUtils.getEnumIgnoreCase(DocumentType.class, value, DocumentType.ASN);
  }

  public String getDocType() {
    return docType;
  }
}
