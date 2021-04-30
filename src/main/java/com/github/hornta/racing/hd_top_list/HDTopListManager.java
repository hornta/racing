package com.github.hornta.racing.hd_top_list;

import com.github.hornta.racing.ConfigKey;
import com.github.hornta.racing.MessageKey;
import com.github.hornta.racing.RacingPlugin;
import com.github.hornta.racing.Util;
import com.github.hornta.racing.enums.RaceStatType;
import com.github.hornta.racing.events.ConfigReloadedEvent;
import com.github.hornta.racing.events.DeleteRaceEvent;
import com.github.hornta.racing.events.RaceChangeNameEvent;
import com.github.hornta.racing.events.RaceResetTopEvent;
import com.github.hornta.racing.events.RaceResultUpdatedEvent;
import com.github.hornta.racing.events.RacesLoadedEvent;
import com.github.hornta.racing.events.UnloadRaceEvent;
import com.github.hornta.racing.objects.Race;
import com.github.hornta.racing.objects.RaceCheckpoint;
import com.github.hornta.racing.objects.RacePlayerStatistic;
import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import se.hornta.messenger.MessageManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HDTopListManager implements Listener {
	private static HDTopListManager instance;
	private final List<HDTopList> topLists;
	private final FileStorage storage;

	public HDTopListManager() {
		instance = this;
		topLists = new ArrayList<>();
		storage = new FileStorage(RacingPlugin.getInstance());
	}

	public static void createTopList(String name, Location location, Race race, RaceStatType statType, int laps, Consumer<Boolean> callback) {
		instance.createTopListInternal(name, location, race, statType, laps, callback);
	}

	public static void deleteTopList(String name, Consumer<Boolean> callback) {
		instance.deleteTopListInternal(name, callback);
	}

	public static List<HDTopList> getTopLists() {
		return instance.topLists;
	}

	public static HDTopList getTopList(String name) {
		for (HDTopList topList : instance.topLists) {
			if (topList.getName().equals(name)) {
				return topList;
			}
		}
		return null;
	}

	private static String getFormattedPlayerName(RacePlayerStatistic racePlayerStatistic) {
		Chat vaultChat = RacingPlugin.getInstance().getVaultChat();
		if (vaultChat != null) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(racePlayerStatistic.getPlayerId());
			String prefix = ChatColor.translateAlternateColorCodes('&', vaultChat.getPlayerPrefix(null, offlinePlayer));
			String suffix = ChatColor.translateAlternateColorCodes('&', vaultChat.getPlayerSuffix(null, offlinePlayer));
			return prefix + racePlayerStatistic.getPlayerName() + suffix;
		} else {
			return racePlayerStatistic.getPlayerName();
		}
	}

	private static void updateText(HDTopList topList, List<String> playerNames) {
		Hologram hologram = topList.getHologram();
		hologram.clearLines();
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.HD_TOP_LIST_SHOW_HEADER)) {
			MessageManager.setValue("stat_type", topList.getStatType().getFormattedStat(topList.getLaps()));
			MessageManager.setValue("race_name", topList.getRace().getName());
			String header = MessageManager.getMessage(MessageKey.HD_TOP_LIST_HEADER);
			for (String part : header.split("\n")) {
				hologram.appendTextLine(part);
			}
		}
		int p = 1;
		Set<RacePlayerStatistic> stats = topList.getRace().getResults(topList.getStatType(), topList.getLaps());
		int playerNameIndex = 0;
		for (RacePlayerStatistic s : stats) {
			MessageManager.setValue("position", p++);
			MessageManager.setValue("player_name", playerNames.get(playerNameIndex));
			playerNameIndex += 1;
			Util.setTimeUnitValues();
			String value = s.getStatValue(topList.getStatType(), topList.getLaps());
			MessageManager.setValue("value", value);
			String item = MessageManager.getMessage(MessageKey.HD_TOP_LIST_ITEM);
			for (String part : item.split("\n")) {
				hologram.appendTextLine(part);
			}
			if (p > 10) {
				break;
			}
		}
		for (int i = p; i <= 10; ++i) {
			MessageManager.setValue("position", i);
			String none = MessageManager.getMessage(MessageKey.HD_TOP_LIST_NONE);
			for (String part : none.split("\n")) {
				hologram.appendTextLine(part);
			}
		}
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.HD_TOP_LIST_SHOW_FOOTER)) {
			String footer = MessageManager.getMessage(MessageKey.HD_TOP_LIST_FOOTER);
			for (String part : footer.split("\n")) {
				hologram.appendTextLine(part);
			}
		}
	}

	private void updateDirtyTopLists() {
		updateDirtyTopLists(null);
	}

	public void updateDirtyTopLists(Runnable runnable) {
		List<HDTopList> dirtyTopLists = topLists.stream().filter(HDTopList::isDirty).collect(Collectors.toList());
		updateToplists(dirtyTopLists);
		for (HDTopList topList : dirtyTopLists) {
			storage.write(topList, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
				if (result) {
					topList.setDirty(false);
					if (runnable != null) {
						runnable.run();
					}
				}
			}));
		}
	}

	@EventHandler
	void onRaceResetTop(RaceResetTopEvent event) {
		updateToplists(topLists.stream().filter((topList -> topList.getRace() == event.getRace())).collect(Collectors.toList()));
	}

	@EventHandler
	void onRacesLoaded(RacesLoadedEvent event) {
		storage.load((topLists) -> {
			this.topLists.addAll(topLists);
			updateToplists(topLists);
		});
	}

	@EventHandler
	void onConfigReloaded(ConfigReloadedEvent event) {
		updateToplists(topLists);
	}

	@EventHandler
	void onRaceChangeName(RaceChangeNameEvent event) {
		updateToplists(topLists.stream().filter((hdTopList -> event.getRace() == hdTopList.getRace())).collect(Collectors.toList()));
	}

	@EventHandler
	void onDeleteRace(DeleteRaceEvent event) {
		Collection<HDTopList> toRemove = new ArrayList<>();
		for (HDTopList topList : topLists) {
			if (event.getRace() == topList.getRace()) {
				toRemove.add(topList);
			}
		}
		for (HDTopList topList : toRemove) {
			deleteTopList(topList);
		}
	}

	@EventHandler
	void onUnloadRace(UnloadRaceEvent event) {
		for (HDTopList topList : topLists) {
			if (event.getRace() == topList.getRace()) {
				topList.getHologram().delete();
			}
		}
		topLists.removeIf((HDTopList l) -> l.getHologram().isDeleted());
	}

	@EventHandler
	void onRaceResultUpdatedEvent(RaceResultUpdatedEvent event) {
		updateToplists(topLists.stream().filter((hdTopList -> event.getRace() == hdTopList.getRace())).collect(Collectors.toList()));
	}

	private void createTopListInternal(String name, Location location, Race race, RaceStatType statType, int laps, Consumer<Boolean> callback) {
		Hologram hologram = HologramsAPI.createHologram(RacingPlugin.getInstance(), location);
		HDTopList hdTopList = new HDTopList(UUID.randomUUID(), HDTopListVersion.getLast(), name, hologram, race, statType, laps);
		storage.write(hdTopList, (Boolean result) -> {
			if (result) {
				topLists.add(hdTopList);
				updateDirtyTopLists();
			}
			callback.accept(result);
		});
	}

	private void updateToplists(Iterable<HDTopList> topLists) {
		CompletableFuture.runAsync(() -> {
			Map<HDTopList, List<String>> names = new HashMap<>();
			for (HDTopList topList : topLists) {
				Set<RacePlayerStatistic> stats = topList.getRace().getResults(topList.getStatType(), topList.getLaps());
				List<String> topListUsernames = new ArrayList<>();
				for (RacePlayerStatistic stat : stats) {
					topListUsernames.add(getFormattedPlayerName(stat));
				}
				names.put(topList, topListUsernames);
			}
			new BukkitRunnable() {
				@Override
				public void run() {
					for (Map.Entry<HDTopList, List<String>> entry : names.entrySet()) {
						updateText(entry.getKey(), entry.getValue());
					}
				}
			}.runTask(RacingPlugin.getInstance());
		});
	}

	private void deleteTopList(HDTopList topList) {
		deleteTopList(topList, null);
	}

	private void deleteTopList(HDTopList topList, Consumer<Boolean> callback) {
		storage.delete(topList, (Boolean result) -> {
			if (result) {
				topList.getHologram().delete();
				topLists.remove(topList);
			}
			if (callback != null) {
				callback.accept(result);
			}
		});
	}

	private void deleteTopListInternal(String name, Consumer<Boolean> callback) {
		for (HDTopList topList : topLists) {
			if (topList.getName().equals(name)) {
				deleteTopList(topList, callback);
				break;
			}
		}
	}
}
