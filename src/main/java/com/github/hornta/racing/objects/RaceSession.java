package com.github.hornta.racing.objects;

import com.github.hornta.racing.ConfigKey;
import com.github.hornta.racing.MessageKey;
import com.github.hornta.racing.RacingPlugin;
import com.github.hornta.racing.ScoreboardManager;
import com.github.hornta.racing.SongManager;
import com.github.hornta.racing.Util;
import com.github.hornta.racing.enums.RaceCommandType;
import com.github.hornta.racing.enums.RaceSessionState;
import com.github.hornta.racing.enums.RaceStatType;
import com.github.hornta.racing.enums.RaceType;
import com.github.hornta.racing.enums.RespawnType;
import com.github.hornta.racing.events.CheckpointReachedEvent;
import com.github.hornta.racing.events.ExecuteCommandEvent;
import com.github.hornta.racing.events.LeaveEvent;
import com.github.hornta.racing.events.ParticipateEvent;
import com.github.hornta.racing.events.RacePlayerGoalEvent;
import com.github.hornta.racing.events.RaceSessionResultEvent;
import com.github.hornta.racing.events.RaceSessionStopEvent;
import com.github.hornta.racing.events.SessionStateChangedEvent;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.songplayer.RadioSongPlayer;
import io.papermc.lib.PaperLib;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import se.hornta.messenger.MessageManager;

import java.util.*;

public class RaceSession implements Listener {
	private final CommandSender initiator;
	private final Race race;
	private final int laps;
	private final List<BukkitTask> startTimerTasks;
	private final Map<Player, RacePlayerSession> playerSessions;
	private final ScoreboardManager scoreboardManager;
	private RadioSongPlayer songPlayer;
	private RaceSessionState state;
	private RaceCountdown countdown;
	private long start;
	private RaceSessionResult result;
	private int numJoinedParticipants;

	public RaceSession(CommandSender initiator, Race race, int laps) {
		this.initiator = initiator;
		this.race = race;
		this.laps = laps;
		startTimerTasks = new ArrayList<>();
		playerSessions = new LinkedHashMap<>();
		scoreboardManager = new ScoreboardManager();
		Bukkit.getPluginManager().registerEvents(scoreboardManager, RacingPlugin.getInstance());
		if (RacingPlugin.getInstance().isNoteBlockAPILoaded() && race.getSong() != null) {
			songPlayer = new RadioSongPlayer(SongManager.getSongByName(race.getSong()));
		}
		numJoinedParticipants = 0;
	}

	public int getLaps() {
		return laps;
	}

	public RaceSessionState getState() {
		return state;
	}

	public void setState(RaceSessionState state) {
		RaceSessionState oldState = this.state;
		this.state = state;
		Bukkit.getPluginManager().callEvent(new SessionStateChangedEvent(this, oldState));
	}

	public Race getRace() {
		return race;
	}

