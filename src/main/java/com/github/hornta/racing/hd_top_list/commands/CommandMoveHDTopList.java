package com.github.hornta.racing.hd_top_list.commands;

import com.github.hornta.racing.MessageKey;
import com.github.hornta.racing.RacingPlugin;
import com.github.hornta.racing.hd_top_list.HDTopList;
import com.github.hornta.racing.hd_top_list.HDTopListManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import se.hornta.commando.ICommandHandler;
import se.hornta.messenger.MessageManager;

public class CommandMoveHDTopList implements ICommandHandler {
	@Override
	public void handle(CommandSender commandSender, String[] args, int i) {
		Entity player = (Entity) commandSender;
		HDTopList topList = HDTopListManager.getTopList(args[0]);
		topList.getHologram().teleport(player.getLocation());
		RacingPlugin.getHdTopListManager().updateDirtyTopLists(() -> MessageManager.sendMessage(commandSender, MessageKey.HD_TOP_LIST_MOVE));
	}
}
