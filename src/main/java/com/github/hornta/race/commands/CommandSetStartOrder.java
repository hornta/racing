package com.github.hornta.race.commands;

import com.github.hornta.carbon.ICommandHandler;
import com.github.hornta.race.RacingManager;
import com.github.hornta.race.enums.RaceState;
import com.github.hornta.race.enums.StartOrder;
import com.github.hornta.race.events.RaceChangeStateEvent;
import com.github.hornta.race.MessageKey;
import com.github.hornta.carbon.message.MessageManager;
import com.github.hornta.race.objects.Race;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class CommandSetStartOrder extends RacingCommand implements ICommandHandler {
  public CommandSetStartOrder(RacingManager racingManager) {
    super(racingManager);
  }

  @Override
  public void handle(CommandSender commandSender, String[] args, int typedArgs) {
    Race race = racingManager.getRace(args[0]);
    StartOrder newOrder = StartOrder.fromString(args[1]);

    if(race.getState() != RaceState.UNDER_CONSTRUCTION) {
      MessageManager.setValue("race_name", race.getName());
      MessageManager.sendMessage(commandSender, MessageKey.EDIT_NO_EDIT_MODE);
      return;
    }

    if(race.getStartOrder() == newOrder) {
      MessageManager.setValue("order", newOrder.name());
      MessageManager.sendMessage(commandSender, MessageKey.RACE_SET_START_ORDER_NOCHANGE);
      return;
    }

    StartOrder oldOrder = race.getStartOrder();
    race.setStartOrder(newOrder);

    racingManager.updateRace(race, () -> {
      Bukkit.getPluginManager().callEvent(new RaceChangeStateEvent(race));
      MessageManager.setValue("old_order", oldOrder);
      MessageManager.setValue("new_order", newOrder);
      MessageManager.sendMessage(commandSender, MessageKey.RACE_SET_START_ORDER_SUCCESS);
    });
  }
}
