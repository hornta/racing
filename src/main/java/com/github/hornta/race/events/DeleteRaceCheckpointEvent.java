package com.github.hornta.race.events;

import com.github.hornta.race.objects.Race;
import com.github.hornta.race.objects.RaceCheckpoint;
import org.bukkit.event.HandlerList;

public class DeleteRaceCheckpointEvent extends RaceCheckpointEvent {
  private static final HandlerList handlers = new HandlerList();

  public DeleteRaceCheckpointEvent(Race race, RaceCheckpoint checkpoint) {
    super(race, checkpoint);
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }

  @Override
  public HandlerList getHandlers() {
    return handlers;
  }
}

