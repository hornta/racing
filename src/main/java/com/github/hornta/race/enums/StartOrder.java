package com.github.hornta.race.enums;

public enum StartOrder {
  FASTEST,
  FASTEST_LAP,
  WINS,
  SLOWEST,
  SLOWEST_LAP,
  RANDOM;

  public static StartOrder fromString(String string) {
    for(StartOrder type : values()) {
      if(type.name().compareToIgnoreCase(string) == 0) {
        return type;
      }
    }
    return null;
  }
}