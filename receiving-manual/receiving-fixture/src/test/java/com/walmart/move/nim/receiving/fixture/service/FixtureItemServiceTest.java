package com.walmart.move.nim.receiving.fixture.service;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.fixture.entity.FixtureItem;
import com.walmart.move.nim.receiving.fixture.model.ItemDTO;
import com.walmart.move.nim.receiving.fixture.repositories.FixtureItemRepository;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixtureItemServiceTest extends ReceivingTestBase {

  @InjectMocks private FixtureItemService fixtureItemService;
  @Autowired private FixtureItemRepository fixtureItemRepository;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        fixtureItemService, "fixtureItemRepository", fixtureItemRepository);
  }

  @AfterMethod
  public void tearDown() {
    fixtureItemRepository.deleteAll();
  }

  @Test
  public void testFindAllItems() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> allItems = fixtureItemService.findAllItems(null, null, null);
    assertNotNull(allItems);
    assertEquals(allItems.size(), 1);
    assertEquals(allItems.get(0).getItemNumber(), Long.valueOf(111L));
    assertEquals(allItems.get(0).getDescription(), "pen");
  }

  @Test
  public void testFindAllItemsEmptySearchString() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> allItems = fixtureItemService.findAllItems("", null, null);
    assertNotNull(allItems);
    assertEquals(allItems.size(), 1);
    assertEquals(allItems.get(0).getItemNumber(), Long.valueOf(111L));
    assertEquals(allItems.get(0).getDescription(), "pen");
  }

  @Test
  public void testFindAllItemsWithSearchStrNotPresent() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> itemsWithSearchStr = fixtureItemService.findAllItems("pr", null, null);
    assertEquals(itemsWithSearchStr.size(), 0);
  }

  @Test
  public void testFindAllItemsWithSearchStr() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(112L).description("pencil").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(113L).description("shelf").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> itemsWithSearchStr = fixtureItemService.findAllItems("pe", null, null);
    assertNotNull(itemsWithSearchStr);
    assertEquals(itemsWithSearchStr.size(), 2);
  }

  @Test
  public void testFindAllItemsWithSearchStr2() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen 1100 B").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11002L).description("pencil").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11003L).description("shelf").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> itemsWithSearchStr = fixtureItemService.findAllItems("1100", null, null);
    assertNotNull(itemsWithSearchStr);
    assertEquals(itemsWithSearchStr.size(), 3);
  }

  @Test
  public void testFindAllItemsWithSearchStrPagination() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen 1100 B").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11002L).description("pencil").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11003L).description("shelf").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> itemsWithSearchStr = fixtureItemService.findAllItems("1100", 0, 2);
    assertNotNull(itemsWithSearchStr);
    assertEquals(itemsWithSearchStr.size(), 2);
  }

  @Test
  public void testFindAllItemsWithSearchStrPagination2() {
    FixtureItem item = FixtureItem.builder().itemNumber(111L).description("pen 1100 B").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11002L).description("pencil").build();
    fixtureItemRepository.save(item);
    item = FixtureItem.builder().itemNumber(11003L).description("shelf").build();
    fixtureItemRepository.save(item);
    List<ItemDTO> itemsWithSearchStr = fixtureItemService.findAllItems("1100", 0, 2);
    assertNotNull(itemsWithSearchStr);
    assertEquals(itemsWithSearchStr.size(), 2);
    itemsWithSearchStr = fixtureItemService.findAllItems("1100", 3, 2);
    assertNotNull(itemsWithSearchStr);
    assertEquals(itemsWithSearchStr.size(), 0);
  }

  @Test
  public void testAddItems() {
    List<ItemDTO> items = new ArrayList<>();
    items.add(ItemDTO.builder().itemNumber(111L).description("pen").build());
    items.add(ItemDTO.builder().itemNumber(112L).description("pencil").build());

    fixtureItemService.addItems(items);
    List<FixtureItem> all = fixtureItemRepository.findAll();
    assertNotNull(all);
    assertEquals(all.size(), 2);
  }
}
