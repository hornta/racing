package com.github.hornta.race.objects;

import com.github.hornta.race.enums.RaceStatType;
import com.github.hornta.race.enums.RaceState;
import com.github.hornta.race.enums.RaceType;
import com.github.hornta.race.enums.RaceVersion;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.util.*;

public class Race implements Listener {
  private final UUID id;
  private RaceVersion version;
  private String name;
  private Location spawn;
  private List<RaceCheckpoint> checkpoints;
  private List<RaceStartPoint> startPoints;
  private RaceState state;
  private Instant createdAt;
  private RaceType type;
  private String song;
  private double entryFee;
  private float walkSpeed;
  private Set<RacePotionEffect> potionEffects;
  private Set<RaceSign> signs;
  private int minimimRequiredParticipantsToStart;
  private double pigSpeed;
  private double horseSpeed;
  private double horseJumpStrength;

  private Map<UUID, RacePlayerStatistic> resultByPlayerId = new HashMap<>();
  private Map<RaceStatType, Set<RacePlayerStatistic>> resultsByStat = new HashMap<>();
  private List<RaceCommand> commands;

  public Race(
    UUID id,
    RaceVersion version,
    String name,
    Location spawn,
    RaceState state,
    Instant createdAt,
    List<RaceCheckpoint> checkpoints,
    List<RaceStartPoint> startPoints,
    RaceType type,
    String song,
    double entryFee,
    float walkSpeed,
    Set<RacePotionEffect> potionEffects,
    Set<RaceSign> signs,
    Set<RacePlayerStatistic> results,
    int minimimRequiredPlayersToStart,
    double pigSpeed,
    double horseSpeed,
    double horseJumpStrength,
    List<RaceCommand> commands
  ) {
    this.id = id;
    this.version = version;
    this.name = name;
    this.spawn = spawn;
    this.state = state;
    this.createdAt = createdAt;
    this.checkpoints = new ArrayList<>(checkpoints);
    this.startPoints = new ArrayList<>(startPoints);
    this.type = type;
    this.song = song;
    this.entryFee = entryFee;
    this.walkSpeed = walkSpeed;
    this.potionEffects = potionEffects;
    this.signs = signs;
    this.minimimRequiredParticipantsToStart = minimimRequiredPlayersToStart;
    this.pigSpeed = pigSpeed;
    this.horseSpeed = horseSpeed;
    this.horseJumpStrength = horseJumpStrength;

    for (RacePlayerStatistic playerStatistic : results) {
      resultByPlayerId.put(playerStatistic.getPlayerId(), playerStatistic);
    }

    for(RaceStatType statType : RaceStatType.values()) {
      Set<RacePlayerStatistic> stats = new TreeSet<>((RacePlayerStatistic o1, RacePlayerStatistic o2) -> {
        int order;
        switch (statType) {
          case WINS:
            order = o2.getWins() - o1.getWins();
            break;
          case FASTEST_LAP:
            order = (int)(o1.getFastestLap() - o2.getFastestLap());
            break;
          case WIN_RATIO:
            order = (int)((float)o2.getWins() / o2.getRuns() * 100 - (float)o1.getWins() / o1.getRuns() * 100);
            break;
          case RUNS:
            order = o2.getRuns() - o1.getRuns();
            break;
          case FASTEST:
            //can't do anything here, as we would have to create many sorted results for 1 stat type
          default:
            order = 0;
        }

        if(order == 0) {
          return o1.getPlayerId().compareTo(o2.getPlayerId());
        } else {
          return order;
        }
      });
      resultsByStat.put(statType, stats);
      stats.addAll(results);
    }

    this.commands = commands;
  }

  public void addResult(PlayerSessionResult result) {
    RacePlayerStatistic playerStatistic = resultByPlayerId.get(result.getPlayerSession().getPlayerId());
    RacePlayerStatistic newStat;
    if(playerStatistic == null) {
      Map<Integer, Long> records = new HashMap<Integer, Long>();
      records.put(result.getPlayerSession().getCurrentLap(), result.getTime());
      newStat = new RacePlayerStatistic(
        result.getPlayerSession().getPlayerId(),
        result.getPlayerSession().getPlayerName(),
        result.getPosition() == 1 ? 1 : 0,
        1,
        result.getPlayerSession().getFastestLapTime(),
        records
      );
    } else {
      newStat = playerStatistic.clone();
      newStat.setPlayerName(result.getPlayerSession().getPlayerName());
      newStat.setRuns(newStat.getRuns() + 1);
      if (result.getPosition() == 1) {
        newStat.setWins(newStat.getWins() + 1);
      }
      if(newStat.getFastestLap() > result.getPlayerSession().getFastestLapTime()) {
        newStat.setFastestLap(result.getPlayerSession().getFastestLapTime());
      }
      int laps = result.getPlayerSession().getCurrentLap();
      if(newStat.getRecord(laps) > result.getTime())
      {
        newStat.setRecord(laps, result.getTime());
      }
    }

    resultByPlayerId.put(newStat.getPlayerId(), newStat);

    for (RaceStatType statType : RaceStatType.values()) {
      Set<RacePlayerStatistic> resultSet = resultsByStat.get(statType);

      if(playerStatistic != null) {
        resultSet.remove(playerStatistic);
      }
      resultSet.add(newStat);
    }
  }