	private ComponentBuilder makeJoinChatCommand() {
		HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageManager.getMessage(MessageKey.PARTICIPATE_HOVER_TEXT)));
		MessageManager.setValue("race_name", race.getName());
		ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, MessageManager.getMessage(MessageKey.PARTICIPATE_CLICK_TEXT));
		return new ComponentBuilder("").event(hoverEvent).event(clickEvent);
	}

	private void broadcastStartMessage() {
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.BROADCAST_START_RACE_MESSAGE)) {
			int prepareTime = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RACE_PREPARE_TIME);
			MessageManager.setValue("race_name", race.getName());
			MessageManager.setValue("time_left", Util.getTimeLeft(prepareTime * 1000));
			MessageManager.setValue("laps", laps);
			MessageKey key = MessageKey.PARTICIPATE_TEXT;
			Economy economy = RacingPlugin.getInstance().getVaultEconomy();
			if (economy != null) {
				key = MessageKey.PARTICIPATE_TEXT_FEE;
				MessageManager.setValue("entry_fee", economy.format(race.getEntryFee()));
			}
			Util.setTimeUnitValues();
			String participateText = MessageManager.getMessage(key);
			Bukkit.getServer().spigot().broadcast(new ComponentBuilder(makeJoinChatCommand()).append(participateText).create());
		}
	}

	private void runAnnouncements() {
		List<Integer> announceIntervals = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RACE_ANNOUNCE_INTERVALS);
		int prepareTime = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RACE_PREPARE_TIME);
		for (int interval : announceIntervals) {
			if (interval >= prepareTime) {
				continue;
			}
			addStartTimerTask(Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
				MessageManager.setValue("race_name", race.getName());
				MessageManager.setValue("time_left", Util.getTimeLeft(interval * 1000));
				Util.setTimeUnitValues();
				String timeLeftMessage = MessageManager.getMessage(MessageKey.PARTICIPATE_TEXT_TIMELEFT);
				Bukkit.getServer().spigot().broadcast(new ComponentBuilder(makeJoinChatCommand()).append(timeLeftMessage).create());
			}, (long) (prepareTime - interval) * 20));
		}
	}

	private void displayInitiatorControls() {
		MessageManager.setValue("race_name", race.getName());
		ClickEvent skipWaitClickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, MessageManager.getMessage(MessageKey.SKIP_WAIT_CLICK_TEXT));
		MessageManager.setValue("race_name", race.getName());
		ClickEvent stopClickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, MessageManager.getMessage(MessageKey.STOP_RACE_CLICK_TEXT));
		if (initiator instanceof Player) {
			HoverEvent skipWaitHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageManager.getMessage(MessageKey.SKIP_WAIT_HOVER_TEXT)));
			HoverEvent stopHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(MessageManager.getMessage(MessageKey.STOP_RACE_HOVER_TEXT)));
			TextComponent tc = new TextComponent();
			tc.addExtra(new ComponentBuilder(MessageManager.getMessage(MessageKey.SKIP_WAIT)).event(skipWaitHover).event(skipWaitClickEvent).create()[0]);
			tc.addExtra(" ");
			tc.addExtra(new ComponentBuilder(MessageManager.getMessage(MessageKey.STOP_RACE)).event(stopHover).event(stopClickEvent).create()[0]);
			initiator.spigot().sendMessage(tc);
		}
	}

	public void start() {
		result = new RaceSessionResult(this);
		Bukkit.getServer().getPluginManager().registerEvents(this, RacingPlugin.getInstance());
		broadcastStartMessage();
		setState(RaceSessionState.PREPARING);
		displayInitiatorControls();
		runAnnouncements();
		int prepareTime = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RACE_PREPARE_TIME);
		addStartTimerTask(Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), this::actualStart, prepareTime * 20L));
	}

	private void actualStart() {
		Iterator<RacePlayerSession> playerSessionIterator = playerSessions.values().iterator();
		while (playerSessionIterator.hasNext()) {
			RacePlayerSession session = playerSessionIterator.next();
			Player player = session.getPlayer();
			if (RacingPlugin.getInstance().getRacingManager().getNonJoinableGameModes().contains(player.getGameMode())) {
				playerSessionIterator.remove();
				MessageManager.sendMessage(player, MessageKey.GAME_MODE_DISQUALIFIED_TARGET);
				for (RacePlayerSession session1 : playerSessions.values()) {
					MessageManager.setValue("player_name", session.getPlayer().getName());
					MessageManager.sendMessage(session1.getPlayer(), MessageKey.GAME_MODE_DISQUALIFIED);
				}
				Economy economy = RacingPlugin.getInstance().getVaultEconomy();
				if (economy != null && session.getChargedEntryFee() > 0) {
					economy.depositPlayer(Bukkit.getOfflinePlayer(session.getPlayer().getUniqueId()), session.getChargedEntryFee());
				}
			}
		}
		if (playerSessions.isEmpty() || getAmountOfParticipants() < race.getMinimimRequiredParticipantsToStart()) {
			if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.BROADCAST_CANCEL_MESSAGE)) {
				MessageManager.broadcast(MessageKey.RACE_CANCELED);
			}
			stop();
			return;
		}
		setState(RaceSessionState.COUNTDOWN);
		if (RacingPlugin.getInstance().getConfiguration().<Boolean>get(ConfigKey.CHECKPOINT_PARTICLES_DURING_RACE)) {
			for (int i = 0; i < race.getCheckpoints().size(); ++i) {
				race.getCheckpoints().get(i).startTask(false, i == race.getCheckpoints().size() - 1);
			}
		}
		int startPointIndex = 0;
		long worldRecord = Long.MAX_VALUE;
		long worldRecordFastestLap = Long.MAX_VALUE;
		String worldRecordHolder = "";
		String worldRecordFastestLapHolder = "";
		for (RacePlayerStatistic playerStatistics : race.getResultByPlayerId().values()) {
			if (worldRecord > playerStatistics.getRecord(laps)) {
				worldRecord = playerStatistics.getRecord(laps);
				worldRecordHolder = playerStatistics.getPlayerName();
			}
		}
		Set<RacePlayerStatistic> fastestLaps = race.getResults(RaceStatType.FASTEST_LAP, laps);
		if (!fastestLaps.isEmpty()) {
			RacePlayerStatistic statistic = fastestLaps.iterator().next();
			worldRecordFastestLap = statistic.getFastestLap();
			worldRecordFastestLapHolder = statistic.getPlayerName();
		}
		for (RacePlayerSession session : getStartOrderSessions()) {
			session.setCurrentLap(1);
			session.setStartPoint(race.getStartPoints().get(startPointIndex));
			session.setBossBar(Bukkit.createBossBar(getBossBarTitle(session), BarColor.BLUE, BarStyle.SOLID));
			session.startCooldown();
			tryIncrementCheckpoint(session);
			startPointIndex += 1;
			scoreboardManager.addScoreboard(session.getPlayer(), race.getName(), laps);
			scoreboardManager.updateWorldRecord(session.getPlayer(), worldRecord);
			scoreboardManager.updateWorldRecordHolder(session.getPlayer(), worldRecordHolder);
			scoreboardManager.updateWorldRecordFastestLap(session.getPlayer(), worldRecordFastestLap);
			scoreboardManager.updateWorldRecordFastestLapHolder(session.getPlayer(), worldRecordFastestLapHolder);
			if (race.getResultByPlayerId().containsKey(session.getPlayer().getUniqueId())) {
				RacePlayerStatistic statistics = race.getResultByPlayerId().get(session.getPlayer().getUniqueId());
				scoreboardManager.updatePersonalBestLapTime(session.getPlayer(), statistics.getFastestLap());
				if (statistics.getRecord(laps) != Long.MAX_VALUE) {
					scoreboardManager.updatePersonalBest(session.getPlayer(), statistics.getRecord(laps));
				}
			}
		}
		countdown = new RaceCountdown(playerSessions.values());
		countdown.start(() -> {
			setState(RaceSessionState.STARTED);
			Collection<PotionEffect> potionEffects = new ArrayList<>();
			for (RacePotionEffect racePotionEffect : race.getPotionEffects()) {
				potionEffects.add(new PotionEffect(racePotionEffect.getType(), Integer.MAX_VALUE, racePotionEffect.getAmplifier(), false, false, false));
			}
			for (RacePlayerSession session : playerSessions.values()) {
				session.startRace();
				RespawnType type = getRespawnInteractType(race.getType());
				switch (type) {
					case FROM_LAST_CHECKPOINT:
						MessageManager.sendMessage(session.getPlayer(), MessageKey.RESPAWN_INTERACT_LAST);
						break;
					case FROM_START:
						MessageManager.sendMessage(session.getPlayer(), MessageKey.RESPAWN_INTERACT_START);
						break;
					case NONE:
					default:
				}
				for (PotionEffect potionEffect : potionEffects) {
					session.getPlayer().addPotionEffect(potionEffect);
				}
				if (songPlayer != null) {
					songPlayer.addPlayer(session.getPlayer());
				}
			}
			if (songPlayer != null) {
				songPlayer.setTick((short) 0);
				songPlayer.setRepeatMode(RepeatMode.ALL);
				songPlayer.setPlaying(true);
			}
			start = System.currentTimeMillis();
			for (RacePlayerSession session : playerSessions.values()) {
				session.setLapStartTime(start);
			}
			int ticksPerUpdate = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_TICKS_PER_UPDATE);
			new BukkitRunnable() {
				@Override
				public void run() {
					long currentTime = System.currentTimeMillis();
					long raceTime = currentTime - start;
					for (RacePlayerSession session : playerSessions.values()) {
						if (!session.isFinished()) {
							Player player = session.getPlayer();
							long lapTime = currentTime - session.getLapStartTime();
							scoreboardManager.updateRaceTime(player, raceTime);
							scoreboardManager.updateRaceCurrentLapTime(player, lapTime);
						}
					}
				}
			}.runTaskTimer(RacingPlugin.getInstance(), ticksPerUpdate, ticksPerUpdate);
		});
	}

	private List<RacePlayerSession> getStartOrderSessions() {
		Map<UUID, RacePlayerStatistic> resultsByPlayerId = race.getResultByPlayerId();
		List<RacePlayerSession> startOrderSessions = new ArrayList<>(playerSessions.values());
		switch (race.getStartOrder()) {
			case FASTEST:
				startOrderSessions.sort((RacePlayerSession o1, RacePlayerSession o2) -> {
					long t1 = resultsByPlayerId.containsKey(o1.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o1.getPlayer().getUniqueId()).getRecord(laps) : Long.MAX_VALUE;
					long t2 = resultsByPlayerId.containsKey(o2.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o2.getPlayer().getUniqueId()).getRecord(laps) : Long.MAX_VALUE;
					return Long.compare(t1, t2);
				});
				break;
			case FASTEST_LAP:
				startOrderSessions.sort((RacePlayerSession o1, RacePlayerSession o2) -> {
					long t1 = resultsByPlayerId.containsKey(o1.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o1.getPlayer().getUniqueId()).getFastestLap() : Long.MAX_VALUE;
					long t2 = resultsByPlayerId.containsKey(o2.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o2.getPlayer().getUniqueId()).getFastestLap() : Long.MAX_VALUE;
					return Long.compare(t1, t2);
				});
				break;
			case SLOWEST:
				startOrderSessions.sort((RacePlayerSession o1, RacePlayerSession o2) -> {
					long t1 = resultsByPlayerId.containsKey(o1.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o1.getPlayer().getUniqueId()).getRecord(laps) : Long.MAX_VALUE;
					long t2 = resultsByPlayerId.containsKey(o2.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o2.getPlayer().getUniqueId()).getRecord(laps) : Long.MAX_VALUE;
					return Long.compare(t2, t1);
				});
				break;
			case SLOWEST_LAP:
				startOrderSessions.sort((RacePlayerSession o1, RacePlayerSession o2) -> {
					long t1 = resultsByPlayerId.containsKey(o1.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o1.getPlayer().getUniqueId()).getFastestLap() : Long.MAX_VALUE;
					long t2 = resultsByPlayerId.containsKey(o2.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o2.getPlayer().getUniqueId()).getFastestLap() : Long.MAX_VALUE;
					return Long.compare(t2, t1);
				});
				break;
			case RANDOM:
				Collections.shuffle(startOrderSessions);
				break;
			case WINS:
				startOrderSessions.sort((RacePlayerSession o1, RacePlayerSession o2) -> {
					long t1 = resultsByPlayerId.containsKey(o1.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o1.getPlayer().getUniqueId()).getWins() : 0;
					long t2 = resultsByPlayerId.containsKey(o2.getPlayer().getUniqueId()) ? resultsByPlayerId.get(o2.getPlayer().getUniqueId()).getWins() : 0;
					return Long.compare(t1, t2);
				});
				break;
			case JOIN_ORDER:
				//do nothing, startOrderSessions is already sorted
				break;
			case REVERSE_JOIN_ORDER:
				Collections.reverse(startOrderSessions);
				break;
			default:
				break;
		}
		return startOrderSessions;
	}

	public void skipToCountdown() {
		for (BukkitTask task : startTimerTasks) {
			task.cancel();
		}
		if (state != RaceSessionState.COUNTDOWN) {
			actualStart();
		}
	}

	private void tryAndSkipToCountdown() {
		if (playerSessions.size() == race.getStartPoints().size()) {
			skipToCountdown();
		}
	}

	public void stop() {
		beforeStopCleanup();
		Bukkit.getPluginManager().callEvent(new RaceSessionStopEvent(this));
		playerSessions.clear();
	}

	private void beforeStopCleanup() {
		if (countdown != null) {
			countdown.stop();
			countdown = null;
		}
		for (BukkitTask task : startTimerTasks) {
			task.cancel();
		}
		startTimerTasks.clear();
		for (RacePlayerSession session : playerSessions.values()) {
			session.restore();
			if (songPlayer != null) {
				songPlayer.removePlayer(session.getPlayer());
			}
		}
		if (songPlayer != null) {
			songPlayer.setPlaying(false);
		}
		if (state != RaceSessionState.PREPARING) {
			for (RaceCheckpoint checkpoint : race.getCheckpoints()) {
				checkpoint.stopTask();
			}
		}
		HandlerList.unregisterAll(this);
	}

	public RacePlayerSession getPlayerSession(Player player) {
		return playerSessions.get(player);
	}

	public void leave(Player player) {
		RacePlayerSession playerSession = playerSessions.get(player);
		playerSession.restore();
		playerSessions.remove(player);
		scoreboardManager.removeScoreboard(player);
		Economy economy = RacingPlugin.getInstance().getVaultEconomy();
		if (economy != null && playerSession.getChargedEntryFee() > 0) {
			economy.depositPlayer(player, playerSession.getChargedEntryFee());
			MessageManager.setValue("entry_fee", economy.format(playerSession.getChargedEntryFee()));
			MessageManager.sendMessage(player, MessageKey.RACE_LEAVE_PAYBACK);
		}
		for (RacePlayerSession session : playerSessions.values()) {
			if (session.getPlayer() != null) {
				MessageManager.setValue("player_name", player.getName());
				MessageManager.sendMessage(session.getPlayer(), MessageKey.RACE_LEAVE_BROADCAST);
			}
		}
		Bukkit.getPluginManager().callEvent(new LeaveEvent(this, playerSession));
		if (state == RaceSessionState.COUNTDOWN || state == RaceSessionState.STARTED) {
			checkFinished();
		}
	}

	public List<RacePlayerSession> getPlayerSessions() {
		return new ArrayList<>(playerSessions.values());
	}

	public boolean isFull() {
		return playerSessions.size() == race.getStartPoints().size();
	}

	public boolean isParticipating(Player player) {
		return playerSessions.containsKey(player);
	}

	public boolean isCurrentlyRacing(Player player) {
		if (!playerSessions.containsKey(player)) {
			return false;
		}
		RacePlayerSession playerSession = playerSessions.get(player);
		return !playerSession.isRestored();
	}

	public int getAmountOfParticipants() {
		return playerSessions.size();
	}

	public void participate(Player player, double chargedEntryFee) {
		RacePlayerSession session = new RacePlayerSession(this, player, chargedEntryFee);
		playerSessions.put(player, session);
		numJoinedParticipants += 1;
		Bukkit.getPluginManager().callEvent(new ParticipateEvent(this, session));
		tryAndSkipToCountdown();
	}

	private void addStartTimerTask(int id) {
		startTimerTasks.add(Bukkit.getScheduler().getPendingTasks().stream().filter(t -> t.getTaskId() == id).findFirst().get());
	}

	private void tryIncrementCheckpoint(RacePlayerSession playerSession) {
		RaceCheckpoint nextCheckpoint = playerSession.getNextCheckpoint();
		boolean hasFinished = playerSession.getCurrentCheckpoint() != null && nextCheckpoint == null;
		if (hasFinished) {
			return;
		}
		if (nextCheckpoint == null) {
			playerSession.setNextCheckpoint(race.getCheckpoint(1));
			playerSession.getBossBar().setProgress(0);
		} else {
			int numCheckpoints = race.getCheckpoints().size();
			Player player = playerSession.getPlayer();
			if (nextCheckpoint.isInside(player)) {
				player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
				int checkpointIndex = race.getCheckpoints().indexOf(nextCheckpoint);
				int totalCheckpoints = numCheckpoints * laps;
				int currentCheckpoints = (playerSession.getCurrentLap() - 1) * numCheckpoints + checkpointIndex + 1;
				double progress = currentCheckpoints / (double) totalCheckpoints;
				playerSession.getBossBar().setProgress(progress);
				boolean isLastCheckpointOfLap = checkpointIndex == numCheckpoints - 1;
				boolean isLastLap = playerSession.getCurrentLap() == laps;
				if (isLastCheckpointOfLap) {
					updatePlayerLapTime(playerSession);
					if (isLastLap) {
						playerSession.setNextCheckpoint(null);
						result.addPlayerSessionResult(playerSession, System.currentTimeMillis() - start, true);
						playerSession.restore();
						scoreboardManager.removeScoreboard(playerSession.getPlayer());
						playerSessions.remove(playerSession.getPlayer());
						Bukkit.getPluginManager().callEvent(new RacePlayerGoalEvent(this, playerSession));
						Bukkit.getPluginManager().callEvent(new ExecuteCommandEvent(RaceCommandType.ON_PLAYER_FINISH, this, playerSession));
						checkFinished();
					} else {
						playerSession.setNextCheckpoint(race.getCheckpoint(1));
						playerSession.setCurrentLap(playerSession.getCurrentLap() + 1);
						playerSession.getBossBar().setTitle(getBossBarTitle(playerSession));
						if (laps > 1) {
							String message;
							if (playerSession.getCurrentLap() == laps) {
								message = MessageManager.getMessage(MessageKey.RACE_FINAL_LAP);
							} else {
								MessageManager.setValue("ordinal", playerSession.getCurrentLap());
								message = MessageManager.getMessage(MessageKey.RACE_NEXT_LAP);
							}
							player.sendActionBar(TextComponent.fromLegacyText(message));
						}
					}
				} else {
					playerSession.setNextCheckpoint(race.getCheckpoint(nextCheckpoint.getPosition() + 1));
				}
				Bukkit.getPluginManager().callEvent(new CheckpointReachedEvent(this, playerSession, nextCheckpoint));
			}
		}
	}

	private void updatePlayerLapTime(RacePlayerSession playerSession) {
		long currentTime = System.currentTimeMillis();
		playerSession.setFastestLapTime(currentTime - playerSession.getLapStartTime());
		playerSession.setLapStartTime(currentTime);
		scoreboardManager.updateRaceFastestLap(playerSession.getPlayer(), playerSession.getFastestLap());
		scoreboardManager.updatePersonalBestLapTime(playerSession.getPlayer(), playerSession.getPersonalBestLapTime());
	}

	@EventHandler
	void onVehicleMove(VehicleMoveEvent event) {
		if (race.getType() != RaceType.MINECART || event.getVehicle().getPassengers().isEmpty() || !(event.getVehicle().getPassengers().get(0) instanceof Player)) {
			return;
		}
		Player player = (Player) event.getVehicle().getPassengers().get(0);
		if (!isCurrentlyRacing(player)) {
			return;
		}
		RacePlayerSession playerSession = playerSessions.get(player);
		if (state == RaceSessionState.COUNTDOWN || state == RaceSessionState.STARTED) {
			tryIncrementCheckpoint(playerSession);
		}
		if (state == RaceSessionState.COUNTDOWN && playerSession.getStartLocation().distanceSquared(event.getTo()) >= 1) {
			playerSession.respawnInVehicle();
			if (race.getType() == RaceType.HORSE) {
				playerSession.freezeHorse();
			}
		}
	}

	@EventHandler
	void onPlayerMove(PlayerMoveEvent event) {
		if (isCurrentlyRacing(event.getPlayer()) && (state == RaceSessionState.COUNTDOWN || state == RaceSessionState.STARTED)) {
			RacePlayerSession playerSession = playerSessions.get(event.getPlayer());
			if (playerSession.isRestored()) {
				return;
			}
			tryIncrementCheckpoint(playerSession);
			// prevent player from moving after being teleported to the start point
			// will happen when player for example is holding walk forward button while being teleported
			if (state != RaceSessionState.COUNTDOWN) {
				return;
			}
			if (playerSession.getVehicle() != null) {
				if (playerSession.getStartLocation().distanceSquared(event.getTo()) >= 1) {
					playerSession.respawnInVehicle();
					if (race.getType() == RaceType.HORSE) {
						playerSession.freezeHorse();
					}
				}
			} else {
				if (Double.compare(event.getFrom().getX(), event.getTo().getX()) == 0 && Double.compare(event.getFrom().getY(), event.getTo().getY()) == 0 && Double.compare(event.getFrom().getZ(), event.getTo().getZ()) == 0) {
					return;
				}
				event.setTo(new Location(event.getFrom().getWorld(), event.getFrom().getX(), event.getFrom().getY(), event.getFrom().getZ(), event.getTo().getYaw(), event.getTo().getPitch()));
			}
		}
	}

	@EventHandler
	void onEntityTarget(EntityTargetEvent event) {
		if ((event.getTarget() instanceof Player) && isCurrentlyRacing((Player) event.getTarget()) && state == RaceSessionState.COUNTDOWN) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	void onPlayerKick(PlayerKickEvent event) {
		Player player = event.getPlayer();
		if (isCurrentlyRacing(player) && (state == RaceSessionState.COUNTDOWN || state == RaceSessionState.STARTED)) {
			playerSessions.get(player).restore();
			playerSessions.remove(player);
			checkFinished();
		}
	}

	@EventHandler
	void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (!isParticipating(player)) {
			return;
		}
		leave(player);
	}

	@EventHandler
	void onEntityDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player)) {
			return;
		}
		Player player = (Player) event.getEntity();
		if (!isCurrentlyRacing(player)) {
			return;
		}
		if (event.getFinalDamage() >= player.getHealth()) {
			RespawnType respawnType = getRespawnDeathType(race.getType());
			event.setCancelled(true);
			player.setFoodLevel(RacePlayerSession.MAX_FOOD_LEVEL);
			player.setHealth(RacePlayerSession.MAX_HEALTH);
			RacePlayerSession playerSession = playerSessions.get(player);
			switch (respawnType) {
				case FROM_LAST_CHECKPOINT:
				case FROM_START:
					playerSession.respawn(respawnType, null, null);
					break;
				case NONE:
					if (state != RaceSessionState.STARTED) {
						playerSession.respawn(RespawnType.FROM_START, null, null);
						break;
					}
					playerSession.restore();
					playerSessions.remove(player);
					player.setFallDistance(0);
					PaperLib.teleportAsync(player, race.getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
					MessageManager.sendMessage(player, MessageKey.DEATH_DISQUALIFIED_TARGET);
					for (RacePlayerSession session : playerSessions.values()) {
						MessageManager.setValue("player_name", player.getName());
						MessageManager.sendMessage(session.getPlayer(), MessageKey.DEATH_DISQUALIFIED);
					}
					if (playerSessions.isEmpty()) {
						checkFinished();
					}
					break;
				default:
			}
		}
	}

	@EventHandler
	void onVehicleEnter(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player)) {
			return;
		}
		Player player = (Player) event.getEntered();
		if (!isCurrentlyRacing(player)) {
			return;
		}
		RacePlayerSession session = playerSessions.get(player);
		switch (race.getType()) {
			case PLAYER:
			case ELYTRA:
				event.setCancelled(true);
				return;
			case MINECART:
			case HORSE:
			case BOAT:
			case PIG:
			case STRIDER:
			default:
		}
		// if player is already mounted we need to cancel a new attempt to mount
		if (!session.isAllowedToEnterVehicle()) {
			event.setCancelled(true);
			// because the player attempted to mount another vehicle, they become automatically dismounted from their current vehicle
			if (session.getVehicle() != event.getVehicle()) {
				// remount them onto their real vehicle
				Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> session.enterVehicle(session.getVehicle()));
			}
			return;
		}
		if ((race.getType() == RaceType.PIG && event.getVehicle().getType() != EntityType.PIG) || (race.getType() == RaceType.HORSE && event.getVehicle().getType() != EntityType.HORSE) || (race.getType() == RaceType.MINECART && event.getVehicle().getType() != EntityType.MINECART) || (race.getType() == RaceType.STRIDER && event.getVehicle().getType() != EntityType.STRIDER)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	void onVehicleExit(VehicleExitEvent event) {
		boolean isPlayer = event.getExited() instanceof Player;
		if (!isPlayer) {
			return;
		}

		if (state == RaceSessionState.PREPARING) {
			return;
		}
		Player player = (Player) event.getExited();
		if (!isCurrentlyRacing(player)) {
			return;
		}
		if (!playerSessions.get(player).isAllowedToExitVehicle()) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	void onPlayerDropItem(PlayerDropItemEvent event) {
		if (!isCurrentlyRacing(event.getPlayer())) {
			return;
		}
		switch (race.getType()) {
			case ELYTRA:
				if (event.getItemDrop().getItemStack().getType() == Material.ELYTRA) {
					event.setCancelled(true);
				}
				break;
			case PIG:
				if (event.getItemDrop().getItemStack().getType() == Material.CARROT_ON_A_STICK) {
					event.setCancelled(true);
				}
				break;
			case STRIDER:
				if (event.getItemDrop().getItemStack().getType() == Material.WARPED_FUNGUS_ON_A_STICK) {
					event.setCancelled(true);
				}
				break;
			case HORSE:
				if (event.getItemDrop().getItemStack().getType() == Material.SADDLE) {
					event.setCancelled(true);
				}
				break;
			default:
		}
	}

	@EventHandler
	void onPlayerItemDamageEvent(PlayerItemDamageEvent event) {
		if (!isCurrentlyRacing(event.getPlayer())) {
			return;
		}
		switch (race.getType()) {
			case PIG:
				if (event.getItem().getType() == Material.CARROT_ON_A_STICK) {
					event.setCancelled(true);
				}
				break;
			case STRIDER:
				if (event.getItem().getType() == Material.WARPED_FUNGUS_ON_A_STICK) {
					event.setCancelled(true);
				}
				break;
			case ELYTRA:
				if (event.getItem().getType() == Material.ELYTRA) {
					event.setCancelled(true);
				}
				break;
			default:
		}
	}

	@EventHandler
	void onPlayerInteract(PlayerInteractEvent event) {
		if (!isCurrentlyRacing(event.getPlayer()) || state != RaceSessionState.STARTED || event.getItem() != null) {
			return;
		}
		RacePlayerSession playerSession = playerSessions.get(event.getPlayer());
		RespawnType respawnType = getRespawnInteractType(race.getType());
		if (respawnType == RespawnType.FROM_LAST_CHECKPOINT || respawnType == RespawnType.FROM_START) {
			playerSession.respawn(respawnType, null, null);
		}
	}

	@EventHandler
	void onEntityToggleGlideEvent(EntityToggleGlideEvent event) {
		if (!RacingPlugin.getInstance().getConfiguration().<Boolean>get(ConfigKey.ELYTRA_RESPAWN_ON_GROUND)) {
			return;
		}
		if (!(event.getEntity() instanceof Player)) {
			return;
		}
		Player player = (Player) event.getEntity();
		if (!isCurrentlyRacing(player) || state != RaceSessionState.STARTED) {
			return;
		}
		if (!event.isGliding()) {
			playerSessions.get(player).respawn(RespawnType.FROM_START, null, null);
			playerSessions.get(player).setNextCheckpoint(race.getCheckpoint(1));
		}
	}

	private String getBossBarTitle(RacePlayerSession session) {
		if (laps == 1) {
			return race.getName();
		}
		return race.getName() + " lap " + session.getCurrentLap() + "/" + laps;
	}

	private void checkFinished() {
		boolean allFinished = true;
		for (RacePlayerSession playerSession : playerSessions.values()) {
			if (!playerSession.isFinished()) {
				allFinished = false;
				break;
			}
		}
		if (allFinished) {
			stop();
			Bukkit.getPluginManager().callEvent(new RaceSessionResultEvent(result));
			Bukkit.getPluginManager().callEvent(new ExecuteCommandEvent(RaceCommandType.ON_RACE_FINISH, this));
		}
	}

	private RespawnType getRespawnInteractType(RaceType type) {
		switch (type) {
			case HORSE:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_HORSE_INTERACT)).toUpperCase(Locale.ENGLISH));
			case PIG:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_PIG_INTERACT)).toUpperCase(Locale.ENGLISH));
			case STRIDER:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_STRIDER_INTERACT)).toUpperCase(Locale.ENGLISH));
			case BOAT:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_BOAT_INTERACT)).toUpperCase(Locale.ENGLISH));
			case ELYTRA:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_ELYTRA_INTERACT)).toUpperCase(Locale.ENGLISH));
			case PLAYER:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_PLAYER_INTERACT)).toUpperCase(Locale.ENGLISH));
			case MINECART:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_MINECART_INTERACT)).toUpperCase(Locale.ENGLISH));
			default:
				throw new IllegalArgumentException();
		}
	}

	private RespawnType getRespawnDeathType(RaceType type) {
		switch (type) {
			case HORSE:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_HORSE_DEATH)).toUpperCase(Locale.ENGLISH));
			case PIG:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_PIG_DEATH)).toUpperCase(Locale.ENGLISH));
			case STRIDER:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_STRIDER_DEATH)).toUpperCase(Locale.ENGLISH));
			case BOAT:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_BOAT_DEATH)).toUpperCase(Locale.ENGLISH));
			case ELYTRA:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_ELYTRA_DEATH)).toUpperCase(Locale.ENGLISH));
			case PLAYER:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_PLAYER_DEATH)).toUpperCase(Locale.ENGLISH));
			case MINECART:
				return RespawnType.valueOf(((String) RacingPlugin.getInstance().getConfiguration().get(ConfigKey.RESPAWN_MINECART_DEATH)).toUpperCase(Locale.ENGLISH));
			default:
				throw new IllegalArgumentException();
		}
	}

	public RaceSessionResult getResult() {
		return result;
	}

	int getNumJoinedParticipants() {
		return numJoinedParticipants;
	}
}
