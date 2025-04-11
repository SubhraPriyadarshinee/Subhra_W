package com.walmart.move.nim.receiving.rdc.utils;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

public class MockDeliveryMetaData {

  private static Gson gson = new Gson();

  public static List<DeliveryMetaData> getDeliveryMetaData_ForOSDR() {
    DeliveryMetaData deliveryMetaData1 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333333")
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .unloadingCompleteDate(new Date())
            .build();
    DeliveryMetaData deliveryMetaData2 =
        DeliveryMetaData.builder()
            .deliveryNumber("12333334")
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .unloadingCompleteDate(new Date())
            .osdrLastProcessedDate(new Date())
            .build();
    return Arrays.asList(deliveryMetaData1, deliveryMetaData2);
  }

  public static List<DeliveryMetaData>
      getDeliveryMetaDataForDeliveryStatusCheckAllEligibleDeliveries() throws IOException {
    GdmDeliveryHeaderDetailsPageResponse response =
        getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetAllEligible();
    List<Long> deliveryNumbers =
        response
            .getData()
            .stream()
            .parallel()
            .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
            .collect(Collectors.toList());
    List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    for (Long deliveryNumber : deliveryNumbers) {
      DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
      deliveryMetaData.setDeliveryNumber(deliveryNumber.toString());
      deliveryMetaData.setDeliveryStatus(DeliveryStatus.COMPLETE);
      deliveryMetaDataList.add(deliveryMetaData);
    }
    return deliveryMetaDataList;
  }

  public static List<DeliveryMetaData>
      getDeliveryMetaDataForDeliveryStatusCheckPartiallyEligibleDeliveries() throws IOException {
    GdmDeliveryHeaderDetailsPageResponse response =
        getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetPartiallyEligible();
    List<Long> deliveryNumbers =
        response
            .getData()
            .stream()
            .parallel()
            .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
            .collect(Collectors.toList());
    List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    for (Long deliveryNumber : deliveryNumbers) {
      DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
      deliveryMetaData.setDeliveryNumber(deliveryNumber.toString());
      deliveryMetaData.setDeliveryStatus(DeliveryStatus.COMPLETE);
      deliveryMetaDataList.add(deliveryMetaData);
    }
    return deliveryMetaDataList;
  }

  public static GdmDeliveryHeaderDetailsPageResponse
      getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetAllEligible()
          throws IOException {
    File resource =
        new ClassPathResource(
                "GdmDeliveryHeaderDetailsResponseLessThan100DeliveriesOffsetAllEligible.json")
            .getFile();
    String respone = new String(Files.readAllBytes(resource.toPath()));
    return gson.fromJson(respone, GdmDeliveryHeaderDetailsPageResponse.class);
  }

  public static GdmDeliveryHeaderDetailsPageResponse
      getMockGDMDeliveryHeaderDetailsPageResponseLessThan100DeliveriesOffsetPartiallyEligible()
          throws IOException {
    File resource =
        new ClassPathResource(
                "GdmDeliveryHeaderDetailsResponseLessThan100DeliveriesOffsetPartiallyEligible.json")
            .getFile();
    String respone = new String(Files.readAllBytes(resource.toPath()));
    return gson.fromJson(respone, GdmDeliveryHeaderDetailsPageResponse.class);
  }

  public static List<DeliveryMetaData> getDeliveryNumbersForMoreThan100DeliveriesOffset() {
    List<Long> deliveryNumbers =
        Arrays.asList(
            24285776L, 25967290L, 26613238L, 26659226L, 26763477L, 26768212L, 26855603L, 26917961L,
            26920685L, 26944725L, 26979367L, 27006189L, 27008214L, 27009808L, 27013664L, 27086528L,
            27092297L, 27095273L, 27102576L, 27106148L, 27111297L, 27125580L, 27128663L, 27130752L,
            27139637L, 27159178L, 27163211L, 27164203L, 27190045L, 27197586L, 27202357L, 27203495L,
            27222800L, 27223796L, 27224381L, 27224396L, 27224413L, 27224958L, 27228158L, 27231970L,
            27234383L, 27239351L, 27242733L, 27244132L, 27245014L, 27245145L, 27252884L, 27252964L,
            27253201L, 27253272L, 27257150L, 27259498L, 27263810L, 27263834L, 27264289L, 27265231L,
            27265485L, 27265820L, 27266265L, 27266835L, 27266961L, 27270375L, 27277787L, 27285197L,
            27285309L, 27286208L, 27298421L, 27300191L, 27307963L, 27319037L, 27319141L, 27319157L,
            27319198L, 27319279L, 27319294L, 27319301L, 27319401L, 27320178L, 27320216L, 27320342L,
            27331613L, 27332626L, 27333859L, 27335122L, 27335683L, 27338424L, 27340395L, 27342916L,
            27343188L, 27344017L, 27348391L, 27348567L, 27349100L, 27351166L, 27352577L, 27353160L,
            27353249L, 27353952L, 27354930L, 27355226L, 27356940L, 27357477L, 27362055L, 27363851L,
            27364647L, 27365679L, 27365805L, 27367466L, 27368126L, 27371539L, 27373684L, 27373834L,
            27374735L, 27374856L, 27375238L, 27375847L, 27376281L, 27381103L, 27381216L, 27381607L,
            27382009L, 27382864L, 27384004L, 27387266L, 27387395L, 27387404L, 27388323L, 27388340L,
            27388682L, 27388766L, 27390056L, 27390068L, 27392059L, 27392219L, 27392451L, 27393308L,
            27394754L, 27394930L, 27397926L, 27397951L, 27401809L, 27401867L, 27402567L, 27404293L,
            27404506L, 27408501L, 27409564L, 27409680L, 27409971L, 27409999L, 27410004L, 27410020L,
            27410053L, 27410063L, 27410199L, 27410220L, 27410245L, 27410279L, 27410288L, 27410305L,
            27410309L, 27410310L, 27410333L, 27410345L, 27410352L, 27410363L, 27410421L, 27410518L,
            27410537L, 27410542L, 27410550L, 27410637L, 27410706L, 27411948L, 27412252L, 27412471L,
            27412713L, 27413130L, 27413870L, 27413965L, 27415975L, 27416594L, 27416786L, 27418624L,
            27421413L, 27424843L, 27424909L, 27424962L, 27425903L, 27426034L, 27426630L, 27426800L,
            27427056L, 27428365L, 27428380L, 27429926L, 27430479L, 27434030L, 27434796L, 27435488L,
            27436244L, 27436265L, 27436819L, 27436859L, 27437265L, 27437493L, 27438800L, 27438832L,
            27439372L);
    List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    for (Long deliveryNumber : deliveryNumbers) {
      DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
      deliveryMetaData.setDeliveryNumber(deliveryNumber.toString());
      deliveryMetaData.setDeliveryStatus(DeliveryStatus.COMPLETE);
      deliveryMetaDataList.add(deliveryMetaData);
    }
    return deliveryMetaDataList;
  }
}
