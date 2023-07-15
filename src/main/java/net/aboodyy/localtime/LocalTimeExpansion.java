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

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public class LocalTimeExpansion extends PlaceholderExpansion implements Cacheable, Configurable {

    private DateManager dateManager;

    @Override
    public String getIdentifier() {
        return "localtime";
    }

    @Override
    public String getAuthor() {
        return "aBooDyy, Opal";
    }

    @Override
    public String getVersion() {
        return "1.3";
    }

    @Override
    public boolean register() {
        dateManager = new DateManager();
        Bukkit.getPluginManager().registerEvents(dateManager, PlaceholderAPIPlugin.getInstance());

        return super.register();
    }

    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("date_format", "dd/MM/yyyy hh:mma");

        return defaults;
    }

    public void clear() {
        dateManager.clear();
        HandlerList.unregisterAll(dateManager);
        dateManager.shutdown();
    }

    @Override
    public String onPlaceholderRequest(Player p, String identifier) {
        if (p == null) return null;

        String[] args;
        String format = this.getString("date_format", "dd/MM/yyyy hh:mma");

        if (identifier.startsWith("time_")) {
            args = identifier.split("time_");
            if (args.length < 2) return null;

            CompletableFuture<String> timezoneFuture = dateManager.getTimeZone(p);
            String timezone = timezoneFuture.join();

            return dateManager.getDate(args[1], timezone);
        }

        if (identifier.startsWith("timezone_")) {
            if (identifier.contains(",")) {
                args = identifier.replace("timezone_", "").split(",", 2);
                if (args.length != 2) return null;

                return dateManager.getDate(args[1], args[0]);
            }

            args = identifier.split("timezone_");
            if (args.length < 2) return null;

            return dateManager.getDate(format, args[1]);
        }

        if (identifier.equalsIgnoreCase("time")) {
            CompletableFuture<String> timezoneFuture = dateManager.getTimeZone(p);
            String timezone = timezoneFuture.join();

            return dateManager.getDate(format, timezone);
        }

        return null;
    }
}
