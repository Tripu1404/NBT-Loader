package com.tripu1404.nbtloader;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
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

    @SuppressWarnings("unchecked")
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
            // 1. CARGA VÍA CONFIG (Universal)
            Config jsonConfig = new Config(nbtFile, Config.JSON);
            Map<String, Object> rootMap = jsonConfig.getAll();

            if (rootMap == null || rootMap.isEmpty()) {
                player.sendMessage(TextFormat.RED + "Error: El archivo NBT está vacío o no tiene un formato JSON válido.");
                return true;
            }

            // Convertimos el mapa de configuración a un CompoundTag de manera manual 
            // Esto evita usar NBTIO.putObjectProperty que cambia o no existe en algunos jars.
            CompoundTag rootTag = new CompoundTag();
            
            // Inyectamos de forma segura las propiedades mapeadas al tag raíz
            Map<String, cn.nukkit.nbt.tag.Tag> internalTags = rootTag.getTags();
            for (Map.Entry<String, Object> entry : rootMap.entrySet()) {
                if (entry.getValue() instanceof cn.nukkit.nbt.tag.Tag) {
                    internalTags.put(entry.getKey(), (cn.nukkit.nbt.tag.Tag) entry.getValue());
                }
            }

            // 2. DETERMINAR EL ID DE LA CAJA SHULKER DINÁMICAMENTE
            String boxId = "minecraft:shulker_box"; 

            if (rootMap.containsKey("Name")) {
                boxId = String.valueOf(rootMap.get("Name"));
            } else if (rootMap.containsKey("Block") && rootMap.get("Block") instanceof Map) {
                Map<String, Object> blockMap = (Map<String, Object>) rootMap.get("Block");
                if (blockMap.containsKey("name")) {
                    boxId = String.valueOf(blockMap.get("name"));
                }
            } else {
                // Mapeo por fallback si viene con "states"
                Object statesObj = rootMap.get("states");
                if (statesObj instanceof Map) {
                    Map<String, Object> statesMap = (Map<String, Object>) statesObj;
                    if (statesMap.containsKey("color")) {
                        boxId = "minecraft:" + statesMap.get("color") + "_shulker_box";
                    }
                }
            }

            // Crear el ítem base en el servidor
            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            // 3. EXTRACCIÓN MANUAL DE LA LISTA DE ÍTEMS INTERNOS
            // Solución al error de putList argument lengths diferring
            CompoundTag finalItemTag = new CompoundTag();
            ListTag<CompoundTag> itemsList = null;

            if (rootMap.containsKey("Items")) {
                Object itemsObj = rootMap.get("Items");
                if (itemsObj instanceof List) {
                    itemsList = new ListTag<>("Items");
                    for (Object itemObj : (List<?>) itemsObj) {
                        if (itemObj instanceof Map) {
                            CompoundTag itemTag = new CompoundTag();
                            // Rellenar de forma cruda las propiedades del subítem
                            itemTag.getTags().putAll((Map<String, cn.nukkit.nbt.tag.Tag>) itemObj);
                            itemsList.add(itemTag);
                        }
                    }
                }
            } else if (rootMap.containsKey("tag")) {
                Object tagObj = rootMap.get("tag");
                if (tagObj instanceof Map) {
                    Map<String, Object> innerTagMap = (Map<String, Object>) tagObj;
                    if (innerTagMap.containsKey("Items") && innerTagMap.get("Items") instanceof List) {
                        itemsList = new ListTag<>("Items");
                        for (Object itemObj : (List<?>) innerTagMap.get("Items")) {
                            if (itemObj instanceof Map) {
                                CompoundTag itemTag = new CompoundTag();
                                itemTag.getTags().putAll((Map<String, cn.nukkit.nbt.tag.Tag>) itemObj);
                                itemsList.add(itemTag);
                            }
                        }
                    }
                }
            }

            if (itemsList != null) {
                // Solución al error: En tu versión de Nukkit, putList(ListTag) no requiere un String de clave,
                // ya que toma el nombre directamente del constructor del ListTag ("Items")
                finalItemTag.putList(itemsList);
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' dentro del archivo.");
                return true;
            }

            // 4. PRESERVAR METADATOS COSMÉTICOS Y ADICIONALES (display, customColor, RepairCost)
            if (rootMap.containsKey("tag") && rootMap.get("tag") instanceof Map) {
                Map<String, Object> innerTagMap = (Map<String, Object>) rootMap.get("tag");
                
                if (innerTagMap.containsKey("display") && innerTagMap.get("display") instanceof Map) {
                    CompoundTag displayTag = new CompoundTag("display");
                    displayTag.getTags().putAll((Map<String, cn.nukkit.nbt.tag.Tag>) innerTagMap.get("display"));
                    finalItemTag.putCompound("display", displayTag);
                }
                
                if (innerTagMap.containsKey("customColor")) {
                    finalItemTag.putInt("customColor", Integer.parseInt(String.valueOf(innerTagMap.get("customColor"))));
                }
                if (innerTagMap.containsKey("RepairCost")) {
                    finalItemTag.putInt("RepairCost", Integer.parseInt(String.valueOf(innerTagMap.get("RepairCost"))));
                }
            } else if (rootMap.containsKey("display") && rootMap.get("display") instanceof Map) {
                CompoundTag displayTag = new CompoundTag("display");
                displayTag.getTags().putAll((Map<String, cn.nukkit.nbt.tag.Tag>) rootMap.get("display"));
                finalItemTag.putCompound("display", displayTag);
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
