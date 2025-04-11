package com.walmart.move.nim.receiving.fixture.service;

import com.walmart.move.nim.receiving.fixture.entity.FixtureItem;
import com.walmart.move.nim.receiving.fixture.model.ItemDTO;
import com.walmart.move.nim.receiving.fixture.repositories.FixtureItemRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class FixtureItemService {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixtureItemService.class);
  @Autowired private FixtureItemRepository fixtureItemRepository;

  public List<ItemDTO> findAllItems(String searchString, Integer pageIndex, Integer pageSize) {

    List<FixtureItem> items = null;
    if (Objects.isNull(pageIndex)) pageIndex = 0;
    if (Objects.isNull(pageSize)) pageSize = 100;
    PageRequest pageRequest = PageRequest.of(pageIndex, pageSize);
    if (StringUtils.isEmpty(searchString)) items = fixtureItemRepository.findAll();
    else items = fixtureItemRepository.findByItemOrDescriptionContaining(searchString, pageRequest);

    List<ItemDTO> allItems = new ArrayList<>();
    items
        .stream()
        .forEach(
            item -> {
              allItems.add(
                  ItemDTO.builder()
                      .itemNumber(item.getItemNumber())
                      .description(item.getDescription())
                      .build());
            });
    return allItems;
  }

  public void addItems(List<ItemDTO> itemDTOList) {
    if (!CollectionUtils.isEmpty(itemDTOList)) {
      Set<FixtureItem> allItems = new HashSet<>();
      itemDTOList
          .stream()
          .forEach(
              itemDTO -> {
                if (Objects.nonNull(itemDTO.getItemNumber()))
                  allItems.add(
                      FixtureItem.builder()
                          .itemNumber(itemDTO.getItemNumber())
                          .description(itemDTO.getDescription())
                          .build());
              });

      if (!CollectionUtils.isEmpty(allItems)) fixtureItemRepository.saveAll(allItems);
    }
  }
}
