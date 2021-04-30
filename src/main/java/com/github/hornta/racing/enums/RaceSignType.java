package com.github.hornta.racing.enums;

public enum RaceSignType {
	JOIN,
	FASTEST,
	FASTEST_LAP,
	WINS,
	WIN_RATIO,
	RUNS,
	INFO;

	public static RaceSignType fromString(String string) {
		for (RaceSignType type : values()) {
			if (type.name().compareToIgnoreCase(string) == 0) {
				return type;
			}
		}
		return null;
	}
}
