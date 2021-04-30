package com.github.hornta.racing.objects;

import com.comphenix.protocol.wrappers.WrappedParticle;
import com.github.hornta.racing.enums.Permission;
import com.github.hornta.racing.packetwrapper.WrapperPlayServerWorldParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class CheckpointParticleTask extends BukkitRunnable {
  private static final double ANGLE_INCREMENT = Math.toRadians(4);
  private static final int NUMBER_ORBS = 2;
  private static final double ANGLE_OFFSET = (2 * Math.PI) / NUMBER_ORBS;
  private double t;
  private final RaceCheckpoint checkpoint;
  private final boolean isEditing;
  private final boolean isLast;
  private final RGB defaultColor;
  private final RGB isInsideColor;

  public CheckpointParticleTask(RaceCheckpoint checkpoint, boolean isEditing, boolean isLast) {
    this.checkpoint = checkpoint;
    this.isEditing = isEditing;
    this.isLast = isLast;
    defaultColor = new RGB(255, 0, 0);
    isInsideColor = new RGB(0, 255, 0);
  }

  @Override
  public void run() {
    List<Player> players = checkpoint.getPlayers();

    boolean isInside = false;
    if(isEditing) {
      for(Player player : Bukkit.getOnlinePlayers()) {
        if(checkpoint.isInside(player)) {
          isInside = true;
          break;
        }
      }
    }

    Vector dir = checkpoint.getLocation().getDirection();

    for(int i = 0; i < NUMBER_ORBS; ++i) {
      double offset = i * ANGLE_OFFSET;
      double angle = t + offset;

      // rotate dir by 90 degrees CW on the y-axis
      Vector p = new Vector(-dir.getZ(), dir.getY(), dir.getX());
      Vector c = p.crossProduct(dir);
      Vector v = c.rotateAroundAxis(dir, angle).normalize().multiply(checkpoint.getRadius());

      Location loc = checkpoint.getLocation().add(v);

      WrapperPlayServerWorldParticles particle = new WrapperPlayServerWorldParticles();
      particle.setNumberOfParticles(1);
      particle.setLongDistance(true);
      RGB rgb = null;
      if(!isInside && isLast) {
        rgb = RGB.randomLightColor();
      }
      particle.setParticleType(WrappedParticle.create(Particle.REDSTONE, new Particle.DustOptions(
        Color.fromRGB(
          isInside ? isInsideColor.getR() : isLast ? rgb.getR() : defaultColor.getR(),
          isInside ? isInsideColor.getG() : isLast ? rgb.getG() : defaultColor.getG(),
          isInside ? isInsideColor.getB() : isLast ? rgb.getB() : defaultColor.getB()
        ), 2
      )));
      particle.setX(loc.getX());
      particle.setY(loc.getY());
      particle.setZ(loc.getZ());
      particle.setParticleData(2);

      if(isEditing) {
        for(Player player : Bukkit.getOnlinePlayers()) {
          if(!player.hasPermission(Permission.RACING_MODIFY.toString())) {
            continue;
          }

          if(player.getLocation().getWorld() == checkpoint.getLocation().getWorld()) {
            particle.sendPacket(player);
          }
        }
      } else {
        for (Player player : players) {
          if(player.getLocation().getWorld() == checkpoint.getLocation().getWorld()) {
            particle.sendPacket(player);
          }
        }
      }
    }
    t += ANGLE_INCREMENT;
  }
}