  public void resetResults() {
    resultByPlayerId.clear();
    for (RaceStatType statType : RaceStatType.values()) {
      resultsByStat.get(statType).clear();
    }
  }

  public Set<RacePlayerStatistic> getResults(RaceStatType type) {
    return resultsByStat.get(type);
  }

  public Set<RacePlayerStatistic> getResultsForLapCount(int laps) {

      Set<RacePlayerStatistic> stats = new TreeSet<>((RacePlayerStatistic o1, RacePlayerStatistic o2) -> {
        int order = (int)(o1.getRecord(laps) - o2.getRecord(laps));
        if(order == 0) {
          return o1.getPlayerId().compareTo(o2.getPlayerId());
        } else {
          return order;
        }
      });
      stats.addAll(getResults(RaceStatType.FASTEST_LAP));
      stats.removeIf(entry -> {
        return entry.getRecord(laps) == Long.MAX_VALUE;
      });
      return stats;   
  }

  public Map<UUID, RacePlayerStatistic> getResultByPlayerId() {
    return resultByPlayerId;
  }

  public Set<RaceSign> getSigns() {
    return signs;
  }

  public Set<RacePotionEffect> getPotionEffects() {
    return potionEffects;
  }

  public void addPotionEffect(RacePotionEffect potionEffect) {
    removePotionEffect(potionEffect.getType());
    potionEffects.add(potionEffect);
  }

  public void removePotionEffect(PotionEffectType type) {
    Iterator<RacePotionEffect> it = potionEffects.iterator();
    while(it.hasNext()) {
      if(((RacePotionEffect)it.next()).getType() == type) {
        it.remove();
        return;
      }
    }
  }

  public void clearPotionEffects() {
    potionEffects.clear();
  }

  public float getWalkSpeed() {
    return walkSpeed;
  }

  public void setWalkSpeed(float walkSpeed) {
    this.walkSpeed = walkSpeed;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Location getSpawn() {
    return spawn.clone();
  }

  public void setSpawn(Location spawn) {
    this.spawn = spawn;
  }

  public List<RaceCheckpoint> getCheckpoints() {
    return new ArrayList<>(checkpoints);
  }

  public List<RaceStartPoint> getStartPoints() {
    return new ArrayList<>(startPoints);
  }

  public RaceCheckpoint getCheckpoint(int position) {
    for(RaceCheckpoint checkpoint : checkpoints) {
      if(checkpoint.getPosition() == position) {
        return checkpoint;
      }
    }
    return null;
  }

  public RaceCheckpoint getCheckpoint(Location location) {
    for(RaceCheckpoint checkpoint : checkpoints) {
      if(
        checkpoint.getLocation().getBlockX() == location.getBlockX() &&
        checkpoint.getLocation().getBlockY() == location.getBlockY() &&
        checkpoint.getLocation().getBlockZ() == location.getBlockZ()) {
        return checkpoint;
      }
    }
    return null;
  }

  public RaceStartPoint getStartPoint(int position) {
    for(RaceStartPoint startPoint : startPoints) {
      if(startPoint.getPosition() == position) {
        return startPoint;
      }
    }
    return null;
  }

  public RaceStartPoint getStartPoint(Location location) {
    for(RaceStartPoint startPoint : startPoints) {
      if(
        startPoint.getLocation().getBlockX() == location.getBlockX() &&
        startPoint.getLocation().getBlockY() == location.getBlockY() &&
        startPoint.getLocation().getBlockZ() == location.getBlockZ()
      ) {
        return startPoint;
      }
    }
    return null;
  }

  public RaceState getState() {
    return state;
  }

  public void setState(RaceState state) {
    this.state = state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void addStartPoint(RaceCheckpoint point) {
    checkpoints.add(point);
  }

  public void addStartPoint(RaceStartPoint startPoint) {
    startPoints.add(startPoint);
  }

  public void setStartPoints(List<RaceStartPoint> startPoints) {
    this.startPoints = startPoints;
  }

  public void setCheckpoints(List<RaceCheckpoint> checkpoints) {
    this.checkpoints = checkpoints;
  }

  public RaceType getType() {
    return type;
  }

  public void setType(RaceType type) {
    this.type = type;
  }

  public String getSong() {
    return song;
  }

  public void setSong(String song) {
    this.song = song;
  }

  public double getEntryFee() {
    return entryFee;
  }

  public void setEntryFee(double entryFee) {
    this.entryFee = entryFee;
  }

  public RaceVersion getVersion() {
    return version;
  }

  public int getMinimimRequiredParticipantsToStart() {
    return minimimRequiredParticipantsToStart;
  }

  public double getPigSpeed() {
    return pigSpeed;
  }

  public void setPigSpeed(double pigSpeed) {
    this.pigSpeed = pigSpeed;
  }

  public double getHorseJumpStrength() {
    return horseJumpStrength;
  }

  public void setHorseJumpStrength(double horseJumpStrength) {
    this.horseJumpStrength = horseJumpStrength;
  }

  public double getHorseSpeed() {
    return horseSpeed;
  }

  public void setHorseSpeed(double horseSpeed) {
    this.horseSpeed = horseSpeed;
  }

  public List<RaceCommand> getCommands() {
    return commands;
  }
}
