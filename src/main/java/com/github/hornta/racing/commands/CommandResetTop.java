package com.github.hornta.racing.commands;

import com.github.hornta.racing.MessageKey;
import com.github.hornta.racing.RacingManager;
import com.github.hornta.racing.events.RaceResetTopEvent;
import com.github.hornta.racing.objects.Race;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import se.hornta.commando.ICommandHandler;
import se.hornta.messenger.MessageManager;

public class CommandResetTop extends RacingCommand implements ICommandHandler {
	public CommandResetTop(RacingManager racingManager) {
		super(racingManager);
	}

	@Override
	public void handle(CommandSender commandSender, String[] args, int typedArgs) {
		Race race = racingManager.getRace(args[0]);
		race.resetResults();
		racingManager.updateRace(race, () -> {
			MessageManager.setValue("race_name", race.getName());
			MessageManager.sendMessage(commandSender, MessageKey.RACE_RESET_TOP);
			Bukkit.getPluginManager().callEvent(new RaceResetTopEvent(race));
		});
	}
}

