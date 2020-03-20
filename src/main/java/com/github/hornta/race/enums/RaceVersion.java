package com.github.hornta.race.enums;

import java.util.EnumSet;

public enum RaceVersion {
  V1,
  V2,
  V3,
  V4,
  V5,
  V6,
  V7,
  V8,
  V9,
  V10,
  V11,
  V12;

  private static final RaceVersion[] copyOfValues = values();

  public static RaceVersion fromString(String name) {
    for (RaceVersion value : copyOfValues) {
      if (value.name().equals(name)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Couldn't translate " + name + " into a RaceVersion");
  }

  static {
    int counter = 0;
    for(RaceVersion v : EnumSet.allOf(RaceVersion.class)) {
      v.setOrder(counter);
      counter += 1;
    }
  }

  public static RaceVersion getLast() {
    return copyOfValues[copyOfValues.length - 1];
  }

  private int order;

  public void setOrder(int order) {
    this.order = order;
  }

  public int getOrder() {
    return order;
  }

  public boolean isGreater(RaceVersion v) {
    return order > v.getOrder();
  }
}
