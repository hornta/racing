package com.github.hornta.race.objects;

import com.github.hornta.race.ConfigKey;
import com.github.hornta.race.Racing;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class RaceParticipantReset {
  private static final int PREVENT_SPRINT_FOOD_LEVEL = 6;

  private RacePlayerSession playerSession;
  private float walkSpeed;
  private int foodLevel;
  private ItemStack[] inventory;
  private Collection<PotionEffect> potionEffects;
  private double health;
  private GameMode gameMode;
  private int fireTicks;
  private boolean allowFlight;
  private float exp;
  private float exhaustion;
  private int level;
  private float saturation;

  RaceParticipantReset(RacePlayerSession playerSession) {
    this.playerSession = playerSession;
    gameMode = playerSession.getPlayer().getGameMode();
    walkSpeed = playerSession.getPlayer().getWalkSpeed();
    foodLevel = playerSession.getPlayer().getFoodLevel();
    potionEffects = new ArrayList<>(playerSession.getPlayer().getActivePotionEffects());
    health = playerSession.getPlayer().getHealth();
    fireTicks = playerSession.getPlayer().getFireTicks();
    allowFlight = playerSession.getPlayer().getAllowFlight();
    exp = playerSession.getPlayer().getExp();
    exhaustion = playerSession.getPlayer().getExhaustion();
    level = playerSession.getPlayer().getLevel();
    saturation = playerSession.getPlayer().getSaturation();

    ItemStack cursorItem = playerSession.getPlayer().getItemOnCursor();
    if(cursorItem.getAmount() > 0) {
      HashMap<Integer, ItemStack> rest = playerSession.getPlayer().getInventory().addItem(cursorItem);
      Racing.debug("Add items to inventory from cursor before closing inventory of %s", playerSession.getPlayer().getName());
      if(!rest.isEmpty()) {
        playerSession.getPlayer().setItemOnCursor(rest.get(0));
        Racing.debug("Setting cursor items of %s to %s", playerSession.getPlayer().getName(), rest.get(0));
      } else {
        playerSession.getPlayer().setItemOnCursor(null);
      }
    }
    playerSession.getPlayer().closeInventory();
    Racing.debug("Closing inventory of %s", playerSession.getPlayer().getName());
    inventory = playerSession.getPlayer().getInventory().getContents();

    if(Racing.getInstance().getConfiguration().<Boolean>get(ConfigKey.ADVENTURE_ON_START)) {
      playerSession.getPlayer().setGameMode(GameMode.ADVENTURE);
      Racing.debug("Setting game mode to %s on %s", GameMode.ADVENTURE, playerSession.getPlayer().getName());
    }
    Racing.debug("Attempting to set exhaustion to %f on %s", 0f, playerSession.getPlayer().getName());
    playerSession.getPlayer().setExhaustion(0);
    Racing.debug("Exhaustion was set to %f on %s", playerSession.getPlayer().getExhaustion(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to set saturation to %f on %s", 0f, playerSession.getPlayer().getName());
    playerSession.getPlayer().setSaturation(0);
    Racing.debug("Saturation was set to %f on %s", playerSession.getPlayer().getSaturation(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to set experience to %f on %s", 0f, playerSession.getPlayer().getName());
    playerSession.getPlayer().setExp(0);
    Racing.debug("Experience was set to %f on %s", playerSession.getPlayer().getExp(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to set level to %f on %s", 0f, playerSession.getPlayer().getName());
    playerSession.getPlayer().setLevel(0);
    Racing.debug("Level was set to %d on %s", playerSession.getPlayer().getLevel(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to set walk speed to %f on %s", 0f, playerSession.getPlayer().getName());
    playerSession.getPlayer().setWalkSpeed(0);
    Racing.debug("Walk speed was set to %f on %s", playerSession.getPlayer().getWalkSpeed(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to set food level to %d on %s", PREVENT_SPRINT_FOOD_LEVEL, playerSession.getPlayer().getName());
    playerSession.getPlayer().setFoodLevel(PREVENT_SPRINT_FOOD_LEVEL);
    Racing.debug("Setting food level to %d on %s", playerSession.getPlayer().getFoodLevel(), playerSession.getPlayer().getName());
    Racing.debug("Attempting to clear inventory of %s", playerSession.getPlayer().getName());
    playerSession.getPlayer().getInventory().clear();
    Racing.debug("Inventory of %s was cleared. Contents: %s", playerSession.getPlayer().getName(), playerSession.getPlayer().getInventory().getContents());

    Racing.debug("Attempting to remove potion effects on %s", playerSession.getPlayer().getName());
    for(PotionEffect effect : playerSession.getPlayer().getActivePotionEffects()) {
      playerSession.getPlayer().removePotionEffect(effect.getType());
      Racing.debug("Removed potion effect %s on %s", effect.getType(), playerSession.getPlayer().getName());
    }

    // prevent players from jumping during the countdown
    int countdown = Racing.getInstance().getConfiguration().get(ConfigKey.COUNTDOWN);
    Racing.debug("Attempting to add jump potion effect on %s", playerSession.getPlayer().getName());
    playerSession.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, countdown * 20, 128));
    Racing.debug("Potion effect %s added to %s", playerSession.getPlayer().getPotionEffect(PotionEffectType.JUMP), playerSession.getPlayer().getName());

    switch (playerSession.getRaceSession().getRace().getType()) {
      case HORSE:
      case BOAT:
      case PIG:
      case MINECART:
        playerSession.getPlayer().setAllowFlight(true);
        break;
      case ELYTRA:
      case PLAYER:
        playerSession.getPlayer().setAllowFlight(false);
        break;
      default:
    }
  }

  public void restore() {
    Racing.debug("Removing potion effects on %s...", playerSession.getPlayer().getName());
    for(PotionEffect effect : playerSession.getPlayer().getActivePotionEffects()) {
      playerSession.getPlayer().removePotionEffect(effect.getType());
      Racing.debug("Removed potion effect %s on %s", effect.getType(), playerSession.getPlayer().getName());
    }
    playerSession.getPlayer().addPotionEffects(potionEffects);
    Racing.debug("Restored potion effects of %s", playerSession.getPlayer().getName());
    playerSession.getPlayer().setFoodLevel(foodLevel);
    Racing.debug("Restored food level on %s to %d", playerSession.getPlayer().getName(), foodLevel);
    playerSession.getPlayer().setWalkSpeed(walkSpeed);
    Racing.debug("Restored walk speed of %s to %f", playerSession.getPlayer().getName(), walkSpeed);
    playerSession.getPlayer().setLevel(level);
    Racing.debug("Restored level of %s to %d", playerSession.getPlayer().getLevel(), level);
    playerSession.getPlayer().setExp(exp);
    Racing.debug("Restored experience of %s to %f", playerSession.getPlayer().getName(), exp);
    playerSession.getPlayer().setSaturation(saturation);
    Racing.debug("Restored saturation of %s to %f", playerSession.getPlayer().getName(), saturation);
    playerSession.getPlayer().setExhaustion(exhaustion);
    Racing.debug("Restored exhaustion of %s to %f", playerSession.getPlayer().getName(), exhaustion);
    playerSession.getPlayer().setFireTicks(fireTicks);
    Racing.debug("Restored fire ticks of %s to %d", playerSession.getPlayer().getName(), fireTicks);
    playerSession.getPlayer().setAllowFlight(allowFlight);
    Racing.debug("Restoring allowFlight of %s to %b", playerSession.getPlayer().getName(), allowFlight);
    playerSession.getPlayer().setHealth(health);
    Racing.debug("Restored health of %s to %f", playerSession.getPlayer().getName(), health);
    playerSession.getPlayer().setGameMode(gameMode);
    Racing.debug("Restored game mode of %s to %s", playerSession.getPlayer().getName(), gameMode);
    playerSession.getPlayer().getInventory().setContents(inventory);
    Racing.debug("Restored inventory of %s to %s", playerSession.getPlayer().getName(), inventory);
  }
}
