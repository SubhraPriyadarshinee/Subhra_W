package com.walmart.move.nim.receiving.reporting.mock.data;

import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.reporting.model.ReportData;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import java.util.ArrayList;
import java.util.List;

public class MockReportData {

  public static ReportData getMockReportData() {

    List<Pair<String, Object>> statisticsData = new ArrayList<>();
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DELIVERIES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_POS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_USERS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_CASES_RECEIVED_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_ITEMS_RECEIVED_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_LABELS_PRINTED_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_CON_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_CON_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_NON_CON_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DA_NON_CON_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_SSTK_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_SSTK_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PBYL_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PBYL_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_ACL_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_MANUAL_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PO_CON_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PO_CON_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DSDC_PALLETS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DSDC_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_VTR_CONTAINERS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_VTR_CASES_STAT, 1));

    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_PROBLEM_PALLETS_STAT, 2));
    statisticsData.add(
        new Pair<>(
            ReportingConstants.AVERAGE_NUMBER_OF_PALLETS_PER_DELIVERY_STAT,
            2.0d)); // Kept one decimal place after consulting PO
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_DOCK_TAGS_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_SYS_REO_CASES_STAT, 2));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_PALLET_BUILD_TIME_STAT, 50L));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_DELIVERY_COMPLETION_TIME_STAT, 2L));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_DELIVERY_COMPLETION_TIME_STAT, 2L));
    statisticsData.add(
        new Pair<>(ReportingConstants.PERCENTAGE_OF_DELIVERIES_MEETING_LOS_STAT, 100.0D));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_PO_PROCESSING_TIME_STAT, 3L));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_PO_PROCESSING_TIME_STAT, 3L));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_DOOR_OPEN_TIME_STAT, 1L));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_DOOR_OPEN_TIME_STAT, 1L));
    statisticsData.add(new Pair<>(ReportingConstants.AVERAGE_DELIVERY_RECEIVING_TIME_STAT, 1L));
    statisticsData.add(new Pair<>(ReportingConstants.TOTAL_DELIVERY_RECEIVING_TIME_STAT, 1L));
    ReportData reportData = new ReportData();
    reportData.setStatisticsData(statisticsData);
    return reportData;
  }

  public static String expectedMailTemplate =
      "<html><body><p style='text-align:left;font-size:14'>Hi,<br> Please find the weekly report of each DC and the attachment for the same.<h3 style='text-decoration: underline;'>6938:</h3><table border='1px solid black'><tr><th>STATISTIC</th><th>VALUE</th></tr><tr><td>Number of deliveries received against</td><td>2</td></tr><tr><td>Number of POs</td><td>2</td></tr><tr><td>Number of users</td><td>2</td></tr><tr><td>Number of cases received</td><td>2</td></tr><tr><td>Number of items Received</td><td>2</td></tr><tr><td>Number of labels Printed</td><td>2</td></tr><tr><td>Number of DA conveyable pallets</td><td>2</td></tr><tr><td>Number of DA conveyable Cases</td><td>2</td></tr><tr><td>Number of DA non-conveyable pallets</td><td>2</td></tr><tr><td>Number of DA non-conveyable Cases</td><td>2</td></tr><tr><td>Number of SSTK pallets</td><td>2</td></tr><tr><td>Number of SSTK Cases</td><td>2</td></tr><tr><td>Number of PBYL pallets</td><td>2</td></tr><tr><td>Number of PBYL Cases</td><td>2</td></tr><tr><td>Number of cases received through ACL</td><td>2</td></tr><tr><td>Number of cases received manually on conveyor</td><td>2</td></tr><tr><td>Number of PoCon pallets</td><td>2</td></tr><tr><td>Number of PoCon Cases</td><td>2</td></tr><tr><td>Number of DSDC pallets</td><td>2</td></tr><tr><td>Number of DSDC Cases</td><td>2</td></tr><tr><td>Number of labels voided(VTRed)</td><td>2</td></tr><tr><td>Number of Cases voided(VTRed)</td><td>1</td></tr><tr><td>Number of answered problem pallets</td><td>2</td></tr><tr><td>Average number of pallets per delivery</td><td>2.0</td></tr><tr><td>Number of dock tags</td><td>2</td></tr><tr><td>Number of cases received after sys reopen</td><td>2</td></tr><tr><td>Average pallet build time in seconds</td><td>50</td></tr><tr><td>Average LOS in hrs</td><td>2</td></tr><tr><td>Total LOS in hrs</td><td>2</td></tr><tr><td>Percentage of deliveries meeting LOS criteria</td><td>100.0</td></tr><tr><td>Average PO processing time in hrs</td><td>3</td></tr><tr><td>Total PO processing time in hrs</td><td>3</td></tr><tr><td>Average time taken to open a delivery after arrival in hrs</td><td>1</td></tr><tr><td>Total time taken to open a delivery after arrival in hrs</td><td>1</td></tr><tr><td>Average time taken to complete the delivery after door open in hrs</td><td>1</td></tr><tr><td>Total time taken to complete the delivery after door open in hrs</td><td>1</td></tr></table><br><br><h3 style='text-decoration: underline;'>32987:</h3><table border='1px solid black'><tr><th>STATISTIC</th><th>VALUE</th></tr><tr><td>Number of deliveries received against</td><td>2</td></tr><tr><td>Number of POs</td><td>2</td></tr><tr><td>Number of users</td><td>2</td></tr><tr><td>Number of cases received</td><td>2</td></tr><tr><td>Number of items Received</td><td>2</td></tr><tr><td>Number of labels Printed</td><td>2</td></tr><tr><td>Number of DA conveyable pallets</td><td>2</td></tr><tr><td>Number of DA conveyable Cases</td><td>2</td></tr><tr><td>Number of DA non-conveyable pallets</td><td>2</td></tr><tr><td>Number of DA non-conveyable Cases</td><td>2</td></tr><tr><td>Number of SSTK pallets</td><td>2</td></tr><tr><td>Number of SSTK Cases</td><td>2</td></tr><tr><td>Number of PBYL pallets</td><td>2</td></tr><tr><td>Number of PBYL Cases</td><td>2</td></tr><tr><td>Number of cases received through ACL</td><td>2</td></tr><tr><td>Number of cases received manually on conveyor</td><td>2</td></tr><tr><td>Number of PoCon pallets</td><td>2</td></tr><tr><td>Number of PoCon Cases</td><td>2</td></tr><tr><td>Number of DSDC pallets</td><td>2</td></tr><tr><td>Number of DSDC Cases</td><td>2</td></tr><tr><td>Number of labels voided(VTRed)</td><td>2</td></tr><tr><td>Number of Cases voided(VTRed)</td><td>1</td></tr><tr><td>Number of answered problem pallets</td><td>2</td></tr><tr><td>Average number of pallets per delivery</td><td>2.0</td></tr><tr><td>Number of dock tags</td><td>2</td></tr><tr><td>Number of cases received after sys reopen</td><td>2</td></tr><tr><td>Average pallet build time in seconds</td><td>50</td></tr><tr><td>Average LOS in hrs</td><td>2</td></tr><tr><td>Total LOS in hrs</td><td>2</td></tr><tr><td>Percentage of deliveries meeting LOS criteria</td><td>100.0</td></tr><tr><td>Average PO processing time in hrs</td><td>3</td></tr><tr><td>Total PO processing time in hrs</td><td>3</td></tr><tr><td>Average time taken to open a delivery after arrival in hrs</td><td>1</td></tr><tr><td>Total time taken to open a delivery after arrival in hrs</td><td>1</td></tr><tr><td>Average time taken to complete the delivery after door open in hrs</td><td>1</td></tr><tr><td>Total time taken to complete the delivery after door open in hrs</td><td>1</td></tr></table><br><br><h4 style='text-decoration: underline;'> Note: </h4><p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.<p>Thanks,<br>Atlas-receiving team</p></html>";

  public static String expectedMetricReportdMailTemplate =
      "<html><body><p style='text-align:left;font-size:14'>Hi,<br> Please find the pharmacy receiving Metrics Report and the attachment for the same.<table border='1px solid black'><tr><th>STATISTIC</th><th>VALUE</th></tr><tr><td>No of pallets received through Case SSCC Scan</td><td>1</td></tr><tr><td>No of Pallet Label Canceled </td><td>1</td></tr><tr><td>% split of Exempted Items</td><td>1</td></tr><tr><td>Total no of deliveries received through Atlas </td><td>1</td></tr><tr><td>% split of Non Exempted Items</td><td>1</td></tr><tr><td>No of pallets received through Pallet SSCC Scan</td><td>1</td></tr><tr><td>No of Pallets received with Case SSCC + unit 2D Scans</td><td>1</td></tr></table><br><br><h4 style='text-decoration: underline;'> Note: </h4><p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.<p>Thanks,<br>Atlas-receiving team</p></html>";
}
