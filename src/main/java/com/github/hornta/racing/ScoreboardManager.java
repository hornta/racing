package com.github.hornta.racing;

import com.github.hornta.racing.events.ConfigReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.*;
import se.hornta.messenger.MessageManager;

import java.util.HashMap;
import java.util.Map;

public class ScoreboardManager implements Listener {
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
	private final String HEADING = "heading";
	private final String NO_TIME_STATS = "noTimeStats";
	private final String NO_NAME_STATS = "noNameStats";
	private final String LAP_TAG = "personalRecord";
	//loaded from config files
	private final String headingFormat;
	private final String titleFormat;
	private final String textFormat;
	private final Map<String, Boolean> configMap = new HashMap<>();
	private final Map<String, String> translationMap = new HashMap<>();
	private int rowsNeeded;
	private boolean displayMillis;
	/// Public Functions

	public ScoreboardManager() {
		headingFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_HEADING_FORMAT);
		titleFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_TITLE_FORMAT);
		textFormat = MessageManager.getMessage(MessageKey.SCOREBOARD_TEXT_FORMAT);
		displayMillis = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_DISPLAY_MILLISECONDS);
		configMap.put(WORLD_RECORD, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD));
		configMap.put(WORLD_RECORD_HOLDER, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_HOLDER));
		configMap.put(WORLD_RECORD_FASTEST_LAP, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP));
		configMap.put(WORLD_RECORD_FASTEST_LAP_HOLDER, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP_HOLDER));
		configMap.put(PERSONAL_RECORD, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD));
		configMap.put(PERSONAL_RECORD_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD_FASTEST_LAP));
		configMap.put(RACE_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_TIME));
		configMap.put(RACE_CURRENT_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_LAP_TIME));
		configMap.put(RACE_FASTEST_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_FASTEST_LAP));
		translationMap.put(HEADING, convertHeading("Racing", 1));
		translationMap.put(NO_TIME_STATS, MessageManager.getMessage(MessageKey.SCOREBOARD_NO_TIME_STATS));
		translationMap.put(NO_NAME_STATS, MessageManager.getMessage(MessageKey.SCOREBOARD_NO_NAME_STATS));
		translationMap.put(WORLD_RECORD, MessageManager.getMessage(MessageKey.SCOREBOARD_WORLD_RECORD));
		translationMap.put(WORLD_RECORD_FASTEST_LAP, MessageManager.getMessage(MessageKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP));
		translationMap.put(PERSONAL_RECORD, MessageManager.getMessage(MessageKey.SCOREBOARD_PERSONAL_RECORD));
		translationMap.put(RACE_TIME, MessageManager.getMessage(MessageKey.SCOREBOARD_TIME));
		translationMap.put(RACE_FASTEST_LAP_TIME, MessageManager.getMessage(MessageKey.SCOREBOARD_FASTEST_LAP));
		translationMap.put(LAP_TAG, MessageManager.getMessage(MessageKey.SCOREBOARD_LAP_TAG));
		rowsNeeded = calculateNumberOfRowsNeeded();
	}

	@EventHandler
	void onConfigReloaded(ConfigReloadedEvent event) {
		displayMillis = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_DISPLAY_MILLISECONDS);
		configMap.clear();
		configMap.put(WORLD_RECORD, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD));
		configMap.put(WORLD_RECORD_HOLDER, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_HOLDER));
		configMap.put(WORLD_RECORD_FASTEST_LAP, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP));
		configMap.put(WORLD_RECORD_FASTEST_LAP_HOLDER, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_WORLD_RECORD_FASTEST_LAP_HOLDER));
		configMap.put(PERSONAL_RECORD, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD));
		configMap.put(PERSONAL_RECORD_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_PERSONAL_RECORD_FASTEST_LAP));
		configMap.put(RACE_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_TIME));
		configMap.put(RACE_CURRENT_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_LAP_TIME));
		configMap.put(RACE_FASTEST_LAP_TIME, RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_FASTEST_LAP));
		rowsNeeded = calculateNumberOfRowsNeeded();
	}

	public void addScoreboard(Player player, String raceName, int laps) {
		translationMap.put(HEADING, convertHeading(raceName, laps));
		String mainHeading = translationMap.get(HEADING);
		Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
		String SCOREBOARD_OBJECTIVE = "hornta.Racing";
		Objective objective = board.registerNewObjective(player.getName(), SCOREBOARD_OBJECTIVE, mainHeading);
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		PlayerScoreboard playerScoreboard = new PlayerScoreboard(board, objective);
		addWorldRecords(playerScoreboard);
		addWorldRecordsFastestLap(playerScoreboard);
		addPersonalRecords(playerScoreboard);
		addRaceTime(playerScoreboard);
		addRaceFastestLapTime(playerScoreboard);
		Team team = board.registerNewTeam(SCOREBOARD_OBJECTIVE);
		team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
		team.addEntry(player.getName());
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_ENABLED)) {
			player.setScoreboard(board);
		}
	}

	public void removeScoreboard(Player player) {
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	public void updateWorldRecord(Player player, long timeMillis) {
		updateTime(player, timeMillis, WORLD_RECORD);
	}

	public void updateWorldRecordHolder(Player player, String name) {
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_ENABLED)) {
			Scoreboard board = player.getScoreboard();
			if (!name.isEmpty() && configMap.get(WORLD_RECORD_HOLDER)) {
				board.getTeam(WORLD_RECORD_HOLDER).setPrefix(convertText(name));
			}
		}
	}

	public void updateWorldRecordFastestLap(Player player, long timeMillis) {
		updateTime(player, timeMillis, WORLD_RECORD_FASTEST_LAP);
	}

	public void updateWorldRecordFastestLapHolder(Player player, String name) {
		Scoreboard board = player.getScoreboard();
		if (!name.isEmpty() && configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) {
			board.getTeam(WORLD_RECORD_FASTEST_LAP_HOLDER).setPrefix(convertText(name));
		}
	}

	public void updatePersonalBest(Player player, long timeMillis) {
		updateTime(player, timeMillis, PERSONAL_RECORD);
	}

	public void updateRaceTime(Player player, long liveTimeMillis) {
		updateTime(player, liveTimeMillis, RACE_TIME);
	}

	public void updateRaceCurrentLapTime(Player player, long liveTimeMillis) {
		updateTime(player, liveTimeMillis, RACE_CURRENT_LAP_TIME);
	}

	public void updateRaceFastestLap(Player player, long fastestLapTime) {
		updateTime(player, fastestLapTime, RACE_FASTEST_LAP_TIME);
	}

	public void updatePersonalBestLapTime(Player player, long pbLapTime) {
		String value = (pbLapTime != Long.MAX_VALUE) ? formatTime(pbLapTime) : translationMap.get(NO_TIME_STATS);
		updateString(player, value + translationMap.get(LAP_TAG), PERSONAL_RECORD_LAP_TIME);
	}

	private void updateTime(Player player, long timeMillis, String scoreboardTeam) {
		if (timeMillis != Long.MAX_VALUE) {
			updateString(player, formatTime(timeMillis), scoreboardTeam);
		} else {
			updateString(player, translationMap.get(NO_TIME_STATS), scoreboardTeam);
		}
	}

	private void updateString(Player player, String value, String scoreboardTeam) {
		if (RacingPlugin.getInstance().getConfiguration().get(ConfigKey.SCOREBOARD_ENABLED)) {
			Scoreboard board = player.getScoreboard();
			if (configMap.get(scoreboardTeam)) {
				Team team = board.getTeam(scoreboardTeam);
				team.setPrefix(convertText(value));
			}
		}
	}

	private String formatTime(long millis) {
		MillisecondConverter time = new MillisecondConverter(millis);
		String pattern = displayMillis ? "%02d:%02d:%02d.%03d" : "%02d:%02d:%02d";
		return String.format(pattern, time.getHours(), time.getMinutes(), time.getSeconds(), time.getMilliseconds());
	}

	private void addWorldRecords(PlayerScoreboard playerBoard) {
		if (configMap.get(WORLD_RECORD) || configMap.get(WORLD_RECORD_HOLDER)) {
			Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(WORLD_RECORD)));
			onlineName.setScore(playerBoard.decreaseAndGetCount());
			if (configMap.get(WORLD_RECORD)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), WORLD_RECORD);
			}
			if (configMap.get(WORLD_RECORD_HOLDER)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), WORLD_RECORD_HOLDER);
			}
		}
	}

	private void addWorldRecordsFastestLap(PlayerScoreboard playerBoard) {
		if (configMap.get(WORLD_RECORD_FASTEST_LAP) || configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) {
			Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(WORLD_RECORD_FASTEST_LAP)));
			onlineName.setScore(playerBoard.decreaseAndGetCount());
			if (configMap.get(WORLD_RECORD_FASTEST_LAP)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), WORLD_RECORD_FASTEST_LAP);
			}
			if (configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), WORLD_RECORD_FASTEST_LAP_HOLDER);
			}
		}
	}

	private void addPersonalRecords(PlayerScoreboard playerBoard) {
		if (configMap.get(PERSONAL_RECORD) || configMap.get(PERSONAL_RECORD_LAP_TIME)) {
			Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(PERSONAL_RECORD)));
			onlineName.setScore(playerBoard.decreaseAndGetCount());
			if (configMap.get(PERSONAL_RECORD)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), PERSONAL_RECORD);
			}
			if (configMap.get(PERSONAL_RECORD_LAP_TIME)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), PERSONAL_RECORD_LAP_TIME);
			}
		}
	}

	private void addRaceTime(PlayerScoreboard playerBoard) {
		if (configMap.get(RACE_TIME) || configMap.get(RACE_CURRENT_LAP_TIME)) {
			Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(RACE_TIME)));
			onlineName.setScore(playerBoard.decreaseAndGetCount());
			if (configMap.get(RACE_TIME)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), RACE_TIME);
			}
			if (configMap.get(RACE_CURRENT_LAP_TIME)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_NAME_STATS), RACE_CURRENT_LAP_TIME);
			}
		}
	}

	private void addRaceFastestLapTime(PlayerScoreboard playerBoard) {
		if (configMap.get(RACE_FASTEST_LAP_TIME)) {
			Score onlineName = playerBoard.objective.getScore(convertTitle(translationMap.get(RACE_FASTEST_LAP_TIME)));
			onlineName.setScore(playerBoard.decreaseAndGetCount());
			if (configMap.get(RACE_FASTEST_LAP_TIME)) {
				addTeamToEntry(playerBoard, translationMap.get(NO_TIME_STATS), RACE_FASTEST_LAP_TIME);
			}
		}
	}

	private void addTeamToEntry(PlayerScoreboard playerBoard, String initialValue, String scoreboardKey) {
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
		if (configMap.get(WORLD_RECORD) || configMap.get(WORLD_RECORD_HOLDER)) {
			rowsNeeded += (configMap.get(WORLD_RECORD) && configMap.get(WORLD_RECORD_HOLDER)) ? 3 : 2;
		}
		if (configMap.get(WORLD_RECORD_FASTEST_LAP) || configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) {
			rowsNeeded += (configMap.get(WORLD_RECORD_FASTEST_LAP) && configMap.get(WORLD_RECORD_FASTEST_LAP_HOLDER)) ? 3 : 2;
		}
		if (configMap.get(PERSONAL_RECORD) || configMap.get(PERSONAL_RECORD_LAP_TIME)) {
			rowsNeeded += (configMap.get(PERSONAL_RECORD) && configMap.get(PERSONAL_RECORD_LAP_TIME)) ? 3 : 2;
		}
		if (configMap.get(RACE_TIME) || configMap.get(RACE_CURRENT_LAP_TIME)) {
			rowsNeeded += (configMap.get(RACE_TIME) && configMap.get(RACE_CURRENT_LAP_TIME)) ? 3 : 2;
		}
		rowsNeeded += configMap.get(RACE_FASTEST_LAP_TIME) ? 2 : 0;
		return rowsNeeded;
	}

	private static class MillisecondConverter {
		private final long milliseconds;
		private final long seconds;
		private final long minutes;
		private final long hours;

		/**
		 * Convert milliseconds into different divisions
		 *
		 * @param millis
		 */
		public MillisecondConverter(long millis) {
			milliseconds = millis;
			seconds = millis / 1000;
			minutes = seconds / 60;
			hours = minutes / 60;
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

	private class PlayerScoreboard {
		private final Scoreboard scoreboard;
		private final Objective objective;
		private int scoreboardCount = rowsNeeded;

		public PlayerScoreboard(Scoreboard scoreboard, Objective objective) {
			this.scoreboard = scoreboard;
			this.objective = objective;
		}

		public int decreaseAndGetCount() {
			return --scoreboardCount;
		}
	}
}
