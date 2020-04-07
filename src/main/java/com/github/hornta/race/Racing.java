package com.github.hornta.race;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.github.hornta.carbon.*;
import com.github.hornta.carbon.config.ConfigType;
import com.github.hornta.carbon.config.Configuration;
import com.github.hornta.carbon.config.ConfigurationBuilder;
import com.github.hornta.carbon.message.MessageManager;
import com.github.hornta.carbon.message.MessagesBuilder;
import com.github.hornta.carbon.message.Translation;
import com.github.hornta.carbon.message.Translations;
import com.github.hornta.race.api.FileAPI;
import com.github.hornta.race.api.StorageType;
import com.github.hornta.race.commands.*;
import com.github.hornta.race.commands.argumentHandlers.*;
import com.github.hornta.race.enums.Permission;
import com.github.hornta.race.enums.RespawnType;
import com.github.hornta.race.enums.TeleportAfterRaceWhen;
import com.github.hornta.race.mcmmo.McMMOListener;
import com.github.hornta.race.objects.RaceCommandExecutor;
import com.gmail.nossr50.mcMMO;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Racing extends JavaPlugin {
  private static Racing instance;
  private boolean isNoteBlockAPILoaded;
  private boolean isHolographicDisplaysLoaded;
  private Economy economy;
  private Chat chat;
  private Carbon carbon;
  private Translations translations;
  private RacingManager racingManager;
  private ProtocolManager protocolManager;
  private Metrics metrics;
  private RaceCommandExecutor raceCommandExecutor;
  private Configuration configuration;
  private MessageManager messageManager;

  public static Racing getInstance() {
    return instance;
  }

  public static Logger logger() {
    return instance.getLogger();
  }

  public ProtocolManager getProtocolManager() {
    return protocolManager;
  }

  public Economy getEconomy() {
    return economy;
  }

  public Chat getChat() {
    return chat;
  }

  @Override
  public void onLoad() {
    protocolManager = ProtocolLibrary.getProtocolManager();
  }

  public boolean isNoteBlockAPILoaded() {
    return isNoteBlockAPILoaded;
  }

  public boolean isHolographicDisplaysLoaded() {
    return isHolographicDisplaysLoaded;
  }

  public Carbon getCarbon() {
    return carbon;
  }

  public Translations getTranslations() {
    return translations;
  }

  public RacingManager getRacingManager() {
    return racingManager;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  private void setupCommands() {
    carbon = new Carbon();

    carbon.setNoPermissionHandler((CommandSender commandSender, CarbonCommand command) -> {
      MessageManager.sendMessage(commandSender, MessageKey.NO_PERMISSION_COMMAND);
    });

    carbon.setMissingArgumentHandler((CommandSender commandSender, CarbonCommand command) -> {
      MessageManager.setValue("usage", command.getHelpText());
      MessageManager.sendMessage(commandSender, MessageKey.MISSING_ARGUMENTS_COMMAND);
    });

    carbon.setMissingCommandHandler((CommandSender sender, List<CarbonCommand> suggestions) -> {
      MessageManager.setValue("suggestions", suggestions.stream()
        .map(CarbonCommand::getHelpText)
        .collect(Collectors.joining("\n")));
      MessageManager.sendMessage(sender, MessageKey.COMMAND_NOT_FOUND);
    });

    carbon.handleValidation((ValidationResult result) -> {
      switch (result.getStatus()) {
        case ERR_INCORRECT_TYPE:
          MessageManager.setValue("help_text", result.getCommand().getHelpText());
          MessageManager.setValue("argument", result.getArgument().getName());
          MessageManager.setValue("received", result.getValue());
          if (result.getArgument().getType() == CarbonArgumentType.INTEGER) {
            MessageManager.sendMessage(result.getCommandSender(), MessageKey.VALIDATE_NON_INTEGER);
          } else if (result.getArgument().getType() == CarbonArgumentType.NUMBER) {
            MessageManager.sendMessage(result.getCommandSender(), MessageKey.VALIDATE_NON_INTEGER);
          }
          break;

        case ERR_MIN_LIMIT:
        case ERR_MAX_LIMIT:
          MessageManager.setValue("help_text", result.getCommand().getHelpText());
          MessageManager.setValue("argument", result.getArgument().getName());
          MessageManager.setValue("received", result.getValue());
          if(result.getStatus() == ValidationStatus.ERR_MIN_LIMIT) {
            MessageManager.setValue("expected", result.getArgument().getMin());
            MessageManager.sendMessage(result.getCommandSender(), MessageKey.VALIDATE_MIN_EXCEED);
          } else {
            MessageManager.setValue("expected", result.getArgument().getMax());
            MessageManager.sendMessage(result.getCommandSender(), MessageKey.VALIDATE_MAX_EXCEED);
          }
          break;

        case ERR_OTHER:
          if(result.getArgument().getType() == CarbonArgumentType.POTION_EFFECT) {
            MessageManager.setValue("potion_effect", result.getValue());
            MessageManager.sendMessage(result.getCommandSender(), MessageKey.POTION_EFFECT_NOT_FOUND);
          }
          break;
      }
    });

    CarbonArgument raceArgument =
      new CarbonArgument.Builder("race")
      .setHandler(new RaceArgumentHandler(racingManager, true))
      .create();

    carbon
      .addCommand("racing create")
      .withHandler(new CommandCreateRace(racingManager))
      .withArgument(
        new CarbonArgument.Builder("race")
          .setHandler(new RaceArgumentHandler(racingManager, false))
          .showTabCompletion(false)
          .create()
      )
      .requiresPermission(Permission.COMMAND_CREATE.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing delete")
      .withHandler(new CommandDeleteRace(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_DELETE.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing list")
      .withHandler(new CommandRaces(racingManager))
      .requiresPermission(Permission.COMMAND_LIST.toString())
      .requiresPermission(Permission.RACING_PLAYER.toString());

    carbon
      .addCommand("racing addcheckpoint")
      .withHandler(new CommandAddCheckpoint(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_ADD_CHECKPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();

    CarbonArgument checkpointArgument = new CarbonArgument.Builder("point")
      .setHandler(new CheckpointArgumentHandler(racingManager, true))
      .dependsOn(raceArgument)
      .create();

    carbon
      .addCommand("racing deletecheckpoint")
      .withHandler(new CommandDeleteCheckpoint(racingManager))
      .withArgument(raceArgument)
      .withArgument(checkpointArgument)
      .requiresPermission(Permission.COMMAND_DELETE_CHECKPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing tpcheckpoint")
      .withHandler(new CommandRaceTeleportPoint(racingManager))
      .withArgument(raceArgument)
      .withArgument(checkpointArgument)
      .requiresPermission(Permission.COMMAND_TELEPORT_CHECKPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing spawn")
      .withHandler(new CommandRaceSpawn(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.RACING_PLAYER.toString())
      .requiresPermission(Permission.COMMAND_SPAWN.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing setspawn")
      .withHandler(new CommandRaceSetSpawn(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_SET_SPAWN.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing setstate")
      .withHandler(new CommandSetRaceState(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("state")
          .setHandler(new RaceStateArgumentHandler())
          .create()
      )
      .requiresPermission(Permission.COMMAND_SET_STATE.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing setname")
      .withHandler(new CommandSetRaceName(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("name")
          .setType(CarbonArgumentType.STRING)
          .create()
      )
      .requiresPermission(Permission.COMMAND_SET_NAME.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing settype")
      .withHandler(new CommandSetType(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("type")
          .setHandler(new RaceTypeArgumentHandler())
          .create()
      )
      .requiresPermission(Permission.COMMAND_SET_TYPE.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    if(economy != null) {
      carbon
        .addCommand("racing setentryfee")
        .withHandler(new CommandSetEntryFee(racingManager))
        .withArgument(raceArgument)
        .withArgument(
          new CarbonArgument.Builder("fee")
            .setType(CarbonArgumentType.NUMBER)
            .setMin(0)
            .create()
        )
        .requiresPermission(Permission.COMMAND_SET_ENTRY_FEE.toString())
        .requiresPermission(Permission.RACING_MODIFY.toString());
    }


    CarbonArgument speedArgument = new CarbonArgument.Builder("speed")
      .setType(CarbonArgumentType.NUMBER)
      .setMin(0)
      .create();

    carbon
      .addCommand("racing setwalkspeed")
      .withHandler(new CommandSetWalkSpeed(racingManager))
      .withArgument(raceArgument)
      .withArgument(speedArgument)
      .requiresPermission(Permission.COMMAND_SET_WALK_SPEED.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing setpigspeed")
      .withHandler(new CommandSetPigSpeed(racingManager))
      .withArgument(raceArgument)
      .withArgument(speedArgument)
      .requiresPermission(Permission.COMMAND_SET_PIG_SPEED.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing sethorsespeed")
      .withHandler(new CommandSetHorseSpeed(racingManager))
      .withArgument(raceArgument)
      .withArgument(speedArgument)
      .requiresPermission(Permission.COMMAND_SET_HORSE_SPEED.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing sethorsejumpstrength")
      .withHandler(new CommandSetHorseJumpStrength(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("jump_strength")
        .setType(CarbonArgumentType.NUMBER)
        .setMin(0)
        .create()
      )
      .requiresPermission(Permission.COMMAND_SET_HORSE_JUMP_STRENGTH.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing addpotioneffect")
      .withHandler(new CommandAddPotionEffect(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("effect")
          .setType(CarbonArgumentType.POTION_EFFECT)
          .create()
      )
      .withArgument(
        new CarbonArgument.Builder("amplifier")
          .setType(CarbonArgumentType.INTEGER)
          .setMin(0)
          .setMax(255)
          .create()
      )
      .requiresPermission(Permission.COMMAND_ADD_POTION_EFFECT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing removepotioneffect")
      .withHandler(new CommandRemovePotionEffect(racingManager))
      .withArgument(raceArgument)
      .withArgument(
        new CarbonArgument.Builder("effect")
          .setHandler(new RacePotionEffectArgumentHandler(racingManager))
          .dependsOn(raceArgument)
          .create()
      )
      .requiresPermission(Permission.COMMAND_REMOVE_POTION_EFFECT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing clearpotioneffects")
      .withHandler(new CommandClearPotionEffects(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_CLEAR_POTION_EFFECTS.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing addstartpoint")
      .withHandler(new CommandAddStartpoint(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_ADD_STARTPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();


    CarbonArgument startPointArgument =
      new CarbonArgument.Builder("point")
        .setHandler(new StartPointArgumentHandler(racingManager, true))
        .dependsOn(raceArgument)
        .create();

    carbon
      .addCommand("racing deletestartpoint")
      .withHandler(new CommandDeleteStartpoint(racingManager))
      .withArgument(raceArgument)
      .withArgument(startPointArgument)
      .requiresPermission(Permission.COMMAND_DELETE_STARTPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString());

    carbon
      .addCommand("racing tpstartpoint")
      .withHandler(new CommandRaceTeleportStart(racingManager))
      .withArgument(raceArgument)
      .withArgument(startPointArgument)
      .requiresPermission(Permission.COMMAND_TELEPORT_STARTPOINT.toString())
      .requiresPermission(Permission.RACING_MODIFY.toString())
      .preventConsoleCommandSender();

    if(isNoteBlockAPILoaded) {
      CarbonArgument songArgument =
        new CarbonArgument.Builder("song")
          .setHandler(new SongArgumentHandler())
          .create();

      carbon
        .addCommand("racing setsong")
        .withHandler(new CommandSetSong(racingManager))
        .withArgument(raceArgument)
        .withArgument(songArgument)
        .requiresPermission(Permission.COMMAND_SET_SONG.toString())
        .requiresPermission(Permission.RACING_MODIFY.toString());

      carbon
        .addCommand("racing unsetsong")
        .withHandler(new CommandUnsetSong(racingManager))
        .withArgument(raceArgument)
        .requiresPermission(Permission.COMMAND_UNSET_SONG.toString())
        .requiresPermission(Permission.RACING_MODIFY.toString());

      carbon
        .addCommand("racing playsong")
        .withHandler(new CommandPlaySong())
        .withArgument(songArgument)
        .requiresPermission(Permission.COMMAND_PLAY_SONG.toString())
        .requiresPermission(Permission.RACING_MODIFY.toString())
        .preventConsoleCommandSender();

      carbon
        .addCommand("racing stopsong")
        .withHandler(new CommandStopSong())
        .requiresPermission(Permission.COMMAND_STOP_SONG.toString())
        .requiresPermission(Permission.RACING_MODIFY.toString())
        .preventConsoleCommandSender();
    }

    CarbonArgument lapsArgument = new CarbonArgument.Builder("laps")
      .setType(CarbonArgumentType.INTEGER)
      .setDefaultValue(CommandSender.class, 1)
      .setMin(1)
      .create();

    carbon
      .addCommand("racing start")
      .withHandler(new CommandStartRace(racingManager))
      .withArgument(raceArgument)
      .withArgument(lapsArgument)
      .requiresPermission(Permission.COMMAND_START.toString())
      .requiresPermission(Permission.RACING_MODERATOR.toString());

    carbon
      .addCommand("racing startrandom")
      .withHandler(new CommandStartRace(racingManager))
      .withArgument(lapsArgument)
      .requiresPermission(Permission.COMMAND_START_RANDOM.toString())
      .requiresPermission(Permission.RACING_MODERATOR.toString());

    carbon
      .addCommand("racing join")
      .withHandler(new CommandJoinRace(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_JOIN.toString())
      .requiresPermission(Permission.RACING_PLAYER.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing stop")
      .withHandler(new CommandStopRace(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_STOP.toString())
      .requiresPermission(Permission.RACING_MODERATOR.toString());

    carbon
      .addCommand("racing skipwait")
      .withHandler(new CommandSkipWait(racingManager))
      .withArgument(raceArgument)
      .requiresPermission(Permission.COMMAND_SKIPWAIT.toString())
      .requiresPermission(Permission.RACING_MODERATOR.toString());

    carbon
      .addCommand("racing leave")
      .withHandler(new CommandLeave(racingManager))
      .requiresPermission(Permission.COMMAND_LEAVE.toString())
      .requiresPermission(Permission.RACING_PLAYER.toString())
      .preventConsoleCommandSender();

    carbon
      .addCommand("racing reload")
      .withHandler(new CommandReload())
      .requiresPermission(Permission.COMMAND_RELOAD.toString())
      .requiresPermission(Permission.RACING_ADMIN.toString());

    carbon
      .addCommand("racing help")
      .withHandler(new CommandHelp())
      .requiresPermission(Permission.COMMAND_HELP.toString())
      .requiresPermission(Permission.RACING_PLAYER.toString());

    carbon
      .addCommand("racing info")
      .withArgument(raceArgument)
      .withHandler(new CommandInfo(racingManager))
      .requiresPermission(Permission.COMMAND_INFO.toString())
      .requiresPermission(Permission.RACING_MODERATOR.toString());

    CarbonArgument statArgument = new CarbonArgument.Builder("stat")
      .setHandler(new RaceStatArgumentHandler())
      .create();

    carbon
      .addCommand("racing top")
      .withArgument(raceArgument)
      .withArgument(statArgument)
      .withHandler(new CommandTop(racingManager))
      .requiresPermission(Permission.COMMAND_TOP.toString())
      .requiresPermission(Permission.RACING_PLAYER.toString());

    carbon
      .addCommand("racing resettop")
      .withArgument(raceArgument)
      .withHandler(new CommandResetTop(racingManager))
      .requiresPermission(Permission.COMMAND_RESET_TOP.toString())
      .requiresPermission(Permission.RACING_ADMIN.toString());
  }

  @Override
  public void onEnable() {
    instance = this;
    metrics = new Metrics(this);
    isNoteBlockAPILoaded = Bukkit.getPluginManager().isPluginEnabled("NoteBlockAPI");
    isHolographicDisplaysLoaded = Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays");

    if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
      {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
          economy = rsp.getProvider();
        }
      }

      {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
          chat = rsp.getProvider();
        }
      }
    }

    try {
      configuration = new ConfigurationBuilder(this)
        .add(ConfigKey.LANGUAGE, "language", ConfigType.STRING, "english")
        // https://www.loc.gov/standards/iso639-2/php/code_list.php
        .add(ConfigKey.LOCALE, "locale", ConfigType.STRING, "en", (Object val) -> new Locale(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.SONGS_DIRECTORY, "songs_directory", ConfigType.STRING, "songs")
        .add(ConfigKey.STORAGE, "storage.current", ConfigType.STRING, StorageType.FILE, (Object val) -> StorageType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.FILE_RACE_DIRECTORY, "storage.file.directory", ConfigType.STRING, "races")
        .add(ConfigKey.RACE_PREPARE_TIME, "prepare_time", ConfigType.INTEGER, 60)
        .add(ConfigKey.RACE_ANNOUNCE_INTERVALS, "race_announce_intervals", ConfigType.LIST, Arrays.asList(30, 10))
        .add(ConfigKey.COUNTDOWN, "countdown", ConfigType.INTEGER, 10)
        .add(ConfigKey.RESPAWN_PLAYER_DEATH, "respawn.player.death", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_PLAYER_INTERACT, "respawn.player.interact", ConfigType.STRING, RespawnType.NONE, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_ELYTRA_DEATH, "respawn.elytra.death", ConfigType.STRING, RespawnType.FROM_START, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_ELYTRA_INTERACT, "respawn.elytra.interact", ConfigType.STRING, RespawnType.FROM_START, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_PIG_DEATH, "respawn.pig.death", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_PIG_INTERACT, "respawn.pig.interact", ConfigType.STRING, RespawnType.NONE, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_HORSE_DEATH, "respawn.horse.death", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_HORSE_INTERACT, "respawn.horse.interact", ConfigType.STRING, RespawnType.NONE, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_BOAT_DEATH, "respawn.boat.death", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_BOAT_INTERACT, "respawn.boat.interact", ConfigType.STRING, RespawnType.NONE, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_MINECART_DEATH, "respawn.minecart.death", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.RESPAWN_MINECART_INTERACT, "respawn.minecart.interact", ConfigType.STRING, RespawnType.FROM_LAST_CHECKPOINT, (Object val) -> RespawnType.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.DISCORD_ENABLED, "discord.enabled", ConfigType.BOOLEAN, false)
        .add(ConfigKey.DISCORD_TOKEN, "discord.bot_token", ConfigType.STRING, "")
        .add(ConfigKey.DISCORD_ANNOUNCE_CHANNEL, "discord.announce_channel", ConfigType.STRING, "")
        .add(ConfigKey.ADVENTURE_ON_START, "adventure_mode_on_start", ConfigType.BOOLEAN, true)
        .add(ConfigKey.FRIENDLY_FIRE_COUNTDOWN, "friendlyfire.countdown", ConfigType.BOOLEAN, false)
        .add(ConfigKey.FRIENDLY_FIRE_STARTED, "friendlyfire.started", ConfigType.BOOLEAN, false)
        .add(ConfigKey.COLLISION_COUNTDOWN, "collision.countdown", ConfigType.BOOLEAN, false)
        .add(ConfigKey.COLLISION_STARTED, "collision.started", ConfigType.BOOLEAN, false)
        .add(ConfigKey.ELYTRA_RESPAWN_ON_GROUND, "elytra_respawn_on_ground", ConfigType.BOOLEAN, true)
        .add(ConfigKey.BLOCKED_COMMANDS, "blocked_commands", ConfigType.LIST, Arrays.asList("spawn", "wild", "wilderness", "rtp", "tpa", "tpo", "tp", "tpahere", "tpaccept", "tpdeny", "tpyes", "tpno", "tppos", "warp", "home", "rc spawn", "racing spawn"))
        .add(ConfigKey.PREVENT_JOIN_FROM_GAME_MODE, "prevent_join_from_game_mode", ConfigType.LIST, Collections.emptyList(), (Object value) -> {
          ArrayList<GameMode> gameModes = new ArrayList<>();
          if(value instanceof ArrayList<?>) {
            for (String gameMode : (ArrayList<String>) value) {
              gameModes.add(GameMode.valueOf(gameMode.toUpperCase(Locale.ENGLISH)));
            }
          }
          return gameModes;
        })
        .add(ConfigKey.START_ON_JOIN_SIGN, "start_on_join.sign", ConfigType.BOOLEAN, false)
        .add(ConfigKey.START_ON_JOIN_COMMAND, "start_on_join.command", ConfigType.BOOLEAN, false)
        .add(ConfigKey.TELEPORT_AFTER_RACE_ENABLED, "teleport_after_race.enabled", ConfigType.BOOLEAN, false)
        .add(ConfigKey.TELEPORT_AFTER_RACE_ENABLED_WHEN, "teleport_after_race.when", ConfigType.STRING, TeleportAfterRaceWhen.PARTICIPANT_FINISHES, (Object val) -> TeleportAfterRaceWhen.valueOf(((String)val).toUpperCase(Locale.ENGLISH)))
        .add(ConfigKey.VERBOSE, "verbose", ConfigType.BOOLEAN, false)
        .add(ConfigKey.CHECKPOINT_PARTICLES_DURING_RACE, "checkpoint_particles_during_race", ConfigType.BOOLEAN, true)
        .build();
    } catch (Exception e) {
      setEnabled(false);
      getLogger().log(Level.SEVERE, e.getMessage(), e);
      return;
    }


    messageManager = new MessagesBuilder()
      .add(MessageKey.CREATE_RACE_SUCCESS, "commands.create_race.success")
      .add(MessageKey.CREATE_RACE_NAME_OCCUPIED, "commands.create_race.error_name_occupied")
      .add(MessageKey.DELETE_RACE_SUCCESS, "commands.delete_race.success")
      .add(MessageKey.CHANGE_RACE_NAME_SUCCESS, "commands.change_race_name.success")
      .add(MessageKey.RACE_ADD_CHECKPOINT_SUCCESS, "commands.race_add_checkpoint.success")
      .add(MessageKey.RACE_ADD_CHECKPOINT_IS_OCCUPIED, "commands.race_add_checkpoint.error_is_occupied")
      .add(MessageKey.RACE_DELETE_CHECKPOINT_SUCCESS, "commands.race_delete_checkpoint.success")
      .add(MessageKey.RACE_ADD_STARTPOINT_SUCCESS, "commands.race_add_startpoint.success")
      .add(MessageKey.RACE_ADD_STARTPOINT_IS_OCCUPIED, "commands.race_add_startpoint.error_is_occupied")
      .add(MessageKey.RACE_DELETE_STARTPOINT_SUCCESS, "commands.race_delete_startpoint.success")
      .add(MessageKey.RACE_SPAWN_NOT_ENABLED, "commands.race_spawn.error_not_enabled")
      .add(MessageKey.RACE_SET_SPAWN_SUCCESS, "commands.race_set_spawn.success")
      .add(MessageKey.LIST_RACES_LIST, "commands.list_races.race_list")
      .add(MessageKey.LIST_RACES_ITEM, "commands.list_races.race_list_item")
      .add(MessageKey.RACE_SET_TYPE_SUCCESS, "commands.race_set_type.success")
      .add(MessageKey.RACE_SET_TYPE_NOCHANGE, "commands.race_set_type.error_nochange")
      .add(MessageKey.RACE_SET_SONG_SUCCESS, "commands.race_set_song.success")
      .add(MessageKey.RACE_SET_SONG_NOCHANGE, "commands.race_set_song.error_nochange")
      .add(MessageKey.RACE_UNSET_SONG_SUCCESS, "commands.race_unset_song.success")
      .add(MessageKey.RACE_UNSET_SONG_ALREADY_UNSET, "commands.race_unset_song.error_already_unset")
      .add(MessageKey.START_RACE_ALREADY_STARTED, "commands.start_race.error_already_started")
      .add(MessageKey.START_RACE_MISSING_STARTPOINT, "commands.start_race.error_missing_startpoint")
      .add(MessageKey.START_RACE_MISSING_CHECKPOINT, "commands.start_race.error_missing_checkpoint")
      .add(MessageKey.START_RACE_MISSING_CHECKPOINTS, "commands.start_race.error_missing_checkpoints")
      .add(MessageKey.START_RACE_NOT_ENABLED, "commands.start_race.error_not_enabled")
      .add(MessageKey.START_RACE_NO_ENABLED, "commands.start_race.error_no_enabled")
      .add(MessageKey.STOP_RACE_SUCCESS, "commands.stop_race.success")
      .add(MessageKey.STOP_RACE_NOT_STARTED, "commands.stop_race.error_not_started")
      .add(MessageKey.JOIN_RACE_SUCCESS, "commands.join_race.success")
      .add(MessageKey.JOIN_RACE_CHARGED, "commands.join_race.charged")
      .add(MessageKey.JOIN_RACE_NOT_OPEN, "commands.join_race.error_not_open")
      .add(MessageKey.JOIN_RACE_IS_FULL, "commands.join_race.error_is_full")
      .add(MessageKey.JOIN_RACE_IS_PARTICIPATING, "commands.join_race.error_is_participating")
      .add(MessageKey.JOIN_RACE_IS_PARTICIPATING_OTHER, "commands.join_race.error_is_participating_other")
      .add(MessageKey.JOIN_RACE_NOT_AFFORD, "commands.join_race.error_not_afford")
      .add(MessageKey.JOIN_RACE_GAME_MODE, "commands.join_race.error_game_mode")
      .add(MessageKey.RACE_SKIP_WAIT_NOT_STARTED, "commands.race_skip_wait.error_not_started")
      .add(MessageKey.RELOAD_SUCCESS, "commands.reload.success")
      .add(MessageKey.RELOAD_FAILED, "commands.reload.failed")
      .add(MessageKey.RELOAD_NOT_RACES, "commands.reload.not_races")
      .add(MessageKey.RELOAD_RACES_FAILED, "commands.reload.races_failed")
      .add(MessageKey.RELOAD_NOT_LANGUAGE, "commands.reload.not_language")
      .add(MessageKey.RACE_SET_STATE_SUCCESS, "commands.race_set_state.success")
      .add(MessageKey.RACE_SET_STATE_NOCHANGE, "commands.race_set_state.error_nochange")
      .add(MessageKey.RACE_SET_STATE_ONGOING, "commands.race_set_state.error_ongoing")
      .add(MessageKey.RACE_HELP_TITLE, "commands.race_help.title")
      .add(MessageKey.RACE_HELP_ITEM, "commands.race_help.item")
      .add(MessageKey.RACE_SET_ENTRYFEE, "commands.race_set_entryfee.success")
      .add(MessageKey.RACE_SET_WALKSPEED, "commands.race_set_walkspeed.success")
      .add(MessageKey.RACE_SET_PIG_SPEED, "commands.race_set_pig_speed.success")
      .add(MessageKey.RACE_SET_HORSE_SPEED, "commands.race_set_horse_speed.success")
      .add(MessageKey.RACE_SET_HORSE_JUMP_STRENGTH, "commands.race_set_horse_jump_strength.success")
      .add(MessageKey.RACE_ADD_POTION_EFFECT, "commands.race_add_potion_effect.success")
      .add(MessageKey.RACE_REMOVE_POTION_EFFECT, "commands.race_remove_potion_effect.success")
      .add(MessageKey.RACE_CLEAR_POTION_EFFECTS, "commands.race_clear_potion_effects.success")
      .add(MessageKey.RACE_LEAVE_NOT_PARTICIPATING, "commands.race_leave.error_not_participating")
      .add(MessageKey.RACE_LEAVE_SUCCESS, "commands.race_leave.success")
      .add(MessageKey.RACE_LEAVE_BROADCAST, "commands.race_leave.leave_broadcast")
      .add(MessageKey.RACE_LEAVE_PAYBACK, "commands.race_leave.leave_payback")
      .add(MessageKey.RACE_INFO_SUCCESS, "commands.race_info.success")
      .add(MessageKey.RACE_INFO_NO_POTION_EFFECTS, "commands.race_info.no_potion_effects")
      .add(MessageKey.RACE_INFO_POTION_EFFECT, "commands.race_info.potion_effect_item")
      .add(MessageKey.RACE_INFO_ENTRY_FEE_LINE, "commands.race_info.entry_fee_line")
      .add(MessageKey.RACE_TOP_TYPE_FASTEST, "commands.race_top.types.fastest")
      .add(MessageKey.RACE_TOP_TYPE_MOST_RUNS, "commands.race_top.types.most_runs")
      .add(MessageKey.RACE_TOP_TYPE_MOST_WINS, "commands.race_top.types.most_wins")
      .add(MessageKey.RACE_TOP_TYPE_WIN_RATIO, "commands.race_top.types.win_ratio")
      .add(MessageKey.RACE_TOP_HEADER, "commands.race_top.header")
      .add(MessageKey.RACE_TOP_ITEM, "commands.race_top.item")
      .add(MessageKey.RACE_TOP_ITEM_NONE, "commands.race_top.item_none")
      .add(MessageKey.RACE_RESET_TOP, "commands.race_reset_top.success")
      .add(MessageKey.RACE_NOT_FOUND, "validators.race_not_found")
      .add(MessageKey.RACE_ALREADY_EXIST, "validators.race_already_exist")
      .add(MessageKey.CHECKPOINT_NOT_FOUND, "validators.checkpoint_not_found")
      .add(MessageKey.CHECKPOINT_ALREADY_EXIST, "validators.checkpoint_already_exist")
      .add(MessageKey.STARTPOINT_NOT_FOUND, "validators.startpoint_not_found")
      .add(MessageKey.STARTPOINT_ALREADY_EXIST, "validators.startpoint_already_exist")
      .add(MessageKey.TYPE_NOT_FOUND, "validators.type_not_found")
      .add(MessageKey.STATE_NOT_FOUND, "validators.state_not_found")
      .add(MessageKey.SONG_NOT_FOUND, "validators.song_not_found")
      .add(MessageKey.VALIDATE_NON_INTEGER, "validators.validate_non_integer")
      .add(MessageKey.VALIDATE_NON_NUMBER, "validators.validate_non_number")
      .add(MessageKey.VALIDATE_MIN_EXCEED, "validators.min_exceed")
      .add(MessageKey.VALIDATE_MAX_EXCEED, "validators.max_exceed")
      .add(MessageKey.RACE_POTION_EFFECT_NOT_FOUND, "validators.race_potion_effect_not_found")
      .add(MessageKey.POTION_EFFECT_NOT_FOUND, "validators.potion_effect_not_found")
      .add(MessageKey.STAT_TYPE_NOT_FOUND, "validators.stat_type_not_found")
      .add(MessageKey.RACE_CANCELED, "race_canceled")
      .add(MessageKey.NOSHOW_DISQUALIFIED, "race_start_noshow_disqualified")
      .add(MessageKey.GAME_MODE_DISQUALIFIED, "race_start_gamemode_disqualified")
      .add(MessageKey.GAME_MODE_DISQUALIFIED_TARGET, "race_start_gamemode_disqualified_target")
      .add(MessageKey.QUIT_DISQUALIFIED, "race_start_quit_disqualified")
      .add(MessageKey.DEATH_DISQUALIFIED, "race_death_disqualified")
      .add(MessageKey.DEATH_DISQUALIFIED_TARGET, "race_death_disqualified_target")
      .add(MessageKey.EDIT_NO_EDIT_MODE, "edit_no_edit_mode")
      .add(MessageKey.RACE_PARTICIPANT_RESULT, "race_participant_result")
      .add(MessageKey.PARTICIPATE_CLICK_TEXT, "race_participate_click_text")
      .add(MessageKey.PARTICIPATE_HOVER_TEXT, "race_participate_hover_text")
      .add(MessageKey.PARTICIPATE_TEXT, "race_participate_text")
      .add(MessageKey.PARTICIPATE_TEXT_FEE, "race_participate_text_fee")
      .add(MessageKey.PARTICIPATE_DISCORD, "race_participate_discord")
      .add(MessageKey.PARTICIPATE_DISCORD_FEE, "race_participate_discord_fee")
      .add(MessageKey.PARTICIPATE_TEXT_TIMELEFT, "race_participate_text_timeleft")
      .add(MessageKey.RACE_COUNTDOWN, "race_countdown_subtitle")
      .add(MessageKey.RACE_NEXT_LAP, "race_next_lap_actionbar")
      .add(MessageKey.RACE_FINAL_LAP, "race_final_lap_actionbar")
      .add(MessageKey.RESPAWN_INTERACT_START, "race_type_respawn_start_info")
      .add(MessageKey.RESPAWN_INTERACT_LAST, "race_type_respawn_last_info")
      .add(MessageKey.SKIP_WAIT_HOVER_TEXT, "race_skipwait_hover_text")
      .add(MessageKey.SKIP_WAIT_CLICK_TEXT, "race_skipwait_click_text")
      .add(MessageKey.SKIP_WAIT, "race_skipwait")
      .add(MessageKey.STOP_RACE_HOVER_TEXT, "race_stop_hover_text")
      .add(MessageKey.STOP_RACE_CLICK_TEXT, "race_stop_click_text")
      .add(MessageKey.STOP_RACE, "race_stop")
      .add(MessageKey.SIGN_REGISTERED, "race_sign_registered")
      .add(MessageKey.SIGN_UNREGISTERED, "race_sign_unregistered")
      .add(MessageKey.RACE_SIGN_LINES, "race_sign_lines")
      .add(MessageKey.SIGN_NOT_STARTED, "race_sign_status_not_started")
      .add(MessageKey.SIGN_LOBBY, "race_sign_status_lobby")
      .add(MessageKey.SIGN_STARTED, "race_sign_status_in_game")
      .add(MessageKey.BLOCKED_CMDS, "race_blocked_cmd")
      .add(MessageKey.NO_PERMISSION_COMMAND, "no_permission_command")
      .add(MessageKey.MISSING_ARGUMENTS_COMMAND, "missing_arguments_command")
      .add(MessageKey.COMMAND_NOT_FOUND, "command_not_found")
      .add(MessageKey.TIME_UNIT_SECOND, "timeunit.second")
      .add(MessageKey.TIME_UNIT_SECONDS, "timeunit.seconds")
      .add(MessageKey.TIME_UNIT_MINUTE, "timeunit.minute")
      .add(MessageKey.TIME_UNIT_MINUTES, "timeunit.minutes")
      .add(MessageKey.TIME_UNIT_HOUR, "timeunit.hour")
      .add(MessageKey.TIME_UNIT_HOURS, "timeunit.hours")
      .add(MessageKey.TIME_UNIT_DAY, "timeunit.day")
      .add(MessageKey.TIME_UNIT_DAYS, "timeunit.days")
      .add(MessageKey.TIME_UNIT_NOW, "timeunit.now")
      .build();

    translations = new Translations(this, messageManager);
    Translation translation = translations.createTranslation(configuration.get(ConfigKey.LANGUAGE));
    Translation fallbackTranslation = translations.createTranslation("english");
    messageManager.setTranslation(translation, fallbackTranslation);

    if(isNoteBlockAPILoaded) {
      SongManager.init(this);
      getServer().getPluginManager().registerEvents(SongManager.getInstance(), this);
    }

    raceCommandExecutor = new RaceCommandExecutor();
    racingManager = new RacingManager();
    SignManager signManager = new SignManager(racingManager);
    DiscordManager discordManager = new DiscordManager();

    getServer().getPluginManager().registerEvents(raceCommandExecutor, this);
    getServer().getPluginManager().registerEvents(racingManager, this);
    getServer().getPluginManager().registerEvents(signManager, this);
    getServer().getPluginManager().registerEvents(discordManager, this);

    StorageType storageType = Racing.getInstance().getConfiguration().get(ConfigKey.STORAGE);
    switch (storageType) {
      case FILE:
        racingManager.setAPI(new FileAPI(this));
        break;
      case CUSTOM:
        break;
      default:
    }

    setupCommands();
    racingManager.load();
    initMcMMO();
  }

  @Override
  public void onDisable() {
    if(racingManager != null) {
      racingManager.shutdown();
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    return carbon.handleCommand(sender, command, args);
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    return carbon.handleAutoComplete(sender, command, args);
  }

  void initMcMMO() {
    Plugin plugin = getServer().getPluginManager().getPlugin("mcMMO");

    if (!(plugin instanceof mcMMO)) {
      return;
    }

    Bukkit.getPluginManager().registerEvents(new McMMOListener(racingManager), Racing.getInstance());
  }

  public static void debug(String message, Object... args) {
    if(Racing.getInstance().getConfiguration().get(ConfigKey.VERBOSE)) {
      try {
        Racing.getInstance().getLogger().info(String.format(message, args));
      } catch (IllegalFormatConversionException e) {
        Racing.getInstance().getLogger().log(Level.SEVERE, e.getMessage(), e);
      }
    }
  }
}
