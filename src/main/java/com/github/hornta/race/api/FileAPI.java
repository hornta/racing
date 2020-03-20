package com.github.hornta.race.api;

import com.github.hornta.race.ConfigKey;
import com.github.hornta.race.Racing;
import com.github.hornta.race.api.migrations.*;
import com.github.hornta.race.enums.RaceCommandType;
import com.github.hornta.race.enums.RaceState;
import com.github.hornta.race.enums.RaceType;
import com.github.hornta.race.enums.RaceVersion;
import com.github.hornta.race.objects.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class FileAPI implements RacingAPI {
  private static final String ID_FIELD = "id";
  private static final String VERSION_FIELD = "version";
  private static final String NAME_FIELD = "name";
  private static final String STATE_FIELD = "state";
  private static final String TYPE_FIELD = "type";
  private static final String SONG_FIELD = "song";
  private static final String CREATED_AT_FIELD = "created_at";
  public static final String ENTRY_FEE_FIELD = "entry_fee";
  public static final String WALK_SPEED_FIELD = "walk_speed";
  private static final String SPAWN_X = "spawn.x";
  private static final String SPAWN_Y = "spawn.y";
  private static final String SPAWN_Z = "spawn.z";
  private static final String SPAWN_PITCH = "spawn.pitch";
  private static final String SPAWN_YAW = "spawn.yaw";
  private static final String SPAWN_WORLD = "spawn.world";
  private static final String CHECKPOINTS_LOCATION = "checkpoints";
  private static final String START_POINTS_LOCATION = "startPoints";
  public static final String POTION_EFFECTS_FIELD = "potion_effects";
  public static final String RESULTS_FIELD = "results";
  private static final String RESULTS_FIELD_PLAYER_ID = "player_id";
  private static final String RESULTS_FIELD_PLAYER_NAME = "name";
  private static final String RESULTS_FIELD_WINS = "wins";
  private static final String RESULTS_FIELD_RUNS = "runs";
  public static final String RESULTS_FIELD_DURATION = "duration";
  public static final String RESULTS_FIELD_FASTEST_LAP = "fastestLap";
  public static final String RESULTS_FIELD_RECORDS = "records";
  private static final String MINIMUM_REQUIRED_PARTICIPANTS_TO_START = "min_required_participants_to_start";
  private static final String PIG_SPEED_FIELD = "pig_speed";
  private static final String HORSE_SPEED_FIELD = "horse_speed";
  private static final String HORSE_JUMP_STRENGTH_FIELD = "horse_jump_strength";
  public static final String COMMANDS_FIELD = "commands";

  private ExecutorService fileService = Executors.newSingleThreadExecutor();
  private File racesDirectory;
  private MigrationManager migrationManager = new MigrationManager();

  public FileAPI(Plugin plugin) {
    racesDirectory = new File(plugin.getDataFolder(), Racing.getInstance().getConfiguration().get(ConfigKey.FILE_RACE_DIRECTORY));
    migrationManager.addMigration(new EntryFeeMigration());
    migrationManager.addMigration(new WalkSpeedMigration());
    migrationManager.addMigration(new PotionEffectsMigration());
    migrationManager.addMigration(new SignsMigration());
    migrationManager.addMigration(new ResultsMigration());
    migrationManager.addMigration(new MinimumRequiredParticipantsToStartMigration());
    migrationManager.addMigration(new PigSpeedMigration());
    migrationManager.addMigration(new HorseAttributesMigration());
    migrationManager.addMigration(new CommandsMigration());
    migrationManager.addMigration(new SignLapsMigration());
    migrationManager.addMigration(new RaceDurationMigration());
  }

  @Override
  public void fetchAllRaces(Consumer<List<Race>> callback) {
    CompletableFuture.supplyAsync(() -> {
      List<YamlConfiguration> races = new ArrayList<>();
      File[] files = racesDirectory.listFiles();

      if (files == null) {
        return races;
      }

      for (File file : files) {
        if (file.isFile()) {
          YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

          try {
            migrationManager.migrate(yaml);
            try {
              yaml.save(file);
            } catch (IOException e) {
              Racing.getInstance().getLogger().log(Level.SEVERE, "Failed to save race");
            }
            races.add(yaml);
          } catch (Exception ex) {
            Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
          }
        }
      }

      return races;
    }).thenAccept((List<YamlConfiguration> configurations) -> Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
      List<Race> races = new ArrayList<>();

      for (YamlConfiguration config : configurations) {
        try {
          races.add(parseRace(config));
        } catch (Exception ex) {
          Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        }
      }

      callback.accept(races);
    }));
  }

  @Override
  public void deleteRace(Race race, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, race.getId() + ".yml");

    CompletableFuture.supplyAsync(() -> {
      boolean success = false;
      try {
        Files.delete(raceFile.toPath());
        success = true;
      } catch (NoSuchFileException ex) {
        Racing.logger().log(Level.WARNING, "Failed to delete race file. File `" + raceFile.getName() + "` wasn't found.", ex);
      } catch (DirectoryNotEmptyException ex) {
        Racing.logger().log(Level.SEVERE, "Failed to delete race file. Expected a file but tried to delete a folder", ex);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
      }

      return success;
    }, fileService).thenAccept(callback);
  }

  @Override
  public void updateRace(Race race, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, race.getId() + ".yml");

    CompletableFuture.supplyAsync(() -> {
      YamlConfiguration yaml = new YamlConfiguration();
      yaml.set(ID_FIELD, race.getId().toString());
      yaml.set(VERSION_FIELD, race.getVersion().name());
      yaml.set(NAME_FIELD, race.getName());
      yaml.set(STATE_FIELD, race.getState().name());
      yaml.set(TYPE_FIELD, race.getType().name());
      yaml.set(SONG_FIELD, race.getSong());
      yaml.set(CREATED_AT_FIELD, race.getCreatedAt().getEpochSecond());
      yaml.set(ENTRY_FEE_FIELD, race.getEntryFee());
      yaml.set(WALK_SPEED_FIELD, race.getWalkSpeed());
      yaml.set(SPAWN_X, race.getSpawn().getX());
      yaml.set(SPAWN_Y, race.getSpawn().getY());
      yaml.set(SPAWN_Z, race.getSpawn().getZ());
      yaml.set(SPAWN_PITCH, race.getSpawn().getPitch());
      yaml.set(SPAWN_YAW, race.getSpawn().getYaw());
      yaml.set(SPAWN_WORLD, race.getSpawn().getWorld().getName());
      writeCheckpoints(race.getCheckpoints(), yaml);
      writeStartPoints(race.getStartPoints(), yaml);
      writePotionEffects(race.getPotionEffects(), yaml);
      writeSigns(race.getSigns(), yaml);
      writeResults(race.getResultByPlayerId().values(), yaml);
      yaml.set(MINIMUM_REQUIRED_PARTICIPANTS_TO_START, race.getMinimimRequiredParticipantsToStart());
      yaml.set(PIG_SPEED_FIELD, race.getPigSpeed());
      yaml.set(HORSE_SPEED_FIELD, race.getHorseSpeed());
      yaml.set(HORSE_JUMP_STRENGTH_FIELD, race.getHorseJumpStrength());
      writeCommands(race.getCommands(), yaml);

      try {
        yaml.save(raceFile);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }

      return true;

    }, fileService).thenAccept(callback);
  }

  @Override
  public void addCheckpoint(UUID raceId, RaceCheckpoint checkpoint, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, raceId + ".yml");

    CompletableFuture.supplyAsync(() -> {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raceFile);

      List<RaceCheckpoint> checkpoints = parseCheckpoints(yaml);

      boolean hasPosition = checkpoints.stream().anyMatch((RaceCheckpoint p) -> p.getPosition() == checkpoint.getPosition());

      if(hasPosition) {
        return false;
      }

      checkpoints.add(checkpoint);
      writeCheckpoints(checkpoints, yaml);

      try {
        yaml.save(raceFile);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }

      return true;
    }, fileService).thenAccept(callback);
  }

  @Override
  public void deleteCheckpoint(UUID raceId, RaceCheckpoint checkpoint, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, raceId + ".yml");

    CompletableFuture.supplyAsync(() -> {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raceFile);

      List<RaceCheckpoint> checkpoints = parseCheckpoints(yaml)
        .stream()
        .filter((RaceCheckpoint point) -> !point.getId().equals(checkpoint.getId()))
        .peek((RaceCheckpoint point) -> {
          if(point.getPosition() > checkpoint.getPosition()) {
            point.setPosition(point.getPosition() - 1);
          }
        })
        .collect(Collectors.toList());

      writeCheckpoints(checkpoints, yaml);

      try {
        yaml.save(raceFile);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }

      return true;
    }).thenAccept(callback);
  }

  @Override
  public void addStartPoint(UUID raceId, RaceStartPoint startPoint, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, raceId + ".yml");

    CompletableFuture.supplyAsync(() -> {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raceFile);

      List<RaceStartPoint> startPoints = parseStartPoints(yaml);

      boolean hasPosition = startPoints.stream().anyMatch((RaceStartPoint p) -> p.getPosition() == startPoint.getPosition());

      if(hasPosition) {
        return false;
      }

      startPoints.add(startPoint);
      writeStartPoints(startPoints, yaml);

      try {
        yaml.save(raceFile);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }

      return true;
    }, fileService).thenAccept(callback);
  }

  @Override
  public void deleteStartPoint(UUID raceId, RaceStartPoint startPoint, Consumer<Boolean> callback) {
    File raceFile = new File(racesDirectory, raceId + ".yml");

    CompletableFuture.supplyAsync(() -> {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raceFile);

      List<RaceStartPoint> filteredStartPoints = parseStartPoints(yaml)
        .stream()
        .filter((RaceStartPoint point) -> !point.getId().equals(startPoint.getId()))
        .peek((RaceStartPoint point) -> {
          if(point.getPosition() > startPoint.getPosition()) {
            point.setPosition(point.getPosition() - 1);
          }
        })
        .collect(Collectors.toList());

      writeStartPoints(filteredStartPoints, yaml);

      try {
        yaml.save(raceFile);
      } catch (IOException ex) {
        Racing.logger().log(Level.SEVERE, ex.getMessage(), ex);
        return false;
      }

      return true;
    }).thenAccept(callback);
  }

  private Race parseRace(YamlConfiguration yaml) throws ParseRaceException {
    String idString = yaml.getString(ID_FIELD);

    if(idString == null) {
      throw new ParseRaceException("`" + ID_FIELD + "` is missing from race");
    }

    RaceVersion version = RaceVersion.fromString(yaml.getString("version"));
    if(version == null) {
      throw new ParseRaceException("`" + VERSION_FIELD + "` is missing from race");
    }

    UUID id;
    try {
      id = UUID.fromString(idString);
    } catch (IllegalArgumentException ex) {
      throw new ParseRaceException("Couldn't convert id to UUID");
    }

    Location spawn;
    try {
      spawn = parseLocation(yaml, "spawn");
    } catch (ParseYamlLocationException ex) {
      throw new ParseRaceException("Couldn't parse spawn: " + ex.getMessage());
    }

    String name = yaml.getString(NAME_FIELD);
    if(name == null) {
      throw new ParseRaceException("`" + NAME_FIELD + "` is missing from race");
    }

    Instant createdAt;
    try {
      createdAt = Instant.ofEpochSecond(yaml.getLong(CREATED_AT_FIELD));
    } catch (DateTimeException ex) {
      throw new ParseRaceException("`" + CREATED_AT_FIELD + "` is invalid");
    }

    RaceType type;
    try {
      type = RaceType.valueOf(yaml.getString(TYPE_FIELD));
    } catch (IllegalArgumentException ex) {
      throw new ParseRaceException("`" + TYPE_FIELD + "` is invalid");
    }

    RaceState state;
    try {
      state = RaceState.valueOf(yaml.getString(STATE_FIELD));
    } catch (IllegalArgumentException ex) {
      throw new ParseRaceException("`" + STATE_FIELD + "` is invalid");
    }

    if(!yaml.contains(ENTRY_FEE_FIELD) || (!yaml.isDouble(ENTRY_FEE_FIELD) && !yaml.isInt(ENTRY_FEE_FIELD))) {
      throw new ParseRaceException("Missing field `" + ENTRY_FEE_FIELD + "`");
    }

    double entryFee;
    if(yaml.isDouble(ENTRY_FEE_FIELD)) {
      entryFee = yaml.getDouble(ENTRY_FEE_FIELD);
    } else {
      entryFee = yaml.getInt(ENTRY_FEE_FIELD);
    }

    List<RaceCheckpoint> checkpoints = parseCheckpoints(yaml);
    List<RaceStartPoint> startPoints = parseStartPoints(yaml);
    Set<RacePotionEffect> potionEffects = parsePotionEffects(yaml);
    Set<RacePlayerStatistic> results = parseResults(yaml);

    String song = yaml.getString(SONG_FIELD, null);

    if(!yaml.contains(WALK_SPEED_FIELD) || (!yaml.isDouble(WALK_SPEED_FIELD) && !yaml.isInt(WALK_SPEED_FIELD))) {
      throw new ParseRaceException("Missing field `" + WALK_SPEED_FIELD + "`");
    }

    float walkSpeed;
    if(yaml.isDouble(WALK_SPEED_FIELD)) {
      walkSpeed = (float)yaml.getDouble(WALK_SPEED_FIELD);
    } else {
      walkSpeed = yaml.getInt(WALK_SPEED_FIELD);
    }

    if(!yaml.contains(MINIMUM_REQUIRED_PARTICIPANTS_TO_START)) {
      throw new ParseRaceException("`" + MINIMUM_REQUIRED_PARTICIPANTS_TO_START + "` is missing from race");
    }
    int minimumRequiredParticipantsToStart = yaml.getInt(MINIMUM_REQUIRED_PARTICIPANTS_TO_START);

    double pigSpeed;
    if(yaml.isDouble(PIG_SPEED_FIELD)) {
      pigSpeed = yaml.getDouble(PIG_SPEED_FIELD);
    } else {
      pigSpeed = yaml.getInt(PIG_SPEED_FIELD);
    }

    double horseSpeed;
    if(yaml.isDouble(HORSE_SPEED_FIELD)) {
      horseSpeed = yaml.getDouble(HORSE_SPEED_FIELD);
    } else {
      horseSpeed = yaml.getInt(HORSE_SPEED_FIELD);
    }

    double horseJumpStrength;
    if(yaml.isDouble(HORSE_JUMP_STRENGTH_FIELD)) {
      horseJumpStrength = yaml.getDouble(HORSE_JUMP_STRENGTH_FIELD);
    } else {
      horseJumpStrength = yaml.getInt(HORSE_JUMP_STRENGTH_FIELD);
    }

    return new Race(
      id,
      version,
      name,
      spawn,
      state,
      createdAt,
      checkpoints,
      startPoints,
      type,
      song,
      entryFee,
      walkSpeed,
      potionEffects,
      parseSigns(yaml),
      results,
      minimumRequiredParticipantsToStart,
      pigSpeed,
      horseSpeed,
      horseJumpStrength,
      parseCommands(yaml)
    );
  }

  private List<RaceCheckpoint> parseCheckpoints(YamlConfiguration yaml) {
    List<RaceCheckpoint> checkpoints = new ArrayList<>();
    ConfigurationSection section = yaml.getConfigurationSection(CHECKPOINTS_LOCATION);

    if(section == null) {
      Racing.logger().log(Level.SEVERE, "Couldn't find checkpoints section");
      return checkpoints;
    }

    for(String key : section.getKeys(false)) {
      UUID id;
      try {
        id = UUID.fromString(key);
      } catch (IllegalArgumentException ex) {
        Racing.logger().log(Level.SEVERE, "Couldn't convert key to UUID", ex);
        continue;
      }

      String positionPath = key + ".position";
      if(!section.isInt(positionPath)) {
        Racing.logger().log(Level.SEVERE, "`position` must be of type integer");
        continue;
      }

      int position = section.getInt(positionPath);

      Location location;
      try {
        location = parseLocation(section, key + ".location");
      }  catch (ParseYamlLocationException ex) {
        Racing.logger().log(Level.SEVERE, "Couldn't parse location: " + ex.getMessage(), ex);
        continue;
      }

      String radiusPath = key + ".radius";
      if(!section.isInt(radiusPath)) {
        Racing.logger().log(Level.SEVERE, "`radius` must be of type integer");
        continue;
      }

      checkpoints.add(new RaceCheckpoint(id, position, location, section.getInt(radiusPath)));
    }

    return checkpoints;
  }

  private void writeCheckpoints(List<RaceCheckpoint> checkpoints, YamlConfiguration yaml) {
    yaml.set(CHECKPOINTS_LOCATION, null);
    if(!yaml.contains(CHECKPOINTS_LOCATION)) {
      yaml.createSection(CHECKPOINTS_LOCATION);
    }
    for(RaceCheckpoint checkpoint : checkpoints) {
      String key = CHECKPOINTS_LOCATION + "." + checkpoint.getId();
      yaml.set(key + ".position", checkpoint.getPosition());
      yaml.set(key + ".location", checkpoint.getPosition());
      writeLocation(checkpoint.getLocation(), yaml, key + ".location");
      yaml.set(key + ".radius", checkpoint.getRadius());
    }
  }

  private List<RaceStartPoint> parseStartPoints(YamlConfiguration yaml) {
    List<RaceStartPoint> startPoints = new ArrayList<>();
    ConfigurationSection section = yaml.getConfigurationSection(START_POINTS_LOCATION);

    if(section == null) {
      Racing.logger().log(Level.SEVERE, "Couldn't find startpoints section");
      return startPoints;
    }

    for(String key : section.getKeys(false)) {
      UUID id;
      try {
        id = UUID.fromString(key);
      } catch (IllegalArgumentException ex) {
        Racing.logger().log(Level.SEVERE, "Couldn't convert key to UUID", ex);
        continue;
      }

      String positionPath = key + ".position";
      if(!section.isInt(positionPath)) {
        Racing.logger().log(Level.SEVERE, "`position` must be of type integer");
        continue;
      }

      int position = section.getInt(positionPath);

      Location location;

      try {
        location = parseLocation(section, key + ".location");
      }  catch (ParseYamlLocationException ex) {
        Racing.logger().log(Level.SEVERE, "Couldn't parse location: " + ex.getMessage(), ex);
        continue;
      }

      startPoints.add(new RaceStartPoint(id, position, location));
    }

    return startPoints;
  }

  private void writeStartPoints(List<RaceStartPoint> startPoints, YamlConfiguration yaml) {
    yaml.set(START_POINTS_LOCATION, null);
    if(!yaml.contains(START_POINTS_LOCATION)) {
      yaml.createSection(START_POINTS_LOCATION);
    }

    for(RaceStartPoint startPoint : startPoints) {
      String key = START_POINTS_LOCATION + "." + startPoint.getId();
      yaml.set(key + ".position", startPoint.getPosition());
      yaml.set(key + ".location", startPoint.getPosition());
      writeLocation(startPoint.getLocation(), yaml, key + ".location");
    }
  }

  private Set<RacePlayerStatistic> parseResults(YamlConfiguration yaml) {
    Set<RacePlayerStatistic> results = new HashSet<>();
    List<Map<String, Object>> entries = (List<Map<String, Object>>)yaml.getList(RESULTS_FIELD);
    if(entries == null) {
      throw new ParseRaceException("Couldn't parse `" + RESULTS_FIELD + "` list");
    }

    for (Map<String, Object> entry : entries) {
      UUID playerId;
      try {
        playerId = UUID.fromString((String) entry.get(RESULTS_FIELD_PLAYER_ID));
      } catch (IllegalArgumentException e) {
        throw new ParseRaceException("Couldn't parse key `" + RESULTS_FIELD_PLAYER_ID + "` as UUID in " + RESULTS_FIELD);
      }

      String playerName = (String) entry.get(RESULTS_FIELD_PLAYER_NAME);
      if (playerName == null || playerName.isEmpty()) {
        throw new ParseRaceException("Couldn't parse `" + RESULTS_FIELD_PLAYER_NAME + "` is null or empty");
      }

      int runs = (int) entry.get(RESULTS_FIELD_RUNS);
      int wins = (int) entry.get(RESULTS_FIELD_WINS);
      long fastestLap = (int) entry.get(RESULTS_FIELD_FASTEST_LAP);
      Map<Integer, Long> records = (Map<Integer, Long>) entry.get(RESULTS_FIELD_RECORDS);

      results.add(new RacePlayerStatistic(playerId, playerName, wins, runs, fastestLap, records));
    }

    return results;
  }

  private void writeResults(Collection<RacePlayerStatistic> results, YamlConfiguration yaml) {
    List<Map<String, Object>> writeList = new ArrayList<>();

    for(RacePlayerStatistic entry : results) {
      Map<String, Object> writeObject = new LinkedHashMap<>();
      writeObject.put(RESULTS_FIELD_PLAYER_ID, entry.getPlayerId().toString());
      writeObject.put(RESULTS_FIELD_PLAYER_NAME, entry.getPlayerName());
      writeObject.put(RESULTS_FIELD_RUNS, entry.getRuns());
      writeObject.put(RESULTS_FIELD_WINS, entry.getWins());
      writeObject.put(RESULTS_FIELD_FASTEST_LAP, entry.getFastestLap());
      writeObject.put(RESULTS_FIELD_RECORDS, entry.getRecords());
      writeList.add(writeObject);
    }

    yaml.set(RESULTS_FIELD, writeList);
  }

  private Set<RacePotionEffect> parsePotionEffects(YamlConfiguration yaml) {
    Set<RacePotionEffect> potionEffects = new HashSet<>();
    ConfigurationSection section = yaml.getConfigurationSection(POTION_EFFECTS_FIELD);

    if(section == null) {
      throw new ParseRaceException("Missing field `" + POTION_EFFECTS_FIELD + "`");
    }

    for(String key : section.getKeys(false)) {
      PotionEffectType type = PotionEffectType.getByName(key);
      if(type == null) {
        throw new ParseRaceException("Couldn't parse PotionEffectType");
      }

      String amplifierPath = key + ".amplifier";
      if(!section.isInt(amplifierPath)) {
        throw new ParseRaceException("Couldn't parse amplifier");
      }

      potionEffects.add(new RacePotionEffect(type, section.getInt(amplifierPath)));
    }

    return potionEffects;
  }

  private void writePotionEffects(Set<RacePotionEffect> potionEffects, YamlConfiguration yaml) {
    yaml.createSection(POTION_EFFECTS_FIELD);

    for(RacePotionEffect potionEffect : potionEffects) {
      String key = POTION_EFFECTS_FIELD + "." + potionEffect.getType().getName();
      yaml.set(key + ".amplifier", potionEffect.getAmplifier());
    }
  }

  private Set<RaceSign> parseSigns(YamlConfiguration yaml) {
    Set<RaceSign> signs = new HashSet<>();
    List<Map<String, Object>> entries = (List<Map<String, Object>>)yaml.getList("signs");
    if(entries == null) {
      throw new ParseRaceException("Couldn't parse signs list");
    }

    for (Map<String, Object> entry : entries) {
      int x = (int) entry.get("x");
      int y = (int) entry.get("y");
      int z = (int) entry.get("z");
      String worldName = (String) entry.get("world");
      World world = Bukkit.getWorld(worldName);
      if(world == null) {
        throw new ParseRaceException("Couldn't parse sign because a world with name " + worldName + " wasn't found");
      }

      Block block = world.getBlockAt(x, y, z);
      if (!(block.getState() instanceof Sign)) {
        Racing.logger().log(Level.WARNING, "Couldn't find sign at (" + x + ", " + y + ", " + z + ")");
        continue;
      }

      UUID uuid;
      try {
        uuid = UUID.fromString((String) entry.get("author"));
      } catch (IllegalArgumentException ex) {
        throw new ParseRaceException("Couldn't parse sign because author UUID is not valid");
      }

      Instant createdAt;
      try {
        createdAt = Instant.ofEpochSecond((int)entry.get("created_at"));
      } catch (DateTimeException ex) {
        throw new ParseRaceException("Couldn't parse sign because timestamp is invalid");
      }

      int laps;
      {
        if (!entry.containsKey("laps")) {
          throw new ParseRaceException("Couldn't find signs[].laps");
        }

        if (!(entry.get("laps") instanceof Integer)) {
          throw new ParseRaceException("Couldn't convert signs[].laps to an integer");
        }

        laps = (int) entry.get("laps");
      }

      signs.add(new RaceSign((Sign) block.getState(), uuid, createdAt, laps));
    }

    return signs;
  }

  private void writeSigns(Set<RaceSign> signs, YamlConfiguration yaml) {
    List<Map<String, Object>> writeList = new ArrayList<>();
    for(RaceSign sign : signs) {
      Map<String, Object> writeSign = new LinkedHashMap<>();
      writeSign.put("x", sign.getSign().getLocation().getBlockX());
      writeSign.put("y", sign.getSign().getLocation().getBlockY());
      writeSign.put("z", sign.getSign().getLocation().getBlockZ());
      writeSign.put("world", sign.getSign().getLocation().getWorld().getName());
      writeSign.put("author", sign.getCreator().toString());
      writeSign.put("created_at", sign.getCreatedAt().getEpochSecond());
      writeSign.put("laps", sign.getLaps());
      writeList.add(writeSign);
    }
    yaml.set("signs", writeList);
  }

  private Location parseLocation(ConfigurationSection section, String path) throws ParseYamlLocationException {
    String xPath = path + ".x";
    if(!section.isDouble(xPath)) {
      throw new ParseYamlLocationException("Expected `" + xPath + "` to be an integer");
    }

    String yPath = path + ".y";
    if(!section.isDouble(yPath)) {
      throw new ParseYamlLocationException("Expected `" + yPath + "` to be an integer");
    }

    String zPath = path + ".z";
    if(!section.isDouble(zPath)) {
      throw new ParseYamlLocationException("Expected `" + zPath + "` to be an integer");
    }

    String pitchPath = path + ".pitch";
    if(!section.isDouble(pitchPath)) {
      throw new ParseYamlLocationException("Expected `" + pitchPath + "` to be a double");
    }

    String yawPath = path + ".yaw";
    if(!section.isDouble(yawPath)) {
      throw new ParseYamlLocationException("Expected `" + yawPath + "` to be a double");
    }

    String worldPath = path + ".world";
    String worldName = section.getString(worldPath);
    if(worldName == null) {
      throw new ParseYamlLocationException("Expected `" + worldPath + "` to be a string");
    }

    World world = Bukkit.getWorld(worldName);

    if(world == null) {
      throw new ParseYamlLocationException("Couldn't find world with name `" + section.getString(worldPath) + "`");
    }

    return new Location(
      world,
      section.getDouble(xPath),
      section.getDouble(yPath),
      section.getDouble(zPath),
      (float)section.getDouble(yawPath),
      (float)section.getDouble(pitchPath)
    );
  }

  private void writeLocation(Location location, YamlConfiguration yaml, String path) {
    yaml.set(path + ".x", location.getX());
    yaml.set(path + ".y", location.getY());
    yaml.set(path + ".z", location.getZ());
    yaml.set(path + ".pitch", location.getPitch());
    yaml.set(path + ".yaw", location.getYaw());
    yaml.set(path + ".world", location.getWorld().getName());
  }

  private List<RaceCommand> parseCommands(YamlConfiguration yaml) {
    List<RaceCommand> commands = new ArrayList<>();
    List<Map<String, Object>> entries = (List<Map<String, Object>>)yaml.getList("commands");
    if(entries == null) {
      throw new ParseRaceException("Couldn't parse `commands`");
    }

    for (Map<String, Object> entry : entries) {
      RaceCommandType commandType;
      {
        if(!entry.containsKey("when")) {
          throw new ParseRaceException("Couldn't find field `commands[].when`");
        }

        String stringWhen = (String) entry.get("when");
        stringWhen = stringWhen.toUpperCase();
        try {
          commandType = RaceCommandType.valueOf(stringWhen);
        } catch (IllegalArgumentException ex) {
          throw new ParseRaceException("Couldn't parse `commands[].when`. Value: " + stringWhen);
        }
      }

      boolean isEnabled;
      {
        if(!entry.containsKey("enabled")) {
          throw new ParseRaceException("Couldn't find field `commands[].enabled`");
        }

        if(!(entry.get("enabled") instanceof Boolean)) {
          throw new ParseRaceException("Couldn't convert `commands[].enabled` to a boolean.");
        }

        isEnabled = (boolean) entry.get("enabled");
      }

      String command;
      {
        if(!entry.containsKey("command")) {
          throw new ParseRaceException("Couldn't find field `commands[].command`");
        }

        command = (String) entry.get("command");
        command = command.trim();
        if(command.isEmpty()) {
          throw new ParseRaceException("Couldn't parse `commands[].command`. Value can't be empty");
        }
      }

      int recipient = Integer.MIN_VALUE;
      {
        if(entry.containsKey("recipient")) {
          if (entry.get("recipient") instanceof String) {
            if(!entry.get("recipient").equals("@everyone")) {
              throw new ParseRaceException("Couldn't parse `commands[].recipient`. Value not recognized");
            }
            recipient = 0;
          } else {
            try {
              recipient = (int) entry.get("recipient");
            } catch (Exception e) {
              throw new ParseRaceException("Couldn't parse `commands[].recipient`. Value can't be converted to an integer");
            }

            if (recipient <= 0) {
              throw new ParseRaceException("Couldn't parse `commands[].recipient`. Value must be above 0");
            }
          }
        }
      }

      commands.add(new RaceCommand(commandType, isEnabled, command, recipient));
    }

    return commands;
  }

  private void writeCommands(List<RaceCommand> commands, YamlConfiguration yaml) {
    List<Map<String, Object>> writeList = new ArrayList<>();
    for(RaceCommand command : commands) {
      Map<String, Object> writeObject = new LinkedHashMap<>();
      writeObject.put("when", command.getCommandType().toString().toLowerCase(Locale.ENGLISH));
      writeObject.put("enabled", command.isEnabled());
      writeObject.put("command", command.getCommand());
      if(command.getRecipient() != Integer.MIN_VALUE) {
        if(command.getRecipient() == 0) {
          writeObject.put("recipient", "@everyone");
        } else {
          writeObject.put("recipient", command.getRecipient());
        }
      }
      writeList.add(writeObject);
    }
    yaml.set("commands", writeList);
  }
}
