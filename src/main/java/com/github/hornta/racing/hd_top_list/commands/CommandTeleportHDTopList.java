package com.github.hornta.racing.hd_top_list.commands;

import com.github.hornta.racing.hd_top_list.HDTopList;
import com.github.hornta.racing.hd_top_list.HDTopListManager;
import io.papermc.lib.PaperLib;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import se.hornta.commando.ICommandHandler;

public class CommandTeleportHDTopList implements ICommandHandler {
	@Override
	public void handle(CommandSender commandSender, String[] args, int i) {
		HDTopList topList = HDTopListManager.getTopList(args[0]);
		Player player = (Player) commandSender;
		PaperLib.teleportAsync(player, topList.getHologram().getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
	}
}
