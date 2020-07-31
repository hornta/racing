package com.github.hornta.racing.api.migrations;

import com.github.hornta.racing.api.IRaceFileMigration;
import com.github.hornta.racing.enums.RaceVersion;
import org.bukkit.configuration.file.YamlConfiguration;

public class ElytraSpeedMigrationRace implements IRaceFileMigration {
  @Override
  public RaceVersion from() {
    return RaceVersion.V17;
  }

  @Override
  public RaceVersion to() {
    return RaceVersion.V18;
  }

  @Override
  public void migrate(YamlConfiguration yamlConfiguration) {
    yamlConfiguration.set("elytra_speed", 0.25D);
  }
}
