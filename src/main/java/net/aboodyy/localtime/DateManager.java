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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

    public DateManager() {
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

        String finalTimezone = timezone;
        new Thread(() -> {
            String timeZone;

            try {
                URL api = new URL("https://ipapi.co/" + address.getAddress().getHostAddress() + "/timezone/");
                URLConnection connection = api.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                timeZone = bufferedReader.readLine();

                if (timeZone == null) {
                    timeZone = "undefined";
                } else {
                    cache.put(player.getUniqueId().toString(), timeZone);
                }
            } catch (Exception e) {
                timeZone = "undefined";
            }

            if (timeZone.equalsIgnoreCase("undefined")) {
                Bukkit.getLogger().info(FAILED);
                timeZone = finalTimezone;
            }

            timezones.put(player.getUniqueId(), timeZone);
        }).start();

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
