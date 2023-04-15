package net.aboodyy.localtime;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;

public class DateManager implements Listener {

    private final Map<UUID, String> timezones;
    private final Map<String, String> cache;
    private int retryDelay;

    DateManager() {
        this.timezones = new HashMap<>();
        this.cache = new HashMap<>();
        this.retryDelay = retryDelay;
    }

    public String getDate(String format, String timezone) {
        Date date = new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));

        return dateFormat.format(date);
    }

    public String getTimeZone(Player player) {
        final String FAILED = "[LocalTime] Couldn't get " + player.getName() + "'s timezone. Will use default timezone.";
        String timezone = cache.get(player.getUniqueId().toString());

        if (timezone != null) {
            return timezone;
        }

        timezone = timezones.get(player.getUniqueId());
        if (timezone != null) {
            return timezone;
        }

        InetSocketAddress address = player.getAddress();
        timezone = TimeZone.getDefault().getID();

        if (address == null) {
            Bukkit.getLogger().info(FAILED);
            cache.put(player.getUniqueId().toString(), timezone);
            return timezone;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                String timezone;

                try {
                    URL api = new URL("https://ipapi.co/" + address.getAddress().getHostAddress() + "/timezone/");
                    URLConnection connection = api.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    timezone = bufferedReader.readLine();

                    if (timezone == null) {
                        timezone = "undefined";
                    } else {
                        cache.put(player.getUniqueId().toString(), timezone);
                    }
                } catch (Exception e) {
                    timezone = "undefined";
                }

                if (timezone.equalsIgnoreCase("undefined")) {
                    Bukkit.getLogger().info(FAILED);
                    timezone = TimeZone.getDefault().getID();
                    String finalTimezone = timezone;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            timezones.put(player.getUniqueId(), finalTimezone);
                        }
                    }.runTaskLaterAsynchronously(PlaceholderAPIPlugin.getInstance(), retryDelay);
                    return;
                }

                timezones.put(player.getUniqueId(), timezone);
            }
        }.runTaskAsynchronously(PlaceholderAPIPlugin.getInstance());

        return timezone;
    }

    public void clear() {
        timezones.clear();
        cache.clear();
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        timezones.remove(e.getPlayer().getUniqueId());
        cache.remove(e.getPlayer().getUniqueId().toString());
    }
}
