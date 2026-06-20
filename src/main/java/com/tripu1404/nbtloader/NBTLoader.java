package com.tripu1404.nbtloader;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.util.List;
import java.util.Map;

public class NBTLoader extends PluginBase {

    @Override
    public void onEnable() {
        File nbtFolder = new File(getDataFolder(), "nbts");
        if (!nbtFolder.exists()) {
            nbtFolder.mkdirs();
            saveResource("nbts/mommys_affection.txt", false);
            saveResource("nbts/Techno kit.txt", false);
        }
        getLogger().info(TextFormat.GREEN + "NBTLoader optimizado para Kits Avanzados activado. Hecho por Tripu1404.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("givekit")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("nbtloader.command.givekit")) {
            player.sendMessage(TextFormat.RED + "No tienes permisos para usar este comando.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(TextFormat.YELLOW + "Uso correcto: /givekit <nombre_del_archivo>");
            return true;
        }

        String fileName = args[0];
        if (!fileName.endsWith(".json") && !fileName.endsWith(".txt")) {
            fileName += ".txt"; 
        }

        File nbtFile = new File(new File(getDataFolder(), "nbts"), fileName);

        if (!nbtFile.exists()) {
            String alternativeName = fileName.endsWith(".txt") ? fileName.replace(".txt", ".json") : fileName.replace(".json", ".txt");
            nbtFile = new File(new File(getDataFolder(), "nbts"), alternativeName);
        }

        if (!nbtFile.exists()) {
            player.sendMessage(TextFormat.RED + "El archivo '" + args[0] + "' no existe en plugins/NBTLoader/nbts/");
            return true;
        }

        try {
            // 1. CARGA SEGURA VÍA CONFIG (Universal para cualquier JSON/SNBT)
            Config jsonConfig = new Config(nbtFile, Config.JSON);
            Map<String, Object> rootMap = jsonConfig.getAll();

            if (rootMap == null || rootMap.isEmpty()) {
                player.sendMessage(TextFormat.RED + "Error: El archivo NBT está vacío o no tiene un formato JSON válido.");
                return true;
            }

            // Convertimos el mapa de configuración a un CompoundTag nativo de Nukkit
            CompoundTag rootTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : rootMap.entrySet()) {
                NBTIO.putObjectProperty(rootTag, entry.getKey(), entry.getValue());
            }

            // 2. DETERMINAR EL ID DE LA CAJA SHULKER DINÁMICAMENTE
            String boxId = "minecraft:shulker_box"; 

            if (rootTag.contains("Name")) {
                boxId = rootTag.getString("Name");
            } else if (rootTag.contains("Block") && rootTag.getCompound("Block").contains("name")) {
                boxId = rootTag.getCompound("Block").getString("name");
            } else if (rootTag.contains("states") && rootTag.getCompound("states").contains("color")) {
                String color = rootTag.getCompound("states").getString("color");
                boxId = "minecraft:" + color + "_shulker_box";
            }

            // Crear el ítem base en el servidor
            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            // 3. EXTRACCIÓN INTELIGENTE DE LA LISTA DE ÍTEMS INTERNOS
            CompoundTag finalItemTag = new CompoundTag();
            ListTag<CompoundTag> itemsList = null;

            if (rootTag.contains("Items")) {
                itemsList = rootTag.getList("Items", CompoundTag.class);
            } else if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("Items")) {
                itemsList = rootTag.getCompound("tag").getList("Items", CompoundTag.class);
            }

            if (itemsList != null) {
                finalItemTag.putList("Items", itemsList);
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' dentro del archivo.");
                return true;
            }

            // 4. PRESERVAR METADATOS COSMÉTICOS (Nombres de Kits, Lores, Encantamientos directos)
            CompoundTag displayTag = null;
            if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("display")) {
                displayTag = rootTag.getCompound("tag").getCompound("display");
            } else if (rootTag.contains("display")) {
                displayTag = rootTag.getCompound("display");
            }

            if (displayTag != null) {
                finalItemTag.putCompound("display", displayTag);
            }

            // Mantener colores y costos de reparación si existen
            if (rootTag.contains("tag")) {
                CompoundTag originalTag = rootTag.getCompound("tag");
                if (originalTag.contains("customColor")) finalItemTag.putInt("customColor", originalTag.getInt("customColor"));
                if (originalTag.contains("RepairCost")) finalItemTag.putInt("RepairCost", originalTag.getInt("RepairCost"));
            }

            // Inyectar etiquetas procesadas al ítem final
            itemToGive.setNamedTag(finalItemTag);

            // 5. ENTREGA AL JUGADOR
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' cargado y procesado correctamente.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente en tu inventario.");
            }

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico al procesar este Kit. Revisa la consola.");
            getServer().getLogger().logException(e);
        }

        return true;
    }
}
