package com.walmart.move.nim.receiving.fixture.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.fixture.model.ItemDTO;
import com.walmart.move.nim.receiving.fixture.service.FixtureItemService;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.fixture.app:false}")
@RestController
@RequestMapping("fixture/item")
public class FixtureItemController {

  @Autowired FixtureItemService fixtureItemService;

  @GetMapping
  @Operation(summary = "Returns all items used in fixture", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32818",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "getAllItemsTimed",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "getAllItems")
  @ExceptionCounted(
      name = "getAllItemsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "getAllItems")
  @Counted(
      name = "getAllItemsCount",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "getAllItems")
  @TimeTracing(component = AppComponent.FIXTURE, type = Type.REST, flow = "getItem")
  public ResponseEntity<List<ItemDTO>> getAllItems(
      @RequestParam(value = "searchString", required = false) String searchString,
      @RequestParam(value = "page", required = false) Integer pageIndex,
      @RequestParam(value = "size", required = false) Integer pageSize) {

    List<ItemDTO> allItems = fixtureItemService.findAllItems(searchString, pageIndex, pageSize);
    if (CollectionUtils.isEmpty(allItems)) {
      return new ResponseEntity<>(allItems, HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(allItems, HttpStatus.OK);
  }

  @PostMapping
  @Operation(summary = "Add item", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32818",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "addItemTimed",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "addItem")
  @ExceptionCounted(
      name = "addItemExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "addItem")
  @Counted(
      name = "addItemCount",
      level1 = "uwms-receiving",
      level2 = "fixtureItemController",
      level3 = "addItem")
  @TimeTracing(component = AppComponent.FIXTURE, type = Type.REST, flow = "addItem")
  public ResponseEntity<Void> addItem(@RequestBody List<ItemDTO> payload) {
    fixtureItemService.addItems(payload);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
