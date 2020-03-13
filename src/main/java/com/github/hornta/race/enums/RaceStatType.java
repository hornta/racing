package com.github.hornta.race.enums;

import com.github.hornta.race.MessageKey;

public enum RaceStatType {
  FASTEST(MessageKey.RACE_TOP_TYPE_FASTEST),
  FASTEST_LAP(MessageKey.RACE_TOP_TYPE_FASTEST_LAP),
  WINS(MessageKey.RACE_TOP_TYPE_MOST_WINS),
  RUNS(MessageKey.RACE_TOP_TYPE_MOST_RUNS),
  WIN_RATIO(MessageKey.RACE_TOP_TYPE_WIN_RATIO);

  private MessageKey key;

  RaceStatType(MessageKey key) {
    this.key = key;
  }

  public MessageKey getKey() {
    return key;
  }
}
