package com.github.hornta.racing;

import com.github.hornta.racing.commands.CommandInfo;
import com.github.hornta.racing.commands.CommandTop;
import com.github.hornta.racing.enums.JoinType;
import com.github.hornta.racing.enums.Permission;
import com.github.hornta.racing.enums.RaceSessionState;
import com.github.hornta.racing.enums.RaceSignType;
import com.github.hornta.racing.enums.RaceStatType;
import com.github.hornta.racing.events.AddRaceStartPointEvent;
import com.github.hornta.racing.events.DeleteRaceStartPointEvent;
import com.github.hornta.racing.events.LeaveEvent;
import com.github.hornta.racing.events.LoadRaceEvent;
import com.github.hornta.racing.events.ParticipateEvent;
import com.github.hornta.racing.events.RaceChangeNameEvent;
import com.github.hornta.racing.events.RaceSessionStopEvent;
import com.github.hornta.racing.events.SessionStateChangedEvent;
import com.github.hornta.racing.events.UnloadRaceEvent;
import com.github.hornta.racing.objects.Race;
import com.github.hornta.racing.objects.RaceCheckpoint;
import com.github.hornta.racing.objects.RaceSession;
import com.github.hornta.racing.objects.RaceSign;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import se.hornta.messenger.MessageManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

class SignManager implements Listener {
	private static final Set<Material> wallSignMaterials;
	private static final Set<Material> signPostMaterial;

	static {
		wallSignMaterials = new HashSet<>();
		wallSignMaterials.add(Material.SPRUCE_WALL_SIGN);
		wallSignMaterials.add(Material.ACACIA_WALL_SIGN);
		wallSignMaterials.add(Material.BIRCH_WALL_SIGN);
		wallSignMaterials.add(Material.DARK_OAK_WALL_SIGN);
		wallSignMaterials.add(Material.JUNGLE_WALL_SIGN);
		wallSignMaterials.add(Material.OAK_WALL_SIGN);
		wallSignMaterials.add(Material.LEGACY_WALL_SIGN);
		wallSignMaterials.add(Material.CRIMSON_WALL_SIGN);
		wallSignMaterials.add(Material.WARPED_WALL_SIGN);
		signPostMaterial = new HashSet<>();
		signPostMaterial.add(Material.SPRUCE_SIGN);
		signPostMaterial.add(Material.ACACIA_SIGN);
		signPostMaterial.add(Material.BIRCH_SIGN);
		signPostMaterial.add(Material.DARK_OAK_SIGN);
		signPostMaterial.add(Material.JUNGLE_SIGN);
		signPostMaterial.add(Material.OAK_SIGN);
		signPostMaterial.add(Material.LEGACY_SIGN);
		signPostMaterial.add(Material.CRIMSON_SIGN);
		signPostMaterial.add(Material.WARPED_SIGN);
	}

	private final RacingManager racingManager;
	private final HashMap<RaceSign, Race> racesBySign = new HashMap<>();
	private final HashMap<String, RaceSign> raceSigns = new HashMap<>();

	SignManager(RacingManager racingManager) {
		this.racingManager = racingManager;
	}

	@EventHandler
	void onLoadRace(LoadRaceEvent event) {
		for (RaceSign sign : event.getRace().getSigns()) {
			raceSigns.put(sign.getKey(), sign);
			racesBySign.put(sign, event.getRace());
		}
	}

	@EventHandler
	void onUnloadRace(UnloadRaceEvent event) {
		for (RaceSign sign : event.getRace().getSigns()) {
			raceSigns.remove(sign.getKey());
			racesBySign.remove(sign);
		}
	}

