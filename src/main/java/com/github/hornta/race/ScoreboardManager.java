package com.github.hornta.race;

import java.util.HashMap;
import java.util.Map;

import com.github.hornta.carbon.message.MessageManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class ScoreboardManager {

  // Using teams to identify the various times, using invisible characters so they aren't displayed
  private final String WORLD_RECORD = ChatColor.AQUA.toString();
  private final String WORLD_RECORD_HOLDER = ChatColor.BLACK.toString();
  private final String WORLD_RECORD_FASTEST_LAP = ChatColor.BLUE.toString();
  private final String WORLD_RECORD_FASTEST_LAP_HOLDER = ChatColor.DARK_AQUA.toString();
  private final String PERSONAL_RECORD = ChatColor.DARK_BLUE.toString();
  private final String RACE_TIME = ChatColor.DARK_GRAY.toString();
  private final String RACE_CURRENT_LAP_TIME = ChatColor.DARK_GREEN.toString();
  private final String RACE_FASTEST_LAP_TIME = ChatColor.DARK_PURPLE.toString();
  private final String PERSONAL_RECORD_LAP_TIME = ChatColor.DARK_RED.toString();

  private final String SCOREBOARD_OBJECTIVE = "hornta.Racing";
  private final String HEADING = "heading";
  private final String NO_TIME_STATS = "noTimeStats";
  private final String NO_NAME_STATS = "noNameStats";
  private final String LAP_TAG = "personalRecord";
  
  private final int rowsNeeded;

  //loaded from config files
  private final boolean enabled;
  private final String headingFormat;
  private final String titleFormat;
  private final String textFormat;
  private final boolean displayMillis;

  private Map<String, Boolean> configMap = new HashMap<>();
  private Map<String, String> translationMap = new HashMap<>();

  /// Public Functions

  public ScoreboardManager()
  {
    this.enabled = Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_ENABLED);
    this.headingFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_HEADING_FORMAT);
    this.titleFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_TITLE_FORMAT);
    this.textFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_TEXT_FORMAT);
    this.displayMillis = Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_DISPLAY_MILLISECONDS);

    configMap.put(WORLD_RECORD, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD));
    configMap.put(WORLD_RECORD_HOLDER, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_HOLDER));
    configMap.put(WORLD_RECORD_FASTEST_LAP, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP));
    configMap.put(WORLD_RECORD_FASTEST_LAP_HOLDER, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP_HOLDER));
    configMap.put(PERSONAL_RECORD, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD));
    configMap.put(PERSONAL_RECORD_LAP_TIME, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD_FASTEST_LAP));
    configMap.put(RACE_TIME, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_TIME));
    configMap.put(RACE_CURRENT_LAP_TIME, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_LAP_TIME));
    configMap.put(RACE_FASTEST_LAP_TIME, Racing.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_FASTEST_LAP));

    translationMap.put(HEADING, convertHeading("Racing", 1));
    translationMap.put(NO_TIME_STATS, MessageManager.getMessage(MessageKey.SCOREBOARD_NO_TIME_STATS));
    translationMap.put(NO_NAME_STATS, MessageManager.getMessage(MessageKey.SCOREBOARD_NO_NAME_STATS));
    translationMap.put(WORLD_RECORD, MessageManager.getMessage(MessageKey.SCOREBOARD_WORLD_RECORD));
    translationMap.put(WORLD_RECORD_FASTEST_LAP, MessageManager.getMessage(MessageKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP));
    translationMap.put(PERSONAL_RECORD, MessageManager.getMessage(MessageKey.SCOREBOARD_PERSONAL_RECORD));
    translationMap.put(RACE_TIME, MessageManager.getMessage(MessageKey.SCOREBOARD_TIME));
    translationMap.put(RACE_FASTEST_LAP_TIME, MessageManager.getMessage(MessageKey.SCOREBOARD_FASTEST_LAP));
    translationMap.put(LAP_TAG, MessageManager.getMessage(MessageKey.SCOREBOARD_LAP_TAG));

    this.rowsNeeded = calculateNumberOfRowsNeeded();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void addScoreboard(Player player, String raceName, int laps)
  {
    if (this.enabled)
    {
      translationMap.put(HEADING, convertHeading(raceName, laps));
      Scoreboard board = setupScoreboard(player);
      Team team = board.registerNewTeam(SCOREBOARD_OBJECTIVE);
      team.addEntry(player.getName());
  
      player.setScoreboard(board);
    }
  }
  
  public void removeScoreboard(Player player) {
    if (this.enabled){
      player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
  }

  public void updateWorldRecord(Player player, long timeMillis)
  {
    updateTime(player, timeMillis, WORLD_RECORD);
  }

  public void updateWorldRecordHolder(Player player, String name)
  {
    Scoreboard board = player.getScoreboard();
    if (board != null && name != "" && configMap.get(WORLD_RECORD_HOLDER))
    {
      board.getTeam(WORLD_RECORD_HOLDER).setPrefix(convertText(name));
    }
  }

  public void updateWorldRecordFastestLap(Player player, long timeMillis)
  {
    updateTime(player, timeMillis, WORLD_RECORD_FASTEST_LAP);
  }

  public void updateWorldRecordFastestLapHolder(Player player, String name)
  {
    Scoreboard board = player.getScoreboard();
    if (board != null && name != "" && configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER))
    {
      board.getTeam(WORLD_RECORD_FASTEST_LAP_HOLDER).setPrefix(convertText(name));
    }
  }

  public void updatePersonalBest(Player player, long timeMillis)
  {
    updateTime(player, timeMillis, PERSONAL_RECORD);
  }

  public void updateRaceTime(Player player, long liveTimeMillis)
  {
    updateTime(player, liveTimeMillis, RACE_TIME);
  }
  
  public void updateRaceCurrentLapTime(Player player, long liveTimeMillis)
  {
    updateTime(player, liveTimeMillis, RACE_CURRENT_LAP_TIME);
  }

  public void updateRaceFastestLap(Player player, long fastestLapTime)
  {
    updateTime(player, fastestLapTime, RACE_FASTEST_LAP_TIME);
  }

  public void updatePersonalBestLapTime(Player player, long pbLapTime)
  {
    String value = (pbLapTime != Long.MAX_VALUE) ? formatTime(pbLapTime) : translationMap.get(NO_TIME_STATS);
    updateString(player, value+translationMap.get(LAP_TAG), PERSONAL_RECORD_LAP_TIME);
  }

  private void updateTime(Player player, long timeMillis, String scoreboardTeam)
  {
    if(timeMillis != Long.MAX_VALUE)
    {
      updateString(player, formatTime(timeMillis), scoreboardTeam);
    }
    else
    {
      updateString(player, translationMap.get(NO_TIME_STATS), scoreboardTeam);
    }
  }

  private void updateString(Player player, String value, String scoreboardTeam)
  {
    if(this.enabled)
    {
      Scoreboard board = player.getScoreboard();
      if (board != null && configMap.get(scoreboardTeam))
      {
        board.getTeam(scoreboardTeam).setPrefix(convertText(value));
      }
    }
  }

  private Scoreboard setupScoreboard(Player player)
  {
    String mainHeading = translationMap.get(HEADING);

    Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
    Objective objective = board.registerNewObjective(player.getName(), SCOREBOARD_OBJECTIVE, mainHeading);
    objective.setDisplaySlot(DisplaySlot.SIDEBAR);

    PlayerScoreboard playerScoreboard = new PlayerScoreboard(board, objective);

    addWorldRecords(playerScoreboard);
    addWorldRecordsFastestLap(playerScoreboard);
    addPersonalRecords(playerScoreboard);
    addRaceTime(playerScoreboard);
    addRaceFastestLapTime(playerScoreboard);

    return board;
  }

  /**
   * Convert milliseconds into formatted time HH:MM:SS(.sss)
   *
   * @param millis
   * @return formatted time: HH:MM:SS.(sss)
   */
  private String formatTime(long millis) {
    MillisecondConverter time = new MillisecondConverter(millis);
    String pattern = this.displayMillis ? "%02d:%02d:%02d.%03d" : "%02d:%02d:%02d";
    return String.format(pattern, time.getHours(), time.getMinutes(), time.getSeconds(), time.getMilliseconds());
  }

  private void addWorldRecords(PlayerScoreboard playerBoard) {
    if(configMap.get(WORLD_RECORD) || configMap.get(WORLD_RECORD_HOLDER))
    {
      Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(WORLD_RECORD)));
      onlineName.setScore(playerBoard.decreaseAndGetCount());

      if(configMap.get(WORLD_RECORD))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), WORLD_RECORD);
      }
      if(configMap.get(WORLD_RECORD_HOLDER))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), WORLD_RECORD_HOLDER);
      }
    }
  }

  private void addWorldRecordsFastestLap(PlayerScoreboard playerBoard) {
    if(configMap.get(WORLD_RECORD_FASTEST_LAP) || configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER))
    {
      Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(WORLD_RECORD_FASTEST_LAP)));
      onlineName.setScore(playerBoard.decreaseAndGetCount());

      if(configMap.get(WORLD_RECORD_FASTEST_LAP))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), WORLD_RECORD_FASTEST_LAP);
      }
      if(configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), WORLD_RECORD_FASTEST_LAP_HOLDER);
      }
    }
  }

  private void addPersonalRecords(PlayerScoreboard playerBoard) {
    if(configMap.get(PERSONAL_RECORD) || configMap.get(PERSONAL_RECORD_LAP_TIME))
    {
      Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(PERSONAL_RECORD)));
      onlineName.setScore(playerBoard.decreaseAndGetCount());

      if(configMap.get(PERSONAL_RECORD))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), PERSONAL_RECORD);
      }
      if(configMap.get(PERSONAL_RECORD_LAP_TIME))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), PERSONAL_RECORD_LAP_TIME);
      }
    }
  }

  private void addRaceTime(PlayerScoreboard playerBoard) {
    if(configMap.get(RACE_TIME) || configMap.get(RACE_CURRENT_LAP_TIME))
    {
      Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(RACE_TIME)));
      onlineName.setScore(playerBoard.decreaseAndGetCount());

      if(configMap.get(RACE_TIME))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), RACE_TIME);
      }
      if(configMap.get(RACE_CURRENT_LAP_TIME))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), RACE_CURRENT_LAP_TIME);
      }
    }
  }

  private void addRaceFastestLapTime(PlayerScoreboard playerBoard)
  {
    if(configMap.get(RACE_FASTEST_LAP_TIME))
    {
      Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(RACE_FASTEST_LAP_TIME)));
      onlineName.setScore(playerBoard.decreaseAndGetCount());

      if(configMap.get(RACE_FASTEST_LAP_TIME))
      {
        addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), RACE_FASTEST_LAP_TIME);
      }
    }
  }

  private void addTeamToEntry(PlayerScoreboard playerBoard, String initialValue, String scoreboardKey){
    Team displayName = playerBoard.scoreboard.registerNewTeam(scoreboardKey);
    displayName.addEntry(scoreboardKey);
    displayName.setPrefix(convertText(initialValue));
    playerBoard.objective.getScore(scoreboardKey).setScore(playerBoard.decreaseAndGetCount());
  }

  private String convertHeading(String heading, int laps) {
    return headingFormat.replace("<heading>", heading).replace("<laps>", Integer.toString(laps));
  }

  private String convertTitle(String title) {
    return titleFormat.replace("<title>", title);
  }

  private String convertText(String value) {
    return textFormat.replace("<text>", value);
  }

  private int calculateNumberOfRowsNeeded() {
    int rowsNeeded = 0;
    if(configMap.get(WORLD_RECORD) || configMap.get(WORLD_RECORD_HOLDER))
    {
      rowsNeeded += (configMap.get(WORLD_RECORD) && configMap.get(WORLD_RECORD_HOLDER)) ? 3 : 2;
    }
    if(configMap.get(WORLD_RECORD_FASTEST_LAP) || configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER))
    {
      rowsNeeded += (configMap.get(WORLD_RECORD_FASTEST_LAP) && configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) ? 3 : 2;
    }
    if(configMap.get(PERSONAL_RECORD) || configMap.get(PERSONAL_RECORD_LAP_TIME))
    {
      rowsNeeded += (configMap.get(PERSONAL_RECORD) && configMap.get(PERSONAL_RECORD_LAP_TIME)) ? 3 : 2;
    }
    if(configMap.get(RACE_TIME) || configMap.get(RACE_CURRENT_LAP_TIME))
    {
      rowsNeeded += (configMap.get(RACE_TIME) && configMap.get(RACE_CURRENT_LAP_TIME)) ? 3 : 2;
    }
    rowsNeeded += configMap.get(RACE_FASTEST_LAP_TIME) ? 2 : 0;
    return rowsNeeded;
  }

  private class PlayerScoreboard {
    private int scoreboardCount = rowsNeeded;
    private Scoreboard scoreboard;
    private Objective objective;

    public PlayerScoreboard(Scoreboard scoreboard, Objective objective) {
      this.scoreboard = scoreboard;
      this.objective = objective;
    }

    public int decreaseAndGetCount() {
      return --scoreboardCount;
    }
  }

  private class MillisecondConverter {
    private long milliseconds;
    private long seconds;
    private long minutes;
    private long hours;
    /**
     * Convert milliseconds into different divisions
     * @param millis
     */
    public MillisecondConverter(long millis) {
      this.milliseconds = millis;
      this.seconds = millis / 1000;
      this.minutes = seconds / 60;
      this.hours = minutes / 60;
    }

    public long getMilliseconds() {
      return milliseconds % 1000;
    }

    public long getSeconds() {
      return seconds % 60;
    }

    public long getMinutes() {
      return minutes % 60;
    }

    public long getHours() {
      return hours % 24;
    }
  }
}
