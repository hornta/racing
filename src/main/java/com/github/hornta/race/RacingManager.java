package com.github.hornta.race;

import com.github.hornta.race.api.RacingAPI;
import com.github.hornta.race.enums.*;
import com.github.hornta.race.events.*;
import com.github.hornta.race.MessageKey;
import com.github.hornta.carbon.message.MessageManager;
import com.github.hornta.race.objects.*;
import io.papermc.lib.PaperLib;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RacingManager implements Listener {
  private Map<String, Race> racesByName = new HashMap<>();
  private List<Race> races = new ArrayList<>();
  private RacingAPI api;
  private List<RaceSession> raceSessions = new ArrayList<>();

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
  void onCreateRace(CreateRaceEvent event) {
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
  void onDeleteRace(DeleteRaceEvent event) {
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

    if (Racing.getInstance().isHolographicDisplaysLoaded()) {
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
    List<PlayerSessionResult> sortedResults = new ArrayList<>(event.getResult().getPlayerResults().values());
    sortedResults.sort(Comparator.comparingInt(PlayerSessionResult::getPosition));

    for (PlayerSessionResult result : sortedResults) {
      Race race = event.getResult().getRaceSession().getRace();
      race.addResult(result);

      int position = result.getPosition();
      if (position <= 10) {
        MessageManager.setValue("position", position);
        MessageManager.setValue("player_name", result.getPlayerSession().getPlayerName());
        MessageManager.setValue("race_name", race.getName());
        MessageManager.setValue("time", Util.getTimeLeft(result.getTime()));
        Util.setTimeUnitValues();
        MessageManager.broadcast(MessageKey.RACE_PARTICIPANT_RESULT);
      }
    }

    updateRace(event.getResult().getRaceSession().getRace(), () -> {
    });
  }

  @EventHandler
  void onRaceSessionStop(RaceSessionStopEvent event) {
    raceSessions.remove(event.getRaceSession());

    if (Racing.getInstance().getConfiguration().get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED)) {
      TeleportAfterRaceWhen when = Racing.getInstance().getConfiguration()
          .get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED_WHEN);
      if (when == TeleportAfterRaceWhen.EVERYONE_FINISHES) {
        for (RacePlayerSession playerSession : event.getRaceSession().getPlayerSessions()) {
          PaperLib.teleportAsync(playerSession.getPlayer(), event.getRaceSession().getRace().getSpawn(),
              PlayerTeleportEvent.TeleportCause.PLUGIN);
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

    List<String> blockedCommands = Racing.getInstance().getConfiguration().get(ConfigKey.BLOCKED_COMMANDS);
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
    if (Racing.getInstance().getConfiguration().get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED)) {
      TeleportAfterRaceWhen when = Racing.getInstance().getConfiguration()
          .get(ConfigKey.TELEPORT_AFTER_RACE_ENABLED_WHEN);
      if (when == TeleportAfterRaceWhen.PARTICIPANT_FINISHES) {
        PaperLib.teleportAsync(event.getPlayerSession().getPlayer(), event.getRaceSession().getRace().getSpawn(),
            PlayerTeleportEvent.TeleportCause.PLUGIN);
      }
    }
  }

  @EventHandler
  void onSessionStateChanged(SessionStateChangedEvent event) {
    if (event.getRaceSession().getState() == RaceSessionState.COUNTDOWN) {
      Bukkit.getPluginManager()
          .callEvent(new ExecuteCommandEvent(RaceCommandType.ON_COUNTDOWN, event.getRaceSession()));
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
      Bukkit.getPluginManager().callEvent(new DeleteRaceEvent(race));
    }
    racesByName.clear();
    races.clear();

    api.fetchAllRaces((List<Race> fetchedRaces) -> {
      for (Race race : fetchedRaces) {
        racesByName.put(race.getName(), race);
        races.add(race);
        Bukkit.getPluginManager().callEvent(new CreateRaceEvent(race));
      }
    });
  }

  public void updateRace(Race race, Runnable runnable) {
    api.updateRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
      if (result) {
        runnable.run();
      }
    }));
  }

  public void addCheckpoint(Location location, Race race, Consumer<RaceCheckpoint> consumer) {
    RaceCheckpoint checkpoint = new RaceCheckpoint(UUID.randomUUID(), race.getCheckpoints().size() + 1, location, 3);

    api.addCheckpoint(race.getId(), checkpoint,
        (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
          if (result) {
            race.addStartPoint(checkpoint);
            Bukkit.getPluginManager().callEvent(new AddRaceCheckpointEvent(race, checkpoint));
            consumer.accept(checkpoint);
          }
        }));
  }

  public void deleteCheckpoint(Race race, RaceCheckpoint checkpoint, Runnable runnable) {
    api.deleteCheckpoint(race.getId(), checkpoint,
        (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
          if (result) {
            race.setCheckpoints(
                race.getCheckpoints().stream().filter((RaceCheckpoint checkpoint1) -> checkpoint1 != checkpoint)
                    .peek((RaceCheckpoint checkpoint1) -> {
                      if (checkpoint1.getPosition() > checkpoint.getPosition()) {
                        checkpoint1.setPosition(checkpoint1.getPosition() - 1);
                      }
                    }).collect(Collectors.toList()));
            Bukkit.getPluginManager().callEvent(new DeleteRaceCheckpointEvent(race, checkpoint));
            runnable.run();
          }
        }));
  }

  public void createRace(Race race, Runnable callback) {
    api.updateRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
      if (result) {
        racesByName.put(race.getName(), race);
        races.add(race);
        Bukkit.getPluginManager().callEvent(new CreateRaceEvent(race));
        callback.run();
      }
    }));
  }

  public void deleteRace(Race race, Runnable runnable) {
    api.deleteRace(race, (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
      if (result) {
        racesByName.remove(race.getName());
        races.remove(race);
        Bukkit.getPluginManager().callEvent(new DeleteRaceEvent(race));
        runnable.run();
      }
    }));
  }

  public void addStartPoint(Location location, Race race, Consumer<RaceStartPoint> consumer) {
    RaceStartPoint startPoint = new RaceStartPoint(UUID.randomUUID(), race.getStartPoints().size() + 1, location);

    api.addStartPoint(race.getId(), startPoint,
        (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
          if (result) {
            race.addStartPoint(startPoint);
            Bukkit.getPluginManager().callEvent(new AddRaceStartPointEvent(race, startPoint));
            consumer.accept(startPoint);
          }
        }));
  }

  public void deleteStartPoint(Race race, RaceStartPoint startPoint, Runnable runnable) {
    api.deleteStartPoint(race.getId(), startPoint,
        (Boolean result) -> Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
          if (result) {
            race.setStartPoints(
                race.getStartPoints().stream().filter((RaceStartPoint startPoint1) -> startPoint1 != startPoint)
                    .peek((RaceStartPoint startPoint1) -> {
                      if (startPoint1.getPosition() > startPoint.getPosition()) {
                        startPoint1.setPosition(startPoint1.getPosition() - 1);
                      }
                    }).collect(Collectors.toList()));
            Bukkit.getPluginManager().callEvent(new DeleteRaceStartPointEvent(race, startPoint));
            runnable.run();
          }
        }));
  }

  public Race getRace(String name) {
    return racesByName.get(name);
  }

  public List<Race> getRaces() {
    return new ArrayList<>(races);
  }

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

    List<GameMode> preventJoinFromGameMode = Racing.getInstance().getConfiguration()
        .get(ConfigKey.PREVENT_JOIN_FROM_GAME_MODE);
    if (preventJoinFromGameMode.contains(player.getGameMode())) {
      MessageManager.setValue("game_mode", player.getGameMode());
      MessageManager.sendMessage(player, MessageKey.JOIN_RACE_GAME_MODE);
      return;
    }

    boolean startOnSign = Racing.getInstance().getConfiguration().get(ConfigKey.START_ON_JOIN_SIGN);
    boolean startOnCommand = Racing.getInstance().getConfiguration().get(ConfigKey.START_ON_JOIN_SIGN);

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

    Economy economy = Racing.getInstance().getEconomy();
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
    MessageManager.broadcast(MessageKey.JOIN_RACE_SUCCESS);
  }

  public StartRaceStatus tryStartRace(String raceName, CommandSender commandSender, int numLaps) {
    Race race = getRace(raceName);

    if (race == null) {
      List<Race> allRaces = races.stream().filter((Race r) -> r.getState() == RaceState.ENABLED)
          .collect(Collectors.toList());

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
    if(PaperLib.isPaper()) {
      return;
    }
    for (World world : Bukkit.getWorlds()) {
      world.removePluginChunkTickets(Racing.getInstance());
    }

    for (Race race : races) {
      if (race.getState() == RaceState.ENABLED) {
        continue;
      }

      for (RaceStartPoint startPoint : race.getStartPoints()) {
        startPoint.getLocation().getChunk().addPluginChunkTicket(Racing.getInstance());
      }

      for (RaceCheckpoint checkpoint : race.getCheckpoints()) {
        checkpoint.getLocation().getChunk().addPluginChunkTicket(Racing.getInstance());
      }

      race.getSpawn().getChunk().addPluginChunkTicket(Racing.getInstance());
    }
  }
}
