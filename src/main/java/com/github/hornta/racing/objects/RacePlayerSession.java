package com.github.hornta.racing.objects;

import com.github.hornta.racing.RacingPlugin;
import com.github.hornta.racing.Util;
import com.github.hornta.racing.enums.RaceType;
import com.github.hornta.racing.enums.RespawnType;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.TreeSpecies;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Tameable;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RacePlayerSession {
	public static final double MAX_HEALTH = 20;
	public static final int MAX_FOOD_LEVEL = 20;
	private final RaceSession raceSession;
	private final double chargedEntryFee;
	private final Player player;
	private Location startLocation;
	private RaceCheckpoint currentCheckpoint;
	private RaceCheckpoint nextCheckpoint;
	private BossBar bossBar;
	private Entity vehicle;
	private int currentLap;
	private long lapStartTime;
	private long fastestLap = Long.MAX_VALUE;
	private long personalBestLapTime = Long.MAX_VALUE;
	private boolean allowedToEnterVehicle;
	private boolean allowedToExitVehicle;
	private RaceParticipantReset restore;

	RacePlayerSession(RaceSession raceSession, Player player, double chargedEntryFee) {
		this.raceSession = raceSession;
		this.player = player;
		this.chargedEntryFee = chargedEntryFee;
		if (raceSession.getRace().getResultByPlayerId().containsKey(player.getUniqueId())) {
			personalBestLapTime = raceSession.getRace().getResultByPlayerId().get(player.getUniqueId()).getFastestLap();
		}
	}

	public long getLapStartTime() {
		return lapStartTime;
	}

	public void setLapStartTime(long millis) {
		lapStartTime = millis;
	}

	public void setFastestLapTime(long millis) {
		if (millis < fastestLap) {
			fastestLap = millis;
		}
		if (millis < personalBestLapTime) {
			personalBestLapTime = millis;
		}
	}

	public long getFastestLap() {
		return fastestLap;
	}

	public long getPersonalBestLapTime() {
		return personalBestLapTime;
	}

	public boolean isFinished() {
		return nextCheckpoint == null;
	}

	void startCooldown() {
		if (player.isInsideVehicle()) {
			RacingPlugin.debug("%s is inside a vehicle. Attempting to eject it...", player.getName());
			allowedToExitVehicle = true;
			player.getVehicle().eject();
			allowedToExitVehicle = false;
			RacingPlugin.debug("Result of ejecting %s from vehicle: %B", player.getName(), player.getVehicle() == null);
		}
		RacingPlugin.debug("Teleporting %s to start location", player.getName());
		respawn(RespawnType.FROM_START, () -> {
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
			restore = new RaceParticipantReset(this);
		}, () -> {
			player.setHealth(MAX_HEALTH);
			RacingPlugin.debug("Setting health to %f on %s", MAX_HEALTH, player.getName());
		});
		if (raceSession.getRace().getType() == RaceType.HORSE) {
			freezeHorse();
			RacingPlugin.debug("Freezed horse movement");
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
		if (vehicle instanceof Pig) {
			player.getInventory().setItemInMainHand(new ItemStack(Material.CARROT_ON_A_STICK, 1));
		} else if (vehicle instanceof Strider) {
			player.getInventory().setItemInMainHand(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK, 1));
		} else if (vehicle instanceof Horse) {
			unfreezeHorse();
		}
		if (raceSession.getRace().getType() == RaceType.ELYTRA) {
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
		if (vehicle != null) {
			exitVehicle();
			vehicle.remove();
		}
		switch (raceSession.getRace().getType()) {
			case PIG:
				spawnVehicle(EntityType.PIG, location);
				setupPig();
				break;
			case STRIDER:
				spawnVehicle(EntityType.STRIDER, location);
				setupStrider();
				break;
			case MINECART:
				spawnVehicle(EntityType.MINECART, location);
				break;
			case HORSE:
				HorseData horseData = null;
				if (vehicle != null) {
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
		if (vehicle != null) {
			vehicle.setInvulnerable(true);
			RacingPlugin.debug("Making vehicle invulnerable");
		}
		Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
			Location playerTeleportLoc = location;
			if (vehicle instanceof Boat) {
				playerTeleportLoc = playerTeleportLoc.clone().add(0, -0.45, 0);
			}
			PaperLib.teleportAsync(player, playerTeleportLoc, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept((Boolean result) -> {
				Bukkit.getScheduler().runTask(RacingPlugin.getInstance(), () -> {
					RacingPlugin.debug("Teleported %s to vehicle %s", player.getName(), vehicle.getType());
					// important to set this after teleporting away from a potential source of fire.
					// 2 ticks looks like the minimum amount of ticks needed to wait after setting it to zero...
					Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
						player.setFireTicks(0);
						if (fireTicksResetCallback != null) {
							fireTicksResetCallback.run();
						}
					}, 2);
					enterVehicle(vehicle);
					if (runnable != null) {
						runnable.run();
					}
				});
			});
		}, 1L);
	}

	public void respawn(RespawnType type, Runnable runnable, Runnable fireTicksResetCallback) {
		RacingPlugin.debug("Respawn %s %s", player.getName(), type);
		if (type == null) {
			throw new NullPointerException();
		}
		Location loc;
		if (type == RespawnType.FROM_START || currentCheckpoint == null) {
			loc = startLocation;
		} else {
			loc = currentCheckpoint.getLocation();
			if (vehicle instanceof Boat) {
				loc.add(0, -0.5, 0);
			}
		}
		if (player.isSleeping()) {
			RacingPlugin.debug("%s was sleeping. Trying to wake them up...", player.getName());
			// 2 seconds of very high resistance
			player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, 255, false, false, false));
			player.addPotionEffect(new PotionEffect(PotionEffectType.HARM, 1, 1, false, false, false));
			RacingPlugin.debug("Result of waking %s up: %B", player.getName(), player.isSleeping());
		}
		RacingPlugin.debug("Attempting to set fall distance to zero on %s to prevent taking damage from falling", player.getName());
		player.setFallDistance(0);
		RacingPlugin.debug("Fall distance on %s was set to %f", player.getName(), player.getFallDistance());
		switch (raceSession.getRace().getType()) {
			case PLAYER:
			case ELYTRA:
				PaperLib.teleportAsync(player, loc, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept((Boolean result) -> {
					Bukkit.getScheduler().runTaskLater(RacingPlugin.getInstance(), () -> {
						Bukkit.getScheduler().scheduleSyncDelayedTask(RacingPlugin.getInstance(), () -> {
							player.setFireTicks(0);
							if (fireTicksResetCallback != null) {
								fireTicksResetCallback.run();
							}
						}, 2);
						if (result && runnable != null) {
							runnable.run();
						}
						// Multiverse is changing the players game mode after teleporting to the world containing the
						// start position which is why we need to run this callback on the CURRENT_TICK + 2 so that
						// Multiverse has time to run their task that(found in link) before our task so we have the final say
						// in what game mode the player should have.
						// https://github.com/Multiverse/Multiverse-Core/blob/1fac13247f297a5d6043b475cade3d18f5d54c2b/src/main/java/com/onarandombox/MultiverseCore/listeners/MVPlayerListener.java#L351
						//
						// runTaskLater(, , 0) would make the callback run on the next tick which isn't desirable
						// because of the above statement
					}, 1);
				});
				break;
			case MINECART:
			case BOAT:
			case HORSE:
			case PIG:
			case STRIDER:
				respawnInVehicle(loc, runnable, fireTicksResetCallback);
				break;
		}
	}

	private void spawnVehicle(EntityType type, Location location) {
		RacingPlugin.debug("Attempting to spawn vehicle of type %s at %s", type, location);
		vehicle = startLocation.getWorld().spawnEntity(location, type);
		RacingPlugin.debug("Spawned vehicle at " + vehicle.getLocation());
	}

	private void setupPig() {
		((LivingEntity) vehicle).setAI(false);
		RacingPlugin.debug("Disable pig AI");
		((Steerable) vehicle).setSaddle(true);
		RacingPlugin.debug("Giving pig a saddle");
		((Attributable) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getPigSpeed());
		RacingPlugin.debug("Setting movementspeed on pig to " + raceSession.getRace().getPigSpeed());
	}

	private void setupStrider() {
		((LivingEntity) vehicle).setAI(false);
		RacingPlugin.debug("Disable strider AI");
		((Steerable) vehicle).setSaddle(true);
		RacingPlugin.debug("Giving strider a saddle");
		((Attributable) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getStriderSpeed());
		RacingPlugin.debug("Setting movementspeed on strider to " + raceSession.getRace().getStriderSpeed());
	}

	private void setupHorse(HorseData horseData) {
		((LivingEntity) vehicle).setAI(false);
		RacingPlugin.debug("Disable horse AI");
		((Tameable) vehicle).setTamed(true);
		RacingPlugin.debug("Set horse to be tamed");
		((Tameable) vehicle).setOwner(player);
		RacingPlugin.debug("Set horse owner to " + player.getName());
		((AbstractHorse) vehicle).getInventory().setSaddle(new ItemStack(Material.SADDLE, 1));
		RacingPlugin.debug("Giving horse a saddle");
		((AbstractHorse) vehicle).setJumpStrength(raceSession.getRace().getHorseJumpStrength());
		RacingPlugin.debug("Setting horse jump strength to" + raceSession.getRace().getHorseJumpStrength());
		((Attributable) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getHorseSpeed());
		RacingPlugin.debug("Setting horse movement speed to " + raceSession.getRace().getHorseSpeed());
		if (horseData != null) {
			RacingPlugin.debug("Transferring old horse values to new horse");
			RacingPlugin.debug("Attempt to set horse color to " + horseData.getColor());
			((Horse) vehicle).setColor(horseData.getColor());
			RacingPlugin.debug("Horse color set to " + ((Horse) vehicle).getColor());
			RacingPlugin.debug("Attempting to set horse style to " + horseData.getStyle());
			((Horse) vehicle).setStyle(horseData.getStyle());
			RacingPlugin.debug("Horse style set to " + ((Horse) vehicle).getStyle());
			RacingPlugin.debug("Attempting to set horse age to " + horseData.getAge());
			((Ageable) vehicle).setAge(horseData.getAge());
			RacingPlugin.debug("Horse age set to " + ((Ageable) vehicle).getAge());
		}
	}

	public void freezeHorse() {
		((AbstractHorse) vehicle).setJumpStrength(0);
		((Attributable) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
	}

	private void unfreezeHorse() {
		((Attributable) vehicle).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(raceSession.getRace().getHorseSpeed());
		((AbstractHorse) vehicle).setJumpStrength(raceSession.getRace().getHorseJumpStrength());
	}

	private void setupBoat() {
		((Boat) vehicle).setWoodType(TreeSpecies.GENERIC);
		RacingPlugin.debug("Setting boat type to " + TreeSpecies.GENERIC);
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
		return allowedToEnterVehicle;
	}

	boolean isAllowedToExitVehicle() {
		return allowedToExitVehicle;
	}

	public void enterVehicle(Entity vehicle) {
		RacingPlugin.debug("Attempting to enter passenger %s to vehicle %s", player.getName(), vehicle.getType());
		allowedToEnterVehicle = true;
		vehicle.addPassenger(player);
		allowedToEnterVehicle = false;
		RacingPlugin.debug("Result of attempting to enter %s into %s: %b", player.getName(), vehicle.getType(), player.isInsideVehicle());
	}

	void exitVehicle() {
		allowedToExitVehicle = true;
		vehicle.removePassenger(player);
		allowedToExitVehicle = false;
	}

	void restore() {
		if (restore == null) {
			return;
		}
		RacingPlugin.debug("Restoring %s...", player.getName());
		startLocation = null;
		currentCheckpoint = null;
		// its null when player has finished
		if (nextCheckpoint != null) {
			nextCheckpoint.removePlayer(player);
			nextCheckpoint = null;
		}
		restore.restore();
		restore = null;
		bossBar.removeAll();
		bossBar = null;
		if (vehicle != null) {
			RacingPlugin.debug("Attempt to eject %s from vehicle", player.getName());
			exitVehicle();
			vehicle.remove();
			RacingPlugin.debug("%s ejected from vehicle result: %b", player.getName(), player.getVehicle() == null);
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
		if (currentCheckpoint != null) {
			currentCheckpoint.removePlayer(player);
		}
		nextCheckpoint = checkpoint;
		if (nextCheckpoint != null) {
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
