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

import org.bukkit.Bukkit;
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

public class DateManager implements Listener {

    private final Map<UUID, String> timezones;
    private final Map<String, String> cache;
    private final ScheduledExecutorService executorService;
    private int retryDelay;
    private final int cacheExpirationMinutes = 1440; // Cache entries expire after 1440 minutes (1 Day)

    public DateManager() {
        this.timezones = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
        this.retryDelay = 5; // default to 5 seconds
        this.executorService = Executors.newSingleThreadScheduledExecutor();

        executorService.scheduleAtFixedRate(this::removeExpiredEntries, cacheExpirationMinutes, cacheExpirationMinutes, TimeUnit.MINUTES);
    }

    public String getDate(String format, String timezone) {
        Date date = new Date();

        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));

        return dateFormat.format(date);
    }

    private boolean isCacheExpired(UUID uuid) {
        String timestampStr = cache.get(uuid.toString() + "_timestamp");
        if (timestampStr == null) {
            return true;
        }

        long timestamp = Long.parseLong(timestampStr);
        long currentTime = System.currentTimeMillis();
        long expirationTime = cacheExpirationMinutes * 60 * 1000;

        if ((currentTime - timestamp) >= expirationTime) {
            timezones.remove(uuid); // Remove the player from the timezones map when cache expires
            return true;
        } else {
            return false;
        }
    }

    private void removeExpiredEntries() {
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry.getKey().endsWith("_timestamp")) {
                continue; // Skip keys with the "_timestamp" suffix
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue; // Skip non-UUID keys
            }

            if (isCacheExpired(uuid)) {
                cache.remove(uuid.toString());
                timezones.remove(uuid);
            }
        }
    }

    public CompletableFuture<String> getTimeZone(Player player) {
        final String FAILED = "[LocalTime] Couldn't get " + player.getName() + "'s timezone. Will use default timezone.";

        String cachedTimezone = cache.get(player.getUniqueId().toString());
        if (cachedTimezone != null) {
            return CompletableFuture.completedFuture(cachedTimezone);
        }

        String timezone = timezones.get(player.getUniqueId());
        if (timezone != null) {
            return CompletableFuture.completedFuture(timezone);
        }

        InetSocketAddress address = player.getAddress();
        timezone = TimeZone.getDefault().getID();

        if (address == null) {
            Bukkit.getLogger().info(FAILED);
            cache.put(player.getUniqueId().toString(), timezone);
            return CompletableFuture.completedFuture(timezone);
        }

        final String timezoneFinal = timezone;
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

                    // Use try-with-resources to automatically close the BufferedReader
                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        result = bufferedReader.readLine();

                        if (result == null) {
                            result = "undefined";
                        } else {
                            cache.put(player.getUniqueId().toString(), result);
                            cache.put(player.getUniqueId().toString() + "_timestamp", String.valueOf(System.currentTimeMillis()));
                        }
                        break; // exit loop if successful
                    }
                } catch (Exception e) {
                    result = "undefined";
                    Bukkit.getLogger().warning("[LocalTime] Exception while getting timezone for player " + player.getName() + ": " + e.getMessage());
                    try {
                        Thread.sleep(retryDelay * 1000);
                    } catch (InterruptedException ignored) {}
                }
            }

            if (result.equalsIgnoreCase("undefined")) {
                Bukkit.getLogger().info(FAILED);
                result = timezoneFinal;
            }

            timezones.put(player.getUniqueId(), result);
            return result;
        }, executorService);


        futureTimezone.exceptionally(ex -> {
            Bukkit.getLogger().warning("[LocalTime] Exception while getting timezone for player " + player.getName() + ": " + ex.getMessage());
            cache.put(player.getUniqueId().toString(), timezoneFinal);
            timezones.put(player.getUniqueId(), timezoneFinal);
            return timezoneFinal;
        });

        return CompletableFuture.completedFuture(timezoneFinal);
    }

    public void clear() {
        timezones.clear();
        cache.clear();
    }
}


