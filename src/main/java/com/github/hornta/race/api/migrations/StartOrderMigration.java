package com.github.hornta.race.api.migrations;

import com.github.hornta.race.api.FileAPI;
import com.github.hornta.race.api.IFileMigration;
import com.github.hornta.race.enums.RaceVersion;
import com.github.hornta.race.enums.StartOrder;

import org.bukkit.configuration.file.YamlConfiguration;

public class StartOrderMigration implements IFileMigration {
  @Override
  public RaceVersion from() {
    return RaceVersion.V12;
  }

  @Override
  public RaceVersion to() {
    return RaceVersion.V13;
  }

  @Override
  public void migrate(YamlConfiguration yamlConfiguration) {
    yamlConfiguration.set(FileAPI.START_ORDER_FIELD, StartOrder.RANDOM.name());
  }
}
