package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.model.DotHazardousClass;
import com.walmart.move.nim.receiving.core.model.Mode;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import java.util.ArrayList;
import java.util.List;

public class MockTransportationModes {
  public static List<TransportationModes> getORMD() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    dotHazardousClass.setDescription("Other Regulated Material");

    transportationModes.setDotRegionCode("UN");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public static List<TransportationModes> getValidHazmat() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr("DotRegion");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public static List<TransportationModes> getNoDotNumber() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr(null);

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public static List<TransportationModes> getNotGroundTransport() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(2);
    mode.setDescription("AIR");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public static List<TransportationModes> getNoDotHazardousClass() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr("valid");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(null);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }
}
