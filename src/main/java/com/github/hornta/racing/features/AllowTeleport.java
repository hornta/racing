package com.github.hornta.racing.features;

import com.github.hornta.racing.ConfigKey;
import com.github.hornta.racing.RacingPlugin;
import com.github.hornta.racing.objects.RaceSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public class AllowTeleport implements Listener {
  @EventHandler
  void onPlayerTeleport(PlayerTeleportEvent event) {
    RaceSession raceSession = RacingPlugin.getInstance().getRacingManager().getParticipatingRace(event.getPlayer());
    boolean allowPearlTeleport = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.ALLOW_ENDER_PEARL_TP);
    boolean allowChorusTeleport = RacingPlugin.getInstance().getConfiguration().get(ConfigKey.ALLOW_CHORUS_FRUIT_TP);

    if(
      (
        !allowChorusTeleport && event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
        !allowPearlTeleport && event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
      ) &&
      raceSession != null &&
      raceSession.isCurrentlyRacing(event.getPlayer())
    ) {
      event.setCancelled(true);
    }
  }
}
