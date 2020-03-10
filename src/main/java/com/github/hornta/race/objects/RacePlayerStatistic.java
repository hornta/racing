package com.github.hornta.race.objects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RacePlayerStatistic {
  private UUID playerId;
  private String playerName;
  private int wins;
  private int runs;
  private long fastestLap;
  private Map<Integer, Long> records = new HashMap<>();

  public RacePlayerStatistic(UUID playerId, String playerName, int wins, int runs, long fastestLap, Map<Integer, Long> records) {
    this.playerId = playerId;
    this.playerName = playerName;
    this.wins = wins;
    this.runs = runs;
    this.fastestLap = fastestLap;
    this.records = records;
  }

  public RacePlayerStatistic clone() {
    return new RacePlayerStatistic(playerId, playerName, wins, runs, fastestLap, records);
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public String getPlayerName() {
    return playerName;
  }

  public long getFastestLap() {
    return fastestLap;
  }

  public int getRuns() {
    return runs;
  }

  public int getWins() {
    return wins;
  }

  public long getRecord(int laps)
  {
    if(records.containsKey(laps))
    {
      return ((Number)records.get(laps)).longValue();
    }
    else
    {
      return Long.MAX_VALUE;
    }
  }

  public Map<Integer, Long> getRecords()
  {
    return records;
  }

  public void setPlayerName(String playerName) {
    this.playerName = playerName;
  }

  public void setRuns(int runs) {
    this.runs = runs;
  }

  public void setFastestLap(long time) {
    this.fastestLap = time;
  }

  public void setWins(int wins) {
    this.wins = wins;
  }

  public void setRecord(int laps, long time)
  {
    if((records.containsKey(laps) && ((Number)records.get(laps)).longValue() > time) || !records.containsKey(laps))
    {
      records.put(laps, time);
    }
  }
}
