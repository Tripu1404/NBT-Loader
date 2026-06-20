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

public class NBTLoader extends PluginBase {

    @Override
    public void onEnable() {
        File nbtFolder = new File(getDataFolder(), "nbts");
        if (!nbtFolder.exists()) {
            nbtFolder.mkdirs();
            
            saveResource("nbts/mommys_affection.json", false);
            saveResource("nbts/insane_nested.json", false);
            saveResource("nbts/build_nested_v1.json", false);
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
            player.sendMessage(TextFormat.RED + "El archivo '" + args[0] + "' no existe en la carpeta plugins/NBTLoader/nbts/");
            return true;
        }

        try {
            String snbtContent = new String(Files.readAllBytes(nbtFile.toPath())).trim();

            if (snbtContent.contains(",,")) {
                snbtContent = snbtContent.replace(",,", ",");
            }

            CompoundTag rootTag = NBTIO.parseJSON(snbtContent);

            if (rootTag == null) {
                player.sendMessage(TextFormat.RED + "Error: Estructura NBT inválida o vacía en el archivo.");
                return true;
            }

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
                else if (lowerContent.contains("color:\"brown\"") || lowerContent.contains("color:brown")) boxId = "minecraft:brown_shulker_box";
            }

            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            CompoundTag finalItemTag = new CompoundTag();

            if (rootTag.contains("Items")) {
                finalItemTag.putList(rootTag.getList("Items"));
            } else if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("Items")) {
                finalItemTag.putList(rootTag.getCompound("tag").getList("Items"));
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' dentro del archivo.");
                return true;
            }

            CompoundTag displayTag = null;
            if (rootTag.contains("tag") && rootTag.getCompound("tag").contains("display")) {
                displayTag = rootTag.getCompound("tag").getCompound("display");
            } else if (rootTag.contains("display")) {
                displayTag = rootTag.getCompound("display");
            }

            if (displayTag != null) {
                finalItemTag.putCompound("display", displayTag);
            }

            if (rootTag.contains("tag")) {
                CompoundTag originalTag = rootTag.getCompound("tag");
                if (originalTag.contains("customColor")) finalItemTag.putInt("customColor", originalTag.getInt("customColor"));
                if (originalTag.contains("RepairCost")) finalItemTag.putInt("RepairCost", originalTag.getInt("RepairCost"));
            }

            itemToGive.setNamedTag(finalItemTag);

            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' cargado y procesado con éxito.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente en el inventario.");
            }

        } catch (IOException e) {
            player.sendMessage(TextFormat.RED + "Error de Entrada/Salida al leer el archivo físico.");
            getServer().getLogger().logException(e);
        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error de parseo NBT: Revisa la consola para ver los detalles estructurales.");
            getServer().getLogger().logException(e);
        }

        return true;
    }
}
