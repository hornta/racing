package com.github.hornta.racing.enums;

public enum Permission {
  RACING_ADMIN("racing.admin"),
  RACING_MODIFY("racing.modify"),
  RACING_MODERATOR("racing.moderator"),
  RACING_PLAYER("racing.player"),

  COMMAND_CREATE("racing.command.create"),
  COMMAND_DELETE("racing.command.delete"),
  COMMAND_LIST("racing.command.list"),
  COMMAND_ADD_CHECKPOINT("racing.command.add_checkpoint"),
  COMMAND_DELETE_CHECKPOINT("racing.command.delete_checkpoint"),
  COMMAND_MOVE_CHECKPOINT("racing.command.move_checkpoint"),
  COMMAND_TELEPORT_CHECKPOINT("racing.command.teleport_checkpoint"),
  COMMAND_SPAWN("racing.command.spawn"),
  COMMAND_SET_SPAWN("racing.command.set_spawn"),
  COMMAND_SET_STATE("racing.command.set_state"),
  COMMAND_SET_NAME("racing.command.set_name"),
  COMMAND_SET_TYPE("racing.command.set_type"),
  COMMAND_SET_ENTRY_FEE("racing.command.set_entry_fee"),
  COMMAND_SET_WALK_SPEED("racing.command.set_walk_speed"),
  COMMAND_SET_PIG_SPEED("racing.command.set_pig_speed"),
  COMMAND_SET_ELYTRA_SPEED("racing.command.set_elytra_speed"),
  COMMAND_SET_STRIDER_SPEED("racing.command.set_strider_speed"),
  COMMAND_SET_HORSE_SPEED("racing.command.set_horse_speed"),
  COMMAND_SET_HORSE_JUMP_STRENGTH("racing.command.set_horse_jump_strength"),
  COMMAND_ADD_POTION_EFFECT("racing.command.add_potion_effect"),
  COMMAND_REMOVE_POTION_EFFECT("racing.command.remove_potion_effect"),
  COMMAND_CLEAR_POTION_EFFECTS("racing.command.clear_potion_effects"),
  COMMAND_ADD_STARTPOINT("racing.command.add_startpoint"),
  COMMAND_DELETE_STARTPOINT("racing.command.delete_startpoint"),
  COMMAND_MOVE_STARTPOINT("racing.command.move_startpoint"),
  COMMAND_TELEPORT_STARTPOINT("racing.command.teleport_startpoint"),
  COMMAND_SET_START_ORDER("racing.command.set_start_order"),
  COMMAND_SET_SONG("racing.command.set_song"),
  COMMAND_UNSET_SONG("racing.command.unset_song"),
  COMMAND_PLAY_SONG("racing.command.play_song"),
  COMMAND_STOP_SONG("racing.command.stop_song"),
  COMMAND_START("racing.command.start"),
  COMMAND_START_RANDOM("racing.command.start_random"),
  COMMAND_JOIN("racing.command.join"),
  COMMAND_STOP("racing.command.stop"),
  COMMAND_SKIPWAIT("racing.command.skipwait"),
  COMMAND_LEAVE("racing.command.leave"),
  COMMAND_RELOAD("racing.command.reload"),
  COMMAND_HELP("racing.command.help"),
  COMMAND_INFO("racing.command.info"),
  COMMAND_TOP("racing.command.top"),
  COMMAND_RESET_TOP("racing.command.reset_top"),
  COMMAND_CREATE_HD_TOP_LIST("racing.command.createhdtoplist"),
  COMMAND_DELETE_HD_TOP_LIST("racing.command.deletehdtoplist"),
  COMMAND_MOVE_HD_TOP_LIST("racing.command.movehdtoplist"),
  COMMAND_SET_RACE_HD_TOP_LIST("racing.command.setracehdtoplist"),
  COMMAND_SET_STAT_HD_TOP_LIST("racing.command.setstathdtoplist"),
  COMMAND_SET_LAPS_HD_TOP_LIST("racing.command.setlapshdtoplist"),
  COMMAND_LIST_HD_TOP_LIST("racing.command.listhdtoplist"),
  COMMAND_TELEPORT_HD_TOP_LIST("racing.command.teleporthdtoplist");

  private final String node;

  Permission(String node) {
    this.node = node;
  }

  @Override
  public String toString() {
    return node;
  }
}
