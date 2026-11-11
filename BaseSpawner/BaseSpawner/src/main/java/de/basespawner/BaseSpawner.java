package de.basespawner;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BaseSpawner extends JavaPlugin implements Listener {

    private final List<Clipboard> schematics = new ArrayList<>();
    private final Set<String> spawnedCells = new HashSet<>();
    private File dataFile;

    private List<String> worlds;
    private int cellSize;
    private double spawnChance;
    private int minY;
    private int maxY;
    private int minDistanceFromSpawn;
    private boolean ignoreAir;
    private boolean logSpawns;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null
                && Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().severe("WorldEdit oder FAWE fehlt! Plugin wird deaktiviert.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        File schemFolder = new File(getDataFolder(), "schematics");
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
        }
        dataFile = new File(getDataFolder(), "data.yml");

        loadAll();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("BaseSpawner aktiv. " + schematics.size() + " Schematic(s) geladen.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadAll() {
        reloadConfig();
        worlds = getConfig().getStringList("worlds");
        cellSize = Math.max(32, getConfig().getInt("cell-size", 300));
        spawnChance = getConfig().getDouble("spawn-chance", 0.6);
        minY = getConfig().getInt("min-y", -55);
        maxY = getConfig().getInt("max-y", -15);
        if (maxY < minY) {
            int t = minY;
            minY = maxY;
            maxY = t;
        }
        minDistanceFromSpawn = getConfig().getInt("min-distance-from-spawn", 150);
        ignoreAir = getConfig().getBoolean("ignore-air", true);
        logSpawns = getConfig().getBoolean("log-spawns", true);

        // Schematics laden
        schematics.clear();
        File folder = new File(getDataFolder(), "schematics");
        File[] files = folder.listFiles((dir, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".schem") || n.endsWith(".schematic");
        });
        if (files != null) {
            for (File f : files) {
                ClipboardFormat format = ClipboardFormats.findByFile(f);
                if (format == null) {
                    getLogger().warning("Unbekanntes Format: " + f.getName());
                    continue;
                }
                try (FileInputStream in = new FileInputStream(f);
                     ClipboardReader reader = format.getReader(in)) {
                    schematics.add(reader.read());
                    getLogger().info("Geladen: " + f.getName());
                } catch (Exception e) {
                    getLogger().warning("Konnte " + f.getName() + " nicht laden: " + e.getMessage());
                }
            }
        }

        // Bereits gespawnte Zellen laden
        spawnedCells.clear();
        if (dataFile.exists()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
            spawnedCells.addAll(data.getStringList("spawned"));
        }
    }

    private void saveData() {
        if (dataFile == null) return;
        YamlConfiguration data = new YamlConfiguration();
        data.set("spawned", new ArrayList<>(spawnedCells));
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Konnte data.yml nicht speichern: " + e.getMessage());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (schematics.isEmpty() || !worlds.contains(world.getName())) return;
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        Chunk chunk = event.getChunk();
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;

        // In welcher Zelle liegt dieser Chunk?
        int cellX = Math.floorDiv(minX, cellSize);
        int cellZ = Math.floorDiv(minZ, cellSize);
        String key = world.getName() + ":" + cellX + ":" + cellZ;
        if (spawnedCells.contains(key)) return;

        // Deterministische Position der Base in dieser Zelle (abhaengig vom Welt-Seed)
        Random rnd = new Random(world.getSeed() * 341873128712L + cellX * 132897987541L + cellZ * 42317861L);
        if (rnd.nextDouble() > spawnChance) {
            return; // in dieser Zelle ist keine Base
        }
        int baseX = cellX * cellSize + rnd.nextInt(cellSize);
        int baseZ = cellZ * cellSize + rnd.nextInt(cellSize);
        int baseY = minY + rnd.nextInt(maxY - minY + 1);
        int index = rnd.nextInt(schematics.size());

        // Nur spawnen wenn genau dieser Chunk die Zielposition enthaelt
        if (baseX < minX || baseX >= minX + 16 || baseZ < minZ || baseZ >= minZ + 16) return;

        double dx = baseX - world.getSpawnLocation().getX();
        double dz = baseZ - world.getSpawnLocation().getZ();
        if (Math.sqrt(dx * dx + dz * dz) < minDistanceFromSpawn) return;

        spawnedCells.add(key);
        // Kurz warten, damit der Chunk fertig geladen ist
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (paste(world, schematics.get(index), baseX, baseY, baseZ)) {
                saveData();
                if (logSpawns) {
                    getLogger().info("Base gespawnt bei " + baseX + " " + baseY + " " + baseZ + " (" + world.getName() + ")");
                }
            } else {
                spawnedCells.remove(key);
            }
        }, 20L);
    }

    private boolean paste(World world, Clipboard clipboard, int x, int y, int z) {
        try {
            BlockVector3 min = clipboard.getMinimumPoint();
            BlockVector3 dim = clipboard.getDimensions();
            BlockVector3 center = min.add(dim.divide(2));
            // Mitte der Schematic soll auf der Zielposition liegen
            BlockVector3 to = BlockVector3.at(x, y, z).add(clipboard.getOrigin().subtract(center));

            try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(session)
                        .to(to)
                        .ignoreAirBlocks(ignoreAir)
                        .build();
                Operations.complete(op);
            }
            return true;
        } catch (Exception e) {
            getLogger().warning("Paste fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/basespawner reload | test | reset");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadAll();
                sender.sendMessage("BaseSpawner neu geladen. " + schematics.size() + " Schematic(s).");
            }
            case "test" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Nur ingame.");
                    return true;
                }
                if (schematics.isEmpty()) {
                    sender.sendMessage("Keine Schematics geladen.");
                    return true;
                }
                Clipboard c = schematics.get(new Random().nextInt(schematics.size()));
                boolean ok = paste(p.getWorld(), c, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                sender.sendMessage(ok ? "Test-Base gespawnt." : "Fehler, siehe Konsole.");
            }
            case "reset" -> {
                spawnedCells.clear();
                saveData();
                sender.sendMessage("Gemerkte Zellen geloescht. Bereits generierte Chunks bekommen keine neuen Basen, nur neu geladene.");
            }
            default -> sender.sendMessage("/basespawner reload | test | reset");
        }
        return true;
    }
}
