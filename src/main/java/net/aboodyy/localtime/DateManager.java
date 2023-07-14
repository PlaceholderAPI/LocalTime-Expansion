/*

    LocalTime Expansion - Provides PlaceholderAPI placeholders to give player's local time
    Copyright (C) 2020 aBooDyy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */

package net.aboodyy.localtime;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

public class DateManager implements Listener {

    private final Map<UUID, String> timezones;
    private final Cache<String, String> cache;
    private final ScheduledExecutorService executorService;
    private int retryDelay;
    private final int cacheExpirationMinutes = 1440; // Cache entries expire after 1440 minutes (1 Day)

    public DateManager() {
        this.timezones = new ConcurrentHashMap<>();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();
        this.retryDelay = 5; // default to 5 seconds
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public String getDate(String format, String timezone) {
        Date date = new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));

        return dateFormat.format(date);
    }

    public CompletableFuture<String> getTimeZone(Player player) {
        final String FAILED = "[LocalTime] Couldn't get " + player.getName() + "'s timezone. Will use default timezone.";

        String cachedTimezone = cache.getIfPresent(player.getUniqueId().toString());
        if (cachedTimezone != null) {
            return CompletableFuture.completedFuture(cachedTimezone);
        }

        String timezone = timezones.get(player.getUniqueId());
        if (timezone != null) {
            return CompletableFuture.completedFuture(timezone);
        }

        InetSocketAddress address = player.getAddress();
        if (address == null) {
            PlaceholderAPIPlugin.getInstance().getLogger().log(Level.WARNING, FAILED);
            timezone = TimeZone.getDefault().getID();
            cache.put(player.getUniqueId().toString(), timezone);
            return CompletableFuture.completedFuture(timezone);
        }

        final String defaultTimezone = TimeZone.getDefault().getID();

        CompletableFuture<String> futureTimezone = CompletableFuture.supplyAsync(() -> {
            String result = "undefined";
            int retries = 3;

            while (retries-- > 0) {
                try {
                    URL api = new URL("https://ipapi.co/" + address.getAddress().getHostAddress() + "/timezone/");
                    URLConnection connection = api.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        result = bufferedReader.readLine();

                        if (result == null) {
                            result = "undefined";
                        } else {
                            cache.put(player.getUniqueId().toString(), result);
                        }
                        break;
                    }
                } catch (Exception e) {
                    result = "undefined";
                    PlaceholderAPIPlugin.getInstance().getLogger().log(Level.WARNING, "[LocalTime] Exception while getting timezone for player " + player.getName() + ": " + e.getMessage(), e);
                    try {
                        Thread.sleep(retryDelay * 1000);
                    } catch (InterruptedException ignored) {}
                }
            }

            if (result.equalsIgnoreCase("undefined")) {
                PlaceholderAPIPlugin.getInstance().getLogger().log(Level.WARNING, FAILED);
                result = defaultTimezone;
            }

            timezones.put(player.getUniqueId(), result);
            return result;
        }, executorService);

        futureTimezone.exceptionally(ex -> {
            PlaceholderAPIPlugin.getInstance().getLogger().log(Level.WARNING, "[LocalTime] Exception while getting timezone for player " + player.getName() + ": " + ex.getMessage(), ex);
            cache.put(player.getUniqueId().toString(), defaultTimezone);
            timezones.put(player.getUniqueId(), defaultTimezone);
            return defaultTimezone;
        });

        return futureTimezone;
    }

    public void clear() {
        timezones.clear();
        cache.invalidateAll();
    }

    public void shutdown() {
        this.executorService.shutdown();
    }
}