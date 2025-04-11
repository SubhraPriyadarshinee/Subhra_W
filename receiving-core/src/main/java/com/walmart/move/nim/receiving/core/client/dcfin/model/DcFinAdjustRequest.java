package com.walmart.move.nim.receiving.core.client.dcfin.model;

import java.util.List;
import lombok.Data;
import lombok.ToString;

/**
 * Request Contract with coma separated in line of below json
 *
 * <pre>
 * ##################################################################################################
 * VTR
 * ##################################################################################################
 * {
 * "transactions":
 * [
 * {
 * key/value,              required,   notes
 * "itemNumber": 552845501,	Y
 * "baseDivCode": "WM",	    Y
 * "primaryQtyUOM": "EA",	Y
 * "inboundChannelMethod": "Staplestock",	Y
 * "dateAdjusted": "2023-02-06T08:58:40.915+0000",	Y	current date
 * "secondaryQty": 4.4,   	Y	fixed with 0 , variable wt actual
 * "weightFormatType": "F",	Y
 * "deliveryNum": "39195748",	Y
 * "primaryQty": 0,	    0	for F, V - all
 * "documentNum": "8472710691",	Y
 * "reasonCode": "28",	    y
 * "containerId": "R08852000020047434",	y
 * "documentLineNo": 7,	    Y
 * "financialReportGrpCode": "US"	Y
 * "secondaryQtyUOM": "LB/ZA",	Y/N	if varaible required
 * "quantityToTransfer": 0,	N
 * "reasonCodeDesc": "Void to reinstate",	N
 * "currencyCode": "USD",	N
 * "vendorPackQty": 6,	    N	if available send
 * "promoBuyInd": "N",	    N
 * "warehousePackQty": 6,	N	if avilable send
 * "documentType": "PO",	N
 * }
 * ],
 * "txnId": "b5c8afed-b5ba-42bb-bb4e-cdeab3c33c8d"	Y
 * }
 *
 * ##################################################################################################
 * Receiving Correction
 * ##################################################################################################
 * curl --location --request POST 'https://dcfinancials.prod.us.walmart.net/v2/adjustment' \
 * --header 'facilityCountryCode: US' \
 * --header 'facilityNum: 8852' \
 * --header 'WMT-API-KEY: 25eacafa-e50c-4172-b7e1-dbc389698809' \
 * --header 'WMT-UserId: t0a057r' \
 * --header 'Content-Type: application/json' \
 * --header 'WMT-correlationId: b05e0763-eaab-42f0-94f8-293d8a0e3025' \
 * --data-raw '{
 * "transactions": [
 * {
 * "itemNumber": 556654314,
 * "promoBuyInd": "N",
 * "secondaryQtyUOM": "LB/ZA",
 * "warehousePackQty": 4,
 * "documentType": "PO",
 * "baseDivCode": "WM",
 * "primaryQtyUOM": "EA",
 * "inboundChannelMethod": "Staplestock",
 * "vendorPackQty": 4,
 * "dateAdjusted": "2023-02-04T15:34:28.604+0000",
 * "secondaryQty": 26.81,
 * "weightFormatType": "F",
 * "quantityToTransfer": 0,
 * "deliveryNum": "39013923",
 * "reasonCodeDesc": "Receiving Correction",
 * "primaryQty": 192,
 * "documentNum": "0731250409",
 * "reasonCode": "52",
 * "containerId": "R08852000020047824",
 * "currencyCode": "USD",
 * "documentLineNo": 8,
 * "financialReportGrpCode": "US"
 * }
 * ],
 * "txnId": "891476e2-926b-4ba9-8911-2e0880ca7b5e"
 * }'
 * </pre>
 */
@Data
@ToString
public class DcFinAdjustRequest {
  private List<TransactionsItem> transactions;
  private String txnId;
}
