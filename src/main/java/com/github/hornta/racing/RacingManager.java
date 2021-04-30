package com.github.hornta.racing;

import com.comphenix.protocol.ProtocolLibrary;
import com.github.hornta.racing.api.RacingAPI;
import com.github.hornta.racing.enums.*;
import com.github.hornta.racing.events.*;
import com.github.hornta.racing.features.*;
import com.github.hornta.racing.objects.*;
import io.papermc.lib.PaperLib;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;
import se.hornta.messenger.MessageManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RacingManager implements Listener {
	private final Map<String, Race> racesByName = new HashMap<>();
	private final Map<UUID, Race> racesById = new HashMap<>();
	private final List<Race> races = new ArrayList<>();
	private final List<RaceSession> raceSessions = new ArrayList<>();
	private RacingAPI api;

	RacingManager() {
		PluginManager pluginManager = RacingPlugin.getInstance().getServer().getPluginManager();
		pluginManager.registerEvents(new TeleportOnLeave(), RacingPlugin.getInstance());
		pluginManager.registerEvents(new AllowTeleport(), RacingPlugin.getInstance());
		pluginManager.registerEvents(new FoodLevel(), RacingPlugin.getInstance());
		pluginManager.registerEvents(new DamageParticipants(), RacingPlugin.getInstance());
		ProtocolLibrary.getProtocolManager().addPacketListener(new Remount());
	}

	public void shutdown() {
		for (RaceSession raceSession : raceSessions) {
			raceSession.stop();
		}
		raceSessions.clear();
	}

	public void startNewSession(CommandSender initiator, Race race, int laps) {
		RaceSession raceSession = new RaceSession(initiator, race, laps);
		raceSessions.add(raceSession);
		raceSession.start();
		Bukkit.getPluginManager().callEvent(new RaceSessionStartEvent(raceSession));
	}

	public List<RaceSession> getRaceSessions() {
		return new ArrayList<>(raceSessions);
	}

	public List<RaceSession> getRaceSessions(Race race) {
		return getRaceSessions(race, null);
	}

	public List<RaceSession> getRaceSessions(Race race, RaceSessionState state) {
		List<RaceSession> sessions = new ArrayList<>();
		for (RaceSession session : raceSessions) {
			boolean stateOk = state == null || session.getState() == state;
			if (session.getRace() == race && stateOk) {
				sessions.add(session);
			}
		}
		return sessions;
	}

	public boolean hasOngoingSession(Race race) {
		for (RaceSession session : raceSessions) {
			if (session.getRace() == race) {
				return true;
			}
		}
		return false;
	}

	@EventHandler
	void onLoadRace(LoadRaceEvent event) {
		if (event.getRace().getState() != RaceState.UNDER_CONSTRUCTION) {
			return;
		}
		addChunkTickets();
		for (RaceCheckpoint checkpoint : event.getRace().getCheckpoints()) {
			checkpoint.startTask(true);
			checkpoint.setupHologram();
		}
		for (RaceStartPoint startPoint : event.getRace().getStartPoints()) {
			startPoint.setupHologram();
		}
	}

	@EventHandler
	void onUnloadRace(UnloadRaceEvent event) {
		for (RaceCheckpoint checkpoint : event.getRace().getCheckpoints()) {
			checkpoint.stopTask();
			checkpoint.removeHologram();
		}
		for (RaceStartPoint startPoint : event.getRace().getStartPoints()) {
			startPoint.removeHologram();
		}
		addChunkTickets();
	}

	@EventHandler
	void onAddRaceCheckpoint(AddRaceCheckpointEvent event) {
		event.getCheckpoint().startTask(true);
		event.getCheckpoint().setupHologram();
		for (RaceCheckpoint cp : event.getRace().getCheckpoints()) {
			if (cp.getPosition() > event.getCheckpoint().getPosition()) {
				cp.removeHologram();
				cp.setupHologram();
			}
		}
		addChunkTickets();
	}

	@EventHandler
	void onDeleteRaceCheckpoint(DeleteRaceCheckpointEvent event) {
		event.getCheckpoint().stopTask();
		event.getCheckpoint().removeHologram();
		for (RaceCheckpoint checkpoint : event.getRace().getCheckpoints()) {
			if (checkpoint.getPosition() >= event.getCheckpoint().getPosition() && checkpoint.getHologram() != null) {
				checkpoint.removeHologram();
				checkpoint.setupHologram();
			}
		}
		addChunkTickets();
	}

	@EventHandler
	void onAddRaceStartPoint(AddRaceStartPointEvent event) {
		event.getStartPoint().setupHologram();
		for (RaceStartPoint cp : event.getRace().getStartPoints()) {
			if (cp.getPosition() > event.getStartPoint().getPosition()) {
				cp.removeHologram();
				cp.setupHologram();
			}
		}
		addChunkTickets();
	}

	@EventHandler
	void onDeleteRaceStartPoint(DeleteRaceStartPointEvent event) {
		event.getStartPoint().removeHologram();
		for (RaceStartPoint startPoint : event.getRace().getStartPoints()) {
			if (startPoint.getPosition() >= event.getStartPoint().getPosition() && startPoint.getHologram() != null) {
				startPoint.removeHologram();
				startPoint.setupHologram();
			}
		}
		addChunkTickets();
	}

	@EventHandler
	void onRaceChangeState(RaceChangeStateEvent event) {
		for (RaceCheckpoint checkpoint : event.getRace().getCheckpoints()) {
			if (event.getRace().getState() == RaceState.UNDER_CONSTRUCTION) {
				checkpoint.startTask(true);
				checkpoint.setupHologram();
			} else {
				checkpoint.stopTask();
				checkpoint.removeHologram();
			}
		}
		if (RacingPlugin.getInstance().isHolographicDisplaysLoaded()) {
			for (RaceStartPoint startPoint : event.getRace().getStartPoints()) {
				if (event.getRace().getState() == RaceState.UNDER_CONSTRUCTION) {
					startPoint.setupHologram();
				} else {
					startPoint.removeHologram();
				}
			}
		}
		addChunkTickets();
	}

	@EventHandler
	void onChangeRaceName(RaceChangeNameEvent event) {
		racesByName.remove(event.getOldName());
		racesByName.put(event.getRace().getName(), event.getRace());
	}

	@EventHandler
	void onRaceSessionResult(RaceSessionResultEvent event) {
		List<PlayerSessionResult> sortedPlayerResults = event.getResult().getPlayerResults().values().stream().sorted(Comparator.comparingLong(PlayerSessionResult::getRaceDuration)).collect(Collectors.toList());
		Collection<String> resultMessages = new ArrayList<>();
		Race race = event.getResult().getRaceSession().getRace();
		MessageManager.setValue("race_name", race.getName());
		String headerMessage = MessageManager.getMessage(MessageKey.RACE_RESULT_HEADER);
		resultMessages.add(headerMessage);
		int currentPosition = 1;
		for (PlayerSessionResult playerResult : sortedPlayerResults) {
			if (currentPosition <= 10) {
				String message;
				String playerName = Bukkit.getOfflinePlayer(playerResult.getPlayerId()).getName();
				if (playerResult.hasCompletedRace()) {
					MessageManager.setValue("position", currentPosition);
					currentPosition += 1;
					MessageManager.setValue("player_name", playerName);
					MessageManager.setValue("time", Util.getTimeLeft(playerResult.getRaceDuration()));
					Util.setTimeUnitValues();
					message = MessageManager.getMessage(MessageKey.RACE_PARTICIPANT_RESULT);
				} else {
					MessageManager.setValue("player_name", playerName);
					message = MessageManager.getMessage(MessageKey.RACE_PARTICIPANT_RESULT_NO_COMPLETE);
				}
				resultMessages.add(message);
			}
		}
		String joinedResultMessage = String.join("\n", resultMessages);
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.BROADCAST_RESULT_MESSAGE)) {
			Bukkit.broadcastMessage(joinedResultMessage);
		} else {
			List<Player> playersToMessage = event.getResult().getPlayerResults().values().stream().map((PlayerSessionResult playerResult) -> Bukkit.getPlayer(playerResult.getPlayerId())).filter(Objects::nonNull).collect(Collectors.toList());
			for (Player player : playersToMessage) {
				player.sendMessage(joinedResultMessage);
			}
		}
		race.addResult(event.getResult());
		updateRace(event.getResult().getRaceSession().getRace(), () -> Bukkit.getPluginManager().callEvent(new RaceResultUpdatedEvent(event.getResult().getRaceSession().getRace())));
	}

	@EventHandler
	void onRaceSessionStop(RaceSessionStopEvent event) {
		raceSessions.remove(event.getRaceSession());
		if (RacingPlugin.getInstance().getConfiguration().<Boolean>get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED)) {
			String whenString = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.TELEPORT_AFTER_RACE_WHEN);
			TeleportAfterRaceWhen when = TeleportAfterRaceWhen.valueOf(whenString.toUpperCase(Locale.ENGLISH));
			if (when == TeleportAfterRaceWhen.EVERYONE_FINISHES) {
				for (RacePlayerSession playerSession : event.getRaceSession().getPlayerSessions()) {
					PaperLib.teleportAsync(playerSession.getPlayer(), event.getRaceSession().getRace().getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
				}
			}
		}
	}

	@EventHandler
	void onPlayerJoin(PlayerJoinEvent event) {
		boolean hasPermission = event.getPlayer().hasPermission(Permission.RACING_MODIFY.toString());
		for (Race race : races) {
			for (RaceCheckpoint checkpoint : race.getCheckpoints()) {
				if (checkpoint.getHologram() != null) {
					if (hasPermission) {
						checkpoint.getHologram().getVisibilityManager().showTo(event.getPlayer());
					} else {
						checkpoint.getHologram().getVisibilityManager().hideTo(event.getPlayer());
					}
				}
			}
			for (RaceStartPoint startPoint : race.getStartPoints()) {
				if (startPoint.getHologram() != null) {
					if (hasPermission) {
						startPoint.getHologram().getVisibilityManager().showTo(event.getPlayer());
					} else {
						startPoint.getHologram().getVisibilityManager().hideTo(event.getPlayer());
					}
				}
			}
		}
	}

	@EventHandler
	void onPlayerQuit(PlayerQuitEvent event) {
		for (Race race : races) {
			for (RaceCheckpoint checkpoint : race.getCheckpoints()) {
				if (checkpoint.getHologram() != null) {
					checkpoint.getHologram().getVisibilityManager().resetVisibility(event.getPlayer());
				}
			}
			for (RaceStartPoint startPoint : race.getStartPoints()) {
				if (startPoint.getHologram() != null) {
					startPoint.getHologram().getVisibilityManager().resetVisibility(event.getPlayer());
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		RaceSession session = getParticipatingRace(event.getPlayer());
		if (session == null || session.getState() == RaceSessionState.PREPARING) {
			return;
		}
		List<String> blockedCommands = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.BLOCKED_COMMANDS);
		String entryLabel = event.getMessage().split(" ")[0].replace("/", "");
		String[] entryArgs = event.getMessage().replace("/" + entryLabel, "").trim().split(" ");
		for (String blockedCommand : blockedCommands) {
			String blockedLabel = blockedCommand.split(" ")[0].replace("/", "");
			if (blockedLabel.equalsIgnoreCase(entryLabel)) {
				boolean skip = false;
				String argString = blockedCommand.replace(blockedLabel, "").trim();
				if (!argString.isEmpty()) {
					String[] blockedArgs = argString.split(" ");
					for (int i = 0; i < blockedArgs.length; ++i) {
						// if entry arg doesnt exist or if entry args isn't equal to the blocked arg
						// then continue with next blocked cmd
						if (i >= entryArgs.length || !blockedArgs[i].equalsIgnoreCase(entryArgs[i])) {
							skip = true;
							break;
						}
					}
				}
				if (skip) {
					continue;
				}
				event.setCancelled(true);
				MessageManager.setValue("command", "/" + blockedCommand);
				MessageManager.sendMessage(event.getPlayer(), MessageKey.BLOCKED_CMDS);
				return;
			}
		}
	}

	@EventHandler
	void onRacePlayerGoal(RacePlayerGoalEvent event) {
		if (RacingPlugin.getInstance().getConfiguration().<Boolean>get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED)) {
			String whenString = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.TELEPORT_AFTER_RACE_WHEN);
			TeleportAfterRaceWhen when = TeleportAfterRaceWhen.valueOf(whenString.toUpperCase(Locale.ENGLISH));
			if (when == TeleportAfterRaceWhen.PARTICIPANT_FINISHES) {
				PaperLib.teleportAsync(event.getPlayerSession().getPlayer(), event.getRaceSession().getRace().getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
			}
		}
	}

	@EventHandler
	void onSessionStateChanged(SessionStateChangedEvent event) {
		if (event.getRaceSession().getState() == RaceSessionState.COUNTDOWN) {
			Bukkit.getPluginManager().callEvent(new ExecuteCommandEvent(RaceCommandType.ON_COUNTDOWN, event.getRaceSession()));
		} else if (event.getRaceSession().getState() == RaceSessionState.STARTED) {
			Bukkit.getPluginManager().callEvent(new ExecuteCommandEvent(RaceCommandType.ON_START, event.getRaceSession()));
		}
	}

	public void setAPI(RacingAPI api) {
		this.api = api;
	}

	public void load() {
		if (!raceSessions.isEmpty()) {
			throw new RuntimeException("Can't load races because there are ongoing race sessions.");
		}
		for (Race race : races) {
			Bukkit.getPluginManager().callEvent(new UnloadRaceEvent(race));
		}
		racesByName.clear();
		racesById.clear();
		races.clear();
		api.fetchAllRaces((List<Race> fetchedRaces) -> {
			for (Race race : fetchedRaces) {
				racesByName.put(race.getName(), race);
				racesById.put(race.getId(), race);
				races.add(race);
				Bukkit.getPluginManager().callEvent(new LoadRaceEvent(race));
			}
			Bukkit.getPluginManager().callEvent(new RacesLoadedEvent());
		});
	}

	public void updateRace(Race race, Runnable runnable) {
		api.updateRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				runnable.run();
			}
		}));
	}

	public void addCheckpoint(Location location, Race race, int position, Consumer<RaceCheckpoint> consumer) {
		RaceCheckpoint checkpoint = new RaceCheckpoint(UUID.randomUUID(), position, location, 3);
		List<RaceCheckpoint> checkpoints = race.getCheckpoints();
		checkpoints.add(checkpoint.getPosition() - 1, checkpoint);
		for (int i = checkpoint.getPosition(); i < checkpoints.size(); ++i) {
			int newPos = checkpoints.get(i).getPosition() + 1;
			checkpoints.get(i).setPosition(newPos);
		}
		api.updateCheckpoints(race.getId(), checkpoints, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				race.setCheckpoints(checkpoints);
				Bukkit.getPluginManager().callEvent(new AddRaceCheckpointEvent(race, checkpoint));
				consumer.accept(checkpoint);
			}
		}));
	}

	public void deleteCheckpoint(Race race, RaceCheckpoint checkpoint, Runnable runnable) {
		List<RaceCheckpoint> checkpoints = race.getCheckpoints();
		int removedIndex = checkpoints.indexOf(checkpoint);
		for (int i = removedIndex + 1; i < checkpoints.size(); ++i) {
			checkpoints.get(i).setPosition(checkpoints.get(i).getPosition() - 1);
		}
		checkpoints.remove(removedIndex);
		api.updateCheckpoints(race.getId(), checkpoints, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				race.setCheckpoints(checkpoints);
				Bukkit.getPluginManager().callEvent(new DeleteRaceCheckpointEvent(race, checkpoint));
				runnable.run();
			}
		}));
	}

	public void moveCheckpoint(Location location, Race race, RaceCheckpoint checkpoint, Runnable runnable) {
		checkpoint.setLocation(location);
		api.updateCheckpoints(race.getId(), race.getCheckpoints(), (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				runnable.run();
			}
		}));
	}

	public void moveStartPoint(Location location, Race race, RaceStartPoint startPoint, Runnable runnable) {
		startPoint.setLocation(location);
		api.updateStartPoints(race.getId(), race.getStartPoints(), (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				runnable.run();
			}
		}));
	}

	public void createRace(Race race, Runnable callback) {
		api.updateRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				racesByName.put(race.getName(), race);
				racesById.put(race.getId(), race);
				races.add(race);
				Bukkit.getPluginManager().callEvent(new CreateRaceEvent(race));
				Bukkit.getPluginManager().callEvent(new LoadRaceEvent(race));
				callback.run();
			}
		}));
	}

	public void deleteRace(Race race, Runnable runnable) {
		api.deleteRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				racesByName.remove(race.getName());
				racesById.remove(race.getId());
				races.remove(race);
				Bukkit.getPluginManager().callEvent(new DeleteRaceEvent(race));
				Bukkit.getPluginManager().callEvent(new UnloadRaceEvent(race));
				runnable.run();
			}
		}));
	}

	public void addStartPoint(Location location, Race race, int position, Consumer<RaceStartPoint> consumer) {
		RaceStartPoint startPoint = new RaceStartPoint(UUID.randomUUID(), position, location);
		List<RaceStartPoint> startPoints = race.getStartPoints();
		startPoints.add(startPoint.getPosition() - 1, startPoint);
		for (int i = startPoint.getPosition(); i < startPoints.size(); ++i) {
			int newPos = startPoints.get(i).getPosition() + 1;
			startPoints.get(i).setPosition(newPos);
		}
		api.updateStartPoints(race.getId(), startPoints, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				race.setStartPoints(startPoints);
				//race.addStartPoint(startPoint);
				Bukkit.getPluginManager().callEvent(new AddRaceStartPointEvent(race, startPoint));
				consumer.accept(startPoint);
			}
		}));
	}

	public void deleteStartPoint(Race race, RaceStartPoint startPoint, Runnable runnable) {
		List<RaceStartPoint> startPoints = race.getStartPoints();
		int removedIndex = startPoints.indexOf(startPoint);
		for (int i = removedIndex + 1; i < startPoints.size(); ++i) {
			startPoints.get(i).setPosition(startPoints.get(i).getPosition() - 1);
		}
		startPoints.remove(removedIndex);
		api.updateStartPoints(race.getId(), startPoints, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			if (result) {
				race.setStartPoints(startPoints);
				Bukkit.getPluginManager().callEvent(new DeleteRaceStartPointEvent(race, startPoint));
				runnable.run();
			}
		}));
	}

	public Race getRace(String name) {
		return racesByName.get(name);
	}

	public Race getRace(UUID id) {
		return racesById.get(id);
	}

	public List<Race> getRaces() {
		return new ArrayList<>(races);
	}

	@Nullable
	public RaceSession getParticipatingRace(Player player) {
		for (RaceSession session : raceSessions) {
			if (session.isParticipating(player)) {
				return session;
			}
		}
		return null;
	}

	public void joinRace(Race race, Player player, JoinType type) {
		joinRace(race, player, type, 1);
	}

	public void joinRace(Race race, Player player, JoinType type, int laps) {
		List<RaceSession> sessions = getRaceSessions(race);
		RaceSession session = null;
		if (!sessions.isEmpty()) {
			session = sessions.get(0);
		}
		if (getNonJoinableGameModes().contains(player.getGameMode())) {
			MessageManager.setValue("game_mode", player.getGameMode());
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_GAME_MODE);
			return;
		}
		if (getNonJoinableWorlds().contains(player.getWorld())) {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_WORLD);
			return;
		}
		boolean startOnSign = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.START_ON_JOIN_SIGN);
		boolean startOnCommand = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.START_ON_JOIN_COMMAND);
		if (session == null && ((type == JoinType.SIGN && startOnSign) || (type == JoinType.COMMAND && startOnCommand))) {
			StartRaceStatus status = tryStartRace(race.getName(), player, laps);
			if (status == StartRaceStatus.ERROR) {
				return;
			}
			session = getRaceSessions(race).get(0);
		}
		if (session == null || session.getState() != RaceSessionState.PREPARING) {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_NOT_OPEN);
			return;
		}
		if (session.isParticipating(player)) {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_IS_PARTICIPATING);
			return;
		}
		if (getParticipatingRace(player) != null) {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_IS_PARTICIPATING_OTHER);
			return;
		}
		if (session.isFull()) {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_IS_FULL);
			return;
		}
		Economy economy = RacingPlugin.getInstance().getVaultEconomy();
		if (economy != null && race.getEntryFee() > 0) {
			if (economy.getBalance(player) < race.getEntryFee()) {
				MessageManager.setValue("entry_fee", economy.format(race.getEntryFee()));
				MessageManager.setValue("balance", economy.format(economy.getBalance(player)));
				MessageManager.sendMessage(player, MessageKey.JOIN_RACE_NOT_AFFORD);
				return;
			}
			economy.withdrawPlayer(player, race.getEntryFee());
			MessageManager.setValue("fee", economy.format(race.getEntryFee()));
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_CHARGED);
		}
		session.participate(player, race.getEntryFee());
		MessageManager.setValue("player_name", player.getName());
		MessageManager.setValue("race_name", race.getName());
		MessageManager.setValue("current_participants", session.getAmountOfParticipants());
		MessageManager.setValue("max_participants", race.getStartPoints().size());
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.BROADCAST_PLAYER_JOIN_MESSAGE)) {
			MessageManager.broadcast(MessageKey.JOIN_RACE_SUCCESS);
		} else {
			MessageManager.sendMessage(player, MessageKey.JOIN_RACE_SUCCESS);
		}
	}

	public StartRaceStatus tryStartRace(String raceName, CommandSender commandSender, int numLaps) {
		Race race = getRace(raceName);
		if (race == null) {
			List<Race> allRaces = races.stream().filter((Race r) -> r.getState() == RaceState.ENABLED).collect(Collectors.toList());
			if (allRaces.isEmpty()) {
				MessageManager.sendMessage(commandSender, MessageKey.START_RACE_NO_ENABLED);
				return StartRaceStatus.ERROR;
			}
			race = allRaces.get(Util.randomRangeInt(0, allRaces.size() - 1));
		}
		if (race.getState() != RaceState.ENABLED) {
			MessageManager.setValue("race_name", race.getName());
			MessageManager.sendMessage(commandSender, MessageKey.START_RACE_NOT_ENABLED);
			return StartRaceStatus.ERROR;
		}
		List<RaceSession> sessions = getRaceSessions(race);
		if (!sessions.isEmpty()) {
			MessageManager.sendMessage(commandSender, MessageKey.START_RACE_ALREADY_STARTED);
			return StartRaceStatus.ERROR;
		}
		if (race.getStartPoints().size() < 1) {
			MessageManager.sendMessage(commandSender, MessageKey.START_RACE_MISSING_STARTPOINT);
			return StartRaceStatus.ERROR;
		}
		if (race.getCheckpoints().isEmpty()) {
			MessageManager.sendMessage(commandSender, MessageKey.START_RACE_MISSING_CHECKPOINT);
			return StartRaceStatus.ERROR;
		}
		if (race.getCheckpoints().size() < 2 && numLaps > 1) {
			MessageManager.sendMessage(commandSender, MessageKey.START_RACE_MISSING_CHECKPOINTS);
			return StartRaceStatus.ERROR;
		}
		startNewSession(commandSender, race, numLaps);
		return StartRaceStatus.OK;
	}

	private void addChunkTickets() {
		if (PaperLib.isPaper()) {
			return;
		}
		for (World world : Bukkit.getWorlds()) {
			world.removePluginChunkTickets(RacingPlugin.getInstance());
		}
		for (Race race : races) {
			if (race.getState() == RaceState.ENABLED) {
				continue;
			}
			for (RaceStartPoint startPoint : race.getStartPoints()) {
				startPoint.getLocation().getChunk().addPluginChunkTicket(RacingPlugin.getInstance());
			}
			for (RaceCheckpoint checkpoint : race.getCheckpoints()) {
				checkpoint.getLocation().getChunk().addPluginChunkTicket(RacingPlugin.getInstance());
			}
			race.getSpawn().getChunk().addPluginChunkTicket(RacingPlugin.getInstance());
		}
	}

	public Set<GameMode> getNonJoinableGameModes() {
		List<String> value = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.PREVENT_JOIN_FROM_GAME_MODE);
		Set<GameMode> gameModes = new HashSet<>();
		for (String gameMode : value) {
			try {
				gameModes.add(GameMode.valueOf(gameMode.toUpperCase(Locale.ENGLISH)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return gameModes;
	}

	public Set<World> getNonJoinableWorlds() {
		List<String> value = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.PREVENT_JOIN_FROM_WORLD);
		Set<World> worlds = new HashSet<>();
		for (String world : value) {
			World gm = Bukkit.getWorld(world);
			if (gm != null) {
				worlds.add(gm);
			}
		}
		return worlds;
	}
}
