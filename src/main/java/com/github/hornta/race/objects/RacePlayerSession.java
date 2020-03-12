package com.github.hornta.race.objects;

import com.github.hornta.race.Racing;
import com.github.hornta.race.Util;
import com.github.hornta.race.enums.RaceType;
import com.github.hornta.race.enums.RespawnType;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class RacePlayerSession {
  public static final double MAX_HEALTH = 20;
  public static final int MAX_FOOD_LEVEL = 20;
  private final RaceSession raceSession;
  private final double chargedEntryFee;
  private final UUID playerId;
  private final String playerName;
  private Player player;
  private Location startLocation;
  private RaceCheckpoint currentCheckpoint;
  private RaceCheckpoint nextCheckpoint;
  private BossBar bossBar;
  private Entity vehicle;
  private int currentLap;
  private long lapStartTime;
  private long fastestLap = Long.MAX_VALUE;
  private long personalBestLapTime = Long.MAX_VALUE;
  private boolean isAllowedToEnterVehicle;
  private boolean isAllowedToExitVehicle;
  private RaceParticipantReset restore;

  RacePlayerSession(RaceSession raceSession, Player player, double chargedEntryFee) {
    this.raceSession = raceSession;
    this.player = player;
    this.chargedEntryFee = chargedEntryFee;
    this.playerId = player.getUniqueId();
    this.playerName = player.getName();
    if(raceSession.getRace().getResultByPlayerId().containsKey(playerId))
    {
      this.personalBestLapTime = raceSession.getRace().getResultByPlayerId().get(playerId).getFastestLap();
    }
    Racing.debug("New RacePlayerSession\nPlayer: %s\nUUID: %s\nCharged: %f", playerName, playerId, chargedEntryFee);
  }

  public void setPlayer(Player player) {
    this.player = player;
  }

  public boolean hasPlayer() {
    return player != null;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public String getPlayerName() {
    return playerName;
  }

  public void setLapStartTime(long millis)
  {
    lapStartTime = millis;
  }

  public long getLapStartTime()
  {
    return lapStartTime;
  }

  public void setFastestLapTime(long millis)
  {
    if(millis < fastestLap)
    {
      fastestLap = millis;
    }
    if(millis < personalBestLapTime)
    {
      personalBestLapTime = millis;
    }
  }

  public long getFastestLapTime()
  {
    return fastestLap;
  }

  public long getPersonalBestLapTime()
  {
    return personalBestLapTime;
  }

  public boolean isFinished()
  {
    return nextCheckpoint == null;
  }

  void startCooldown() {
    Racing.debug("Starting cooldown for RacePlayerSession %s", playerName);

    restore = new RaceParticipantReset(this);

    if(player.isInsideVehicle()) {
      Racing.debug("%s is inside a vehicle. Attempting to eject it...", player.getName());
      isAllowedToExitVehicle = true;
      player.getVehicle().eject();
      isAllowedToExitVehicle = false;
      Racing.debug("Result of ejecting %s from vehicle: %B", player.getName(), player.getVehicle() == null);
    }

    Racing.debug("Teleporting %s to start location", player.getName());
    respawn(RespawnType.FROM_START, () -> {
      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
    }, () -> {
      player.setHealth(MAX_HEALTH);
      Racing.debug("Setting health to %f on %s", MAX_HEALTH, player.getName());
    });

    if(raceSession.getRace().getType() == RaceType.HORSE) {
      freezeHorse();
      Racing.debug("Freezed horse movement");
    }
  }

  Entity getVehicle() {
    return vehicle;
  }

  public double getChargedEntryFee() {
    return chargedEntryFee;
  }

  void startRace() {
    player.setWalkSpeed(raceSession.getRace().getWalkSpeed());
    player.setFoodLevel(MAX_FOOD_LEVEL);
    player.removePotionEffect(PotionEffectType.JUMP);

    if(vehicle instanceof Pig) {
      player.getInventory().setItemInMainHand(new ItemStack(Material.CARROT_ON_A_STICK, 1));
    } else if(vehicle instanceof Horse) {
      unfreezeHorse();
    }

    if(raceSession.getRace().getType() == RaceType.ELYTRA) {
      player.getInventory().setChestplate(new ItemStack(Material.ELYTRA, 1));
    }
  }

  public void respawnInVehicle() {
    Location location;
    if (currentCheckpoint == null || raceSession.getRace().getType() == RaceType.ELYTRA) {
      location = startLocation;
    } else {
      location = currentCheckpoint.getLocation();
    }

    respawnInVehicle(location, null, null);
  }

  public void respawnInVehicle(Location location, Runnable runnable, Runnable fireTicksResetCallback) {
    if(getVehicle() != null) {
      exitVehicle();
      getVehicle().remove();
    }

    switch (raceSession.getRace().getType()) {
      case PIG:
        spawnVehicle(EntityType.PIG, location);
        setupPig();
        break;
      case MINECART:
        spawnVehicle(EntityType.MINECART, location);
        break;
      case HORSE:
        HorseData horseData = null;
        if(vehicle != null) {
          horseData = new HorseData((Horse) vehicle);
        }
        spawnVehicle(EntityType.HORSE, location);
        setupHorse(horseData);
        break;
      case BOAT:
        spawnVehicle(EntityType.BOAT, location);
        setupBoat();
        break;
      case ELYTRA:
      case PLAYER:
      default:
        //no vehichle, do nothing, intentional fall throughs.
        break;
    }

    if(vehicle != null) {
      vehicle.setInvulnerable(true);
      Racing.debug("Making vehicle invulnerable");
    }

    Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
      Location playerTeleportLoc = location;

      if(vehicle instanceof Boat) {
        playerTeleportLoc = playerTeleportLoc.clone().add(0, -0.45, 0);
      }

      PaperLib.teleportAsync(player, playerTeleportLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
      Racing.debug("Teleported %s to vehicle %s", player.getName(), vehicle.getType());

      // important to set this after teleporting away from a potential source of fire.
      // 2 ticks looks like the minimum amount of ticks needed to wait after setting it to zero...
      Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
        player.setFireTicks(0);
        if (fireTicksResetCallback != null) {
          fireTicksResetCallback.run();
        }
      }, 2);
      enterVehicle();
      if (runnable != null) {
        runnable.run();
      }
    }, 1L);
  }

  public void respawn(RespawnType type, Runnable runnable, Runnable fireTicksResetCallback) {
    Racing.debug("Respawn %s %s", player.getName(), type);
    if(type == null) {
      throw new NullPointerException();
    }

    Location loc;
    if(type == RespawnType.FROM_START || currentCheckpoint == null) {
      loc = startLocation;
    } else {
      loc = currentCheckpoint.getLocation();
      if(vehicle instanceof Boat) {
        loc.add(0, -0.5, 0);
      }
    }

    if (player.isSleeping()) {
      Racing.debug("%s was sleeping. Trying to wake it up...", player.getName());
      // 2 seconds of very high resistance
      player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, 255, false, false, false));
      player.addPotionEffect(new PotionEffect(PotionEffectType.HARM, 1, 1, false, false, false));
      Racing.debug("Result of waking %s up: %B", player.getName(), player.isSleeping());
    }

    Racing.debug("Attempting to set fall distance to zero on %s to prevent taking damage from falling", player.getName());
    player.setFallDistance(0);
    Racing.debug("Fall distance on %s was set to %f", player.getName(), player.getFallDistance());

    switch (raceSession.getRace().getType()) {
      case PLAYER:
      case ELYTRA:
        PaperLib.teleportAsync(player, loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
        Bukkit.getScheduler().scheduleSyncDelayedTask(Racing.getInstance(), () -> {
          player.setFireTicks(0);
          if(fireTicksResetCallback != null) {
            fireTicksResetCallback.run();
          }
        }, 2);
        break;
      case MINECART:
      case BOAT:
      case HORSE:
      case PIG:
        respawnInVehicle(loc, runnable, fireTicksResetCallback);
        break;
    }
  }

  private void spawnVehicle(EntityType type, Location location) {
    Location spawnLocation = location;
    Racing.debug("Attempting to spawn vehicle of type %s at %s", type, spawnLocation);
    vehicle = startLocation.getWorld().spawnEntity(spawnLocation, type);
    Racing.debug("Spawned vehicle at " + vehicle.getLocation());
  }

  private void setupPig() {
    ((Pig)vehicle).setAI(false);
    Racing.debug("Disable pig AI");
    ((Pig)vehicle).setSaddle(true);
    Racing.debug("Giving pig a saddle");
    ((Pig)vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getPigSpeed());
    Racing.debug("Setting movementspeed on pig to " + raceSession.getRace().getPigSpeed());
  }

  private void setupHorse(HorseData horseData) {
    ((Horse) vehicle).setAI(false);
    Racing.debug("Disable horse AI");
    ((Horse) vehicle).setTamed(true);
    Racing.debug("Set horse to be tamed");
    ((Horse) vehicle).setOwner(player);
    Racing.debug("Set horse owner to " + player.getName());
    ((Horse) vehicle).getInventory().setSaddle(new ItemStack(Material.SADDLE, 1));
    Racing.debug("Giving horse a saddle");
    ((Horse) vehicle).setJumpStrength(raceSession.getRace().getHorseJumpStrength());
    Racing.debug("Setting horse jump strength to" + raceSession.getRace().getHorseJumpStrength());
    ((Horse) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getHorseSpeed());
    Racing.debug("Setting horse movement speed to " + raceSession.getRace().getHorseSpeed());

    if(horseData != null) {
      Racing.debug("Transferring old horse values to new horse");
      Racing.debug("Attempt to set horse color to " + horseData.getColor());
      ((Horse) vehicle).setColor(horseData.getColor());
      Racing.debug("Horse color set to " + ((Horse) vehicle).getColor());
      Racing.debug("Attempting to set horse style to " + horseData.getStyle());
      ((Horse) vehicle).setStyle(horseData.getStyle());
      Racing.debug("Horse style set to " + ((Horse) vehicle).getStyle());
      Racing.debug("Attempting to set horse age to " + horseData.getAge());
      ((Horse) vehicle).setAge(horseData.getAge());
      Racing.debug("Horse age set to " + ((Horse) vehicle).getAge());
    }
  }

  public void freezeHorse() {
    ((Horse) vehicle).setJumpStrength(0);
    ((Horse) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
  }

  private void unfreezeHorse() {
    ((Horse) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getHorseSpeed());
    ((Horse) vehicle).setJumpStrength(raceSession.getRace().getHorseJumpStrength());
  }

  private void setupBoat() {
    ((Boat)vehicle).setWoodType(TreeSpecies.GENERIC);
    Racing.debug("Setting boat type to " + TreeSpecies.GENERIC);
  }

  void setStartPoint(RaceStartPoint startPoint) {
    startLocation = Util.snapAngles(startPoint.getLocation());
  }

  public Location getStartLocation() {
    return startLocation;
  }

  public Player getPlayer() {
    return player;
  }

  boolean isAllowedToEnterVehicle() {
    return isAllowedToEnterVehicle;
  }

  boolean isAllowedToExitVehicle() {
    return isAllowedToExitVehicle;
  }

  void enterVehicle() {
    Racing.debug("Attempting to enter passanger %s to vehicle %s", player.getName(), getVehicle().getType());
    isAllowedToEnterVehicle = true;
    getVehicle().addPassenger(player);
    isAllowedToEnterVehicle = false;
    Racing.debug("Result of attempting to enter %s into %s: %b", player.getName(), getVehicle().getType(), player.isInsideVehicle());
  }

  void exitVehicle() {
    isAllowedToExitVehicle = true;
    getVehicle().removePassenger(player);
    isAllowedToExitVehicle = false;
  }

  void restore() {
    if(restore == null) {
      return;
    }

    Racing.debug("Restoring %s...", player.getName());

    startLocation = null;
    currentCheckpoint = null;

    // its null when player has finished
    if(nextCheckpoint != null) {
      nextCheckpoint.removePlayer(player);
      nextCheckpoint = null;
    }

    restore.restore();
    restore = null;
    bossBar.removeAll();
    bossBar = null;
    if(getVehicle() != null) {
      Racing.debug("Attempt to eject %s from vehicle", player.getName());
      exitVehicle();
      getVehicle().remove();
      Racing.debug("%s ejected from vehicle result: %b", player.getName(), player.getVehicle() == null);
    }
    vehicle = null;
  }

  public RaceCheckpoint getCurrentCheckpoint() {
    return currentCheckpoint;
  }

  public RaceCheckpoint getNextCheckpoint() {
    return nextCheckpoint;
  }

  public void setNextCheckpoint(RaceCheckpoint checkpoint) {
    currentCheckpoint = nextCheckpoint;

    if(currentCheckpoint != null) {
      currentCheckpoint.removePlayer(player);
    }

    nextCheckpoint = checkpoint;

    if(nextCheckpoint != null) {
      nextCheckpoint.addPlayer(player);
    }
  }

  public BossBar getBossBar() {
    return bossBar;
  }

  void setBossBar(BossBar bossBar) {
    bossBar.addPlayer(player);
    this.bossBar = bossBar;
  }

  public int getCurrentLap() {
    return currentLap;
  }

  public void setCurrentLap(int currentLap) {
    this.currentLap = currentLap;
  }

  public boolean isRestored() {
    return restore == null;
  }

  public RaceSession getRaceSession() {
    return raceSession;
  }
}
