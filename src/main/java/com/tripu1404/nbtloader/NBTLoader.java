package com.tripu1404.nbtloader;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class NBTLoader extends PluginBase {

    @Override
    public void onEnable() {
        // Asegurar la creación de las carpetas de datos
        File nbtFolder = new File(getDataFolder(), "nbts");
        if (!nbtFolder.exists()) {
            nbtFolder.mkdirs();
            
            // Registros iniciales por defecto (opcional)
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
        // Soportar tanto si escriben con extensión como si no
        if (!fileName.endsWith(".json") && !fileName.endsWith(".txt")) {
            fileName += ".txt"; 
        }

        File nbtFile = new File(new File(getDataFolder(), "nbts"), fileName);

        // Intento de fallback si no encuentra el archivo con .txt, busca .json
        if (!nbtFile.exists()) {
            String alternativeName = fileName.endsWith(".txt") ? fileName.replace(".txt", ".json") : fileName.replace(".json", ".txt");
            nbtFile = new File(new File(getDataFolder(), "nbts"), alternativeName);
        }

        if (!nbtFile.exists()) {
            player.sendMessage(TextFormat.RED + "El archivo '" + args[0] + "' no existe en la carpeta plugins/NBTLoader/nbts/");
            return true;
        }

        try {
            // 1. Leer todo el contenido del archivo de texto y limpiar espacios
            String snbtContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8).trim();

            // Corrección de sintaxis rápida por si hay comas duplicadas
            if (snbtContent.contains(",,")) {
                snbtContent = snbtContent.replace(",,", ",");
            }

            // 2. PARSEO SEGURO: Solución al error de compilación de la API de Nukkit
            CompoundTag rootTag = null;
            try {
                // Método estándar oficial en la gran mayoría de forks de NukkitX / Cloudburst
                rootTag = NBTIO.readJSON(snbtContent);
            } catch (NoSuchMethodError | Exception e) {
                // Fallback mecánico en caso de que la firma del método varíe en el JAR de desarrollo
                try (java.io.ByteArrayInputStream bai = new java.io.ByteArrayInputStream(snbtContent.getBytes(StandardCharsets.UTF_8));
                     java.io.DataInputStream dis = new java.io.DataInputStream(bai)) {
                    cn.nukkit.nbt.tag.Tag tag = cn.nukkit.nbt.tag.Tag.readNamedTag(dis);
                    if (tag instanceof CompoundTag) {
                        rootTag = (CompoundTag) tag;
                    }
                }
            }

            if (rootTag == null) {
                player.sendMessage(TextFormat.RED + "Error: Estructura NBT inválida, vacía o incompatible.");
                return true;
            }

            // 3. Determinar el tipo de objeto (Caja Shulker) de forma dinámica
            String boxId = "minecraft:shulker_box"; 

            if (rootTag.contains("Name")) {
                boxId = rootTag.getString("Name");
            } else if (rootTag.contains("Block") && rootTag.getCompound("Block").contains("name")) {
                boxId = rootTag.getCompound("Block").getString("name");
            } else if (rootTag.contains("states") && rootTag.getCompound("states").contains("color")) {
                String color = rootTag.getCompound("states").getString("color");
                boxId = "minecraft:" + color + "_shulker_box";
            } else {
                String lowerContent = snbtContent.toLowerCase();
                if (lowerContent.contains("color:\"lime\"") || lowerContent.contains("color:lime")) boxId = "minecraft:lime_shulker_box";
                else if (lowerContent.contains("color:\"yellow\"") || lowerContent.contains("color:yellow")) boxId = "minecraft:yellow_shulker_box";
                else if (lowerContent.contains("color:\"pink\"") || lowerContent.contains("color:pink")) boxId = "minecraft:pink_shulker_box";
                else if (lowerContent.contains("color:\"green\"") || lowerContent.contains("color:green")) boxId = "minecraft:green_shulker_box";
            }

            // Instanciar el objeto en Nukkit
            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            // 4. Extracción inteligente de la lista "Items"
            CompoundTag finalItemTag = new CompoundTag();

            if (rootTag.contains("Items")) {
                finalItemTag.putList(rootTag.getList("Items"));
            } else if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("Items")) {
                finalItemTag.putList(rootTag.getCompound("tag").getList("Items"));
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' dentro del archivo.");
                return true;
            }

            // 5. Conservar propiedades cosméticas (Nombres, lores, etc.)
            CompoundTag displayTag = null;
            if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("display")) {
                displayTag = rootTag.getCompound("tag").getCompound("display");
            } else if (rootTag.contains("display")) {
                displayTag = rootTag.getCompound("display");
            }

            if (displayTag != null) {
                finalItemTag.putCompound("display", displayTag);
            }

            // Copiar metadatos adicionales del contenedor si existen
            if (rootTag.contains("tag")) {
                CompoundTag originalTag = rootTag.getCompound("tag");
                if (originalTag.contains("customColor")) finalItemTag.putInt("customColor", originalTag.getInt("customColor"));
                if (originalTag.contains("RepairCost")) finalItemTag.putInt("RepairCost", originalTag.getInt("RepairCost"));
            }

            // Asignar el NBT definitivo al ítem
            itemToGive.setNamedTag(finalItemTag);

            // 6. Entrega al jugador
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' procesado con éxito.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente en el inventario.");
            }

        } catch (IOException e) {
            player.sendMessage(TextFormat.RED + "Error de Entrada/Salida al leer el archivo.");
            getServer().getLogger().logException(e);
        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico de parseo. Revisa la consola.");
            getServer().getLogger().logException(e);
        }

        return true;
    }
}