	@EventHandler
	void onRaceChangeName(RaceChangeNameEvent event) {
		event.getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onParticipate(ParticipateEvent event) {
		event.getRaceSession().getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onSessionStateChanged(SessionStateChangedEvent event) {
		event.getRaceSession().getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onRaceSessionStop(RaceSessionStopEvent event) {
		event.getRaceSession().getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onAddRaceStartPoint(AddRaceStartPointEvent event) {
		event.getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onDeleteRaceStartPoint(DeleteRaceStartPointEvent event) {
		event.getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onLeave(LeaveEvent event) {
		event.getRaceSession().getRace().getSigns().forEach(this::updateSign);
	}

	@EventHandler
	void onSignChange(SignChangeEvent event) {
		Race race = racingManager.getRace(event.getLine(1));
		if (race == null) {
			return;
		}
		boolean creatingRaceSign = "race".equalsIgnoreCase(event.getLine(0)) && event.getPlayer().hasPermission(Permission.RACING_MODIFY.toString());
		RaceSignType type = RaceSignType.fromString(event.getLine(2).toUpperCase().replace(" ", "_"));
		if (!creatingRaceSign || type == null) {
			return;
		}
		int laps = 1;
		try {
			laps = (event.getLine(3).isEmpty() ? 1 : Integer.parseInt(event.getLine(3)));
		} catch (NumberFormatException e) {
			//leave laps = 1, no harm if no number entered
		}
		addSign(race, (Sign) event.getBlock().getState(), event.getPlayer(), Instant.now(), laps, type);
	}

	private void addSign(Race race, Sign sign, Player player, Instant createdAt, int laps, RaceSignType type) {
		RaceSign raceSign = new RaceSign(sign, player.getUniqueId(), createdAt, laps, type);
		race.getSigns().add(raceSign);
		racingManager.updateRace(race, () -> {
			raceSigns.put(raceSign.getKey(), raceSign);
			racesBySign.put(raceSign, race);
			updateSign(raceSign);
			MessageManager.setValue("race_name", race.getName());
			MessageManager.sendMessage(player, MessageKey.SIGN_REGISTERED);
		});
	}

	@EventHandler
	void onBlockPhysics(BlockPhysicsEvent event) {
		boolean isSign = signPostMaterial.contains(event.getBlock().getType());
		boolean isWallSign = wallSignMaterials.contains(event.getBlock().getType());
		if (!isSign && !isWallSign) {
			return;
		}
		WallSign wallSign;
		Block parent;
		if (isSign) {
			parent = event.getBlock().getRelative(BlockFace.DOWN);
		} else {
			wallSign = (WallSign) event.getBlock().getBlockData();
			parent = event.getBlock().getRelative(wallSign.getFacing().getOppositeFace());
		}
		if (parent.getType() == Material.AIR) {
			removeSign(event, null);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	void onBlockBreak(BlockBreakEvent event) {
		if (event.isCancelled()) {
			return;
		}
		removeSign(event, event.getPlayer());
	}

	private void removeSign(BlockEvent event, Player player) {
		RaceSign sign = getRaceSignFromBlock(event.getBlock());
		if (sign == null) {
			return;
		}
		Race race = racesBySign.get(sign);
		if (race == null) {
			return;
		}
		race.getSigns().remove(sign);
		racingManager.updateRace(racesBySign.get(sign), () -> {
			raceSigns.remove(sign.getKey());
			racesBySign.remove(sign);
			MessageManager.setValue("race_name", race.getName());
			if (player != null) {
				MessageManager.sendMessage(player, MessageKey.SIGN_UNREGISTERED);
			} else {
				RacingPlugin.logger().log(Level.INFO, MessageManager.getMessage(MessageKey.SIGN_UNREGISTERED));
			}
		});
	}

	@EventHandler
	void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		RaceSign sign = getRaceSignFromBlock(event.getClickedBlock());
		if (sign == null) {
			return;
		}
		if (sign.getSignType() == RaceSignType.JOIN) {
			RaceSession currentSession = racingManager.getParticipatingRace(event.getPlayer());
			if (currentSession == null) {
				racingManager.joinRace(racesBySign.get(sign), event.getPlayer(), JoinType.SIGN, sign.getLaps());
			} else {
				currentSession.leave(event.getPlayer());
			}
		} else if (sign.getSignType() == RaceSignType.INFO) {
			CommandInfo.sendInfoMessage(event.getPlayer(), racesBySign.get(sign));
		} else {
			Race race = racesBySign.get(sign);
			if (race != null) {
				int laps = sign.getLaps();
				RaceStatType statType;
				switch (sign.getSignType()) {
					case WINS:
						statType = RaceStatType.WINS;
						break;
					case WIN_RATIO:
						statType = RaceStatType.WIN_RATIO;
						break;
					case FASTEST:
						statType = RaceStatType.FASTEST;
						break;
					case FASTEST_LAP:
						statType = RaceStatType.FASTEST_LAP;
						break;
					case RUNS:
						statType = RaceStatType.RUNS;
						break;
					case JOIN://fall through
					case INFO://fall through
					default://none of these should ever hit.
						throw new IllegalArgumentException("Unexpected case: " + sign.getSignType().name());
				}
				CommandTop.sendTopMessage(event.getPlayer(), race, laps, statType);
			}
		}
	}

	private RaceSign getRaceSignFromBlock(Block block) {
		if (!wallSignMaterials.contains(block.getType()) && !signPostMaterial.contains(block.getType())) {
			return null;
		}
		return raceSigns.get(RaceSign.createKey(block));
	}

	private void updateSign(RaceSign sign) {
		Race race = racesBySign.get(sign);
		MessageKey messageKey;
		switch (sign.getSignType()) {
			case JOIN:
				List<RaceSession> sessions = racingManager.getRaceSessions(race);
				RaceSession session = sessions.isEmpty() ? null : sessions.get(0);
				MessageKey statusKey;
				int laps = sign.getLaps();
				if (session == null) {
					statusKey = MessageKey.SIGN_NOT_STARTED;
				} else if (session.getState() == RaceSessionState.PREPARING) {
					statusKey = MessageKey.SIGN_LOBBY;
					laps = session.getLaps();
				} else {
					statusKey = MessageKey.SIGN_STARTED;
					laps = session.getLaps();
				}
				MessageManager.setValue("status", MessageManager.getMessage(statusKey));
				MessageManager.setValue("laps", laps);
				MessageManager.setValue("current_participants", session == null ? 0 : session.getAmountOfParticipants());
				MessageManager.setValue("max_participants", race.getStartPoints().size());
				messageKey = MessageKey.RACE_SIGN_LINES;
				break;
			case FASTEST:
				MessageManager.setValue("laps", sign.getLaps());
				messageKey = MessageKey.RACE_SIGN_FASTEST_LINES;
				break;
			case FASTEST_LAP: //intentional fallthrough from here down
			case INFO:
			case RUNS:
			case WINS:
			case WIN_RATIO:
			default:
				MessageManager.setValue("stat_name", sign.getSignType().name().replace("_", " "));
				messageKey = MessageKey.RACE_SIGN_STATS_LINES;
				break;
		}
		MessageManager.setValue("race_name", race.getName());
		String[] contents = MessageManager.getMessage(messageKey).split("\n");
		setSignLines(sign.getSign(), contents);
	}

	private void setSignLines(Sign sign, String[] lines) {
		for (int i = 0; i < lines.length; ++i) {
			sign.setLine(i, lines[i]);
		}
		sign.update();
	}
}
