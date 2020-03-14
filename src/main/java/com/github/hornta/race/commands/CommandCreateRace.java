package com.github.hornta.race.commands;

import com.github.hornta.carbon.ICommandHandler;
import com.github.hornta.race.RacingManager;
import com.github.hornta.race.Util;
import com.github.hornta.race.enums.RaceState;
import com.github.hornta.race.enums.RaceType;
import com.github.hornta.race.enums.RaceVersion;
import com.github.hornta.race.enums.StartOrder;
import com.github.hornta.race.MessageKey;
import com.github.hornta.carbon.message.MessageManager;
import com.github.hornta.race.objects.Race;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

public class CommandCreateRace extends RacingCommand implements ICommandHandler {
  public CommandCreateRace(RacingManager racingManager) {
    super(racingManager);
  }

  @Override
  public void handle(CommandSender commandSender, String[] args, int typedArgs) {
    Player player = (Player) commandSender;

    Race race = new Race(
      UUID.randomUUID(),
      RaceVersion.getLast(),
      args[0],
      Util.centerOnBlockHorizontally(player.getLocation()),
      RaceState.UNDER_CONSTRUCTION,
      Instant.now(),
      Collections.emptyList(),
      Collections.emptyList(),
      RaceType.PLAYER,
      StartOrder.RANDOM,
      null,
      0,
      0.2F,
      new HashSet<>(),
      new HashSet<>(),
      Collections.emptySet(),
      1,
      0.25D,
      0.225D,
      0.7D,
      Collections.emptyList()
    );

    racingManager.createRace(race, () -> {
      MessageManager.setValue("race_name", race.getName());
      MessageManager.sendMessage(player, MessageKey.CREATE_RACE_SUCCESS);
    });
  }
}
