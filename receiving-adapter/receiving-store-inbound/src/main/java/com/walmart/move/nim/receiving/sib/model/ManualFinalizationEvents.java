package com.walmart.move.nim.receiving.sib.model;

import com.walmart.move.nim.receiving.sib.entity.Event;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
@ToString
public class ManualFinalizationEvents extends ApplicationEvent {

  private List<Event> events;

  public ManualFinalizationEvents(Object source, List<Event> events) {
    super(source);
    this.events = events;
  }
}
