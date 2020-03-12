package com.github.hornta.race;

import com.github.hornta.race.events.ConfigReloadedEvent;
import com.github.hornta.race.events.RaceSessionStartEvent;
import com.github.hornta.carbon.message.MessageManager;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.hooks.EventListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import javax.security.auth.login.LoginException;
import java.util.Comparator;
import java.util.logging.Level;

public class DiscordManager implements Listener, EventListener {
  private JDA api;
  private TextChannel announceChannel;
  private boolean startAfterShutdown = false;

  DiscordManager() {
    if(Racing.getInstance().getConfiguration().<Boolean>get(ConfigKey.DISCORD_ENABLED)) {
      startup();
    }
  }

  private void startup() {
    startAfterShutdown = false;
    try {
      api = new JDABuilder(AccountType.BOT)
        .setToken(Racing.getInstance().getConfiguration().get(ConfigKey.DISCORD_TOKEN))
        .addEventListener(this)
        .build();
    } catch (LoginException e) {
      Racing.logger().log(Level.SEVERE, "Failed to integrate with Discord, the bot token was incorrect.");
    }
  }

  @EventHandler
  void onConfigReloaded(ConfigReloadedEvent event) {
    if(api != null) {
      api.shutdown();
    }

    if(Racing.getInstance().getConfiguration().<Boolean>get(ConfigKey.DISCORD_ENABLED)) {
      startAfterShutdown = true;
    }
  }

  @EventHandler
  void onRaceSessionStart(RaceSessionStartEvent event) {
    if(!Racing.getInstance().getConfiguration().<Boolean>get(ConfigKey.DISCORD_ENABLED)) {
      return;
    }

    MessageKey key;
    if (event.getRaceSession().getRace().getEntryFee() > 0) {
      key = MessageKey.PARTICIPATE_DISCORD_FEE;
    } else {
      key = MessageKey.PARTICIPATE_DISCORD;
    }

    int prepareTime = Racing.getInstance().getConfiguration().get(ConfigKey.RACE_PREPARE_TIME);

    Util.setTimeUnitValues();

    MessageManager.setValue("race_name", event.getRaceSession().getRace().getName());
    MessageManager.setValue("time_left", Util.getTimeLeft(prepareTime * 1000));
    MessageManager.setValue("laps", event.getRaceSession().getLaps());

    Economy economy = Racing.getInstance().getEconomy();
    if(economy != null) {
      MessageManager.setValue("entry_fee", economy.format(event.getRaceSession().getRace().getEntryFee()));
    }

    announceChannel.sendMessage(MessageManager.getMessage(key)).queue();
  }

  @Override
  public void onEvent(Event event) {
    if (event instanceof ShutdownEvent) {
      if(startAfterShutdown) {
        startup();
      }
    } else if (event instanceof ReadyEvent) {
      Racing.logger().log(Level.INFO, "Successful integration with Discord.");
      String channelId = Racing.getInstance().getConfiguration().get(ConfigKey.DISCORD_ANNOUNCE_CHANNEL);
      if(channelId.isEmpty()) {
        announceChannel = api
          .getTextChannels()
          .stream()
          .min(Comparator.comparingInt(TextChannel::getPosition))
          .orElse(null);
      } else {
        announceChannel = api.getTextChannelById(channelId);
      }

      if (announceChannel == null) {
        Racing.logger().log(Level.SEVERE, "Couldn't find Discord channel with id: " + channelId);
      } else {
        Racing.logger().log(Level.INFO, "Found Discord channel " + announceChannel.getName() + " with id: " + announceChannel.getId());
      }
    }
  }
}
