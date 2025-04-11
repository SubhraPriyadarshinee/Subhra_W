package com.walmart.move.nim.receiving.sib.model;

import com.walmart.move.nim.receiving.sib.entity.Event;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/** POJO for internal application events. Reference - https://www.baeldung.com/spring-events */
@Getter
@Setter
@ToString
public class ContainerApplicationEvents extends ApplicationEvent {
  public ContainerApplicationEvents(Object source, List<Event> events) {
    super(source);
    this.events = events;
  }

  private List<Event> events;
}
