package me.roinujnosde.simpleclans.griefprevention;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.hooks.protection.Coordinate;
import net.sacredlabyrinth.phaed.simpleclans.hooks.protection.Land;
import net.sacredlabyrinth.phaed.simpleclans.hooks.protection.ProtectionProvider;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("unused")
public class GriefPreventionProvider extends JavaPlugin implements ProtectionProvider, Listener {

    @Override
    public void onEnable() {
        SimpleClans sc = (SimpleClans) Bukkit.getPluginManager().getPlugin("SimpleClans");
        if (sc == null) {
            throw new IllegalStateException();
        }
        getLogger().info("Registering provider...");
        sc.getProtectionManager().registerProvider(this);
    }

    @Override
    public void setup() {
        Bukkit.getPluginManager().registerEvents(this, SimpleClans.getInstance());
    }

    @Override
    public @NotNull Set<Land> getLandsAt(@NotNull Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) {
            return Collections.emptySet();
        }
        return Collections.singleton(getLand(claim));
    }

    @Override
    public @NotNull Set<Land> getLandsOf(@NotNull OfflinePlayer player, @NotNull World world) {
        HashSet<Land> lands = new HashSet<>();
        for (Claim claim : GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).getClaims()) {
            if (claim == null) {
                continue;
            }
            lands.add(getLand(claim));
        }
        return lands;
    }

    @Override
    public @NotNull String getIdPrefix() {
        return "gp";
    }

    @Override
    public void deleteLand(@NotNull String id, @NotNull World world) {
        DataStore dataStore = GriefPrevention.instance.dataStore;
        Claim claim = dataStore.getClaim(Long.parseLong(id.replaceFirst(getIdPrefix(), "")));
        if (claim != null) {
            dataStore.deleteClaim(claim);
        }
    }

    @Override
    public @Nullable Class<? extends Event> getCreateLandEvent() {
        try {
            return ClaimCreatedEvent.class;
        } catch (NoClassDefFoundError error) {
            return null;
        }
    }

    @Override
    public @Nullable Player getPlayer(Event event) {
        if (event instanceof ClaimCreatedEvent) {
            if (((ClaimCreatedEvent) event).getCreator() instanceof Player) {
                return ((Player) ((ClaimCreatedEvent) event).getCreator());
            }
        }
        return null;
    }

    @Override
    public @Nullable String getRequiredPluginName() {
        return "GriefPrevention";
    }

    @Nullable
    private Land getLand(@Nullable Claim claim) {
        if (claim == null) {
            return null;
        }
        List<Coordinate> coords = Arrays.asList(new Coordinate(claim.getLesserBoundaryCorner()),
                new Coordinate(claim.getGreaterBoundaryCorner()));

        return new Land(getIdPrefix() + claim.getID().toString(), Collections.singleton(getOwnerID(claim)), coords);
    }

    @SuppressWarnings("deprecation")
    @NotNull
    private UUID getOwnerID(@NotNull Claim claim) {
        try {
            return claim.getOwnerID();
        } catch (NoSuchMethodError error) {
            return Bukkit.getOfflinePlayer(claim.getOwnerName()).getUniqueId();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onResize(ClaimResizeEvent event) {
        ClanManager clanManager = SimpleClans.getInstance().getClanManager();

        Land originalLand = getLand(event.getFrom());
        Land newLand = getLand(event.getTo());
        if (originalLand == null || newLand == null) {
            return;
        }
        for (UUID owner : originalLand.getOwners()) {
            ClanPlayer cp = clanManager.getAnyClanPlayer(owner);
            if (cp == null) continue;
            for (ProtectionManager.Action action : ProtectionManager.Action.values()) {
                if (cp.isAllowed(action, originalLand.getId())) {
                    cp.disallow(action, originalLand.getId());
                    cp.allow(action, newLand.getId());
                }
            }
        }
    }
}