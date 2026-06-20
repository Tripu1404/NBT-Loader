package com.tripu1404.nbtloader;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        getLogger().info(TextFormat.GREEN + "NBTLoader para Kits Avanzados activado con soporte Nivel 32767. Hecho por Tripu1404.");
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
            player.sendMessage(TextFormat.RED + "El archivo '" + args[0] + "' no existe.");
            return true;
        }

        try {
            String rawContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8).trim();
            
            if (rawContent.contains(",,")) rawContent = rawContent.replace(",,", ",");
            if (rawContent.contains(",}")) rawContent = rawContent.replace(",}", "}");

            Object parsedStructure = parseMiniSNBT(rawContent);
            if (!(parsedStructure instanceof Map)) {
                player.sendMessage(TextFormat.RED + "Error: La raíz del archivo debe ser un objeto compuesto {}.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsedStructure;

            // 1. OBTENER ID Y COLOR DE LA SHULKER PRINCIPAL
            String boxId = "minecraft:shulker_box";
            
            if (rootMap.containsKey("Name")) {
                boxId = String.valueOf(rootMap.get("Name"));
            } else if (rootMap.containsKey("Block") && rootMap.get("Block") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blockMap = (Map<String, Object>) rootMap.get("Block");
                if (blockMap.containsKey("name")) {
                    boxId = String.valueOf(blockMap.get("name"));
                }
                if (blockMap.containsKey("states") && blockMap.get("states") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> statesMap = (Map<String, Object>) blockMap.get("states");
                    if (statesMap.containsKey("color")) {
                        boxId = "minecraft:" + String.valueOf(statesMap.get("color")).replace("\"", "") + "_shulker_box";
                    }
                }
            } else if (rootMap.containsKey("states") && rootMap.get("states") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> statesMap = (Map<String, Object>) rootMap.get("states");
                if (statesMap.containsKey("color")) {
                    boxId = "minecraft:" + String.valueOf(statesMap.get("color")).replace("\"", "") + "_shulker_box";
                }
            }

            // Fallback de traducción directa si solo viene "shulker_box" plano pero el archivo contiene colores clave
            if (boxId.equals("minecraft:shulker_box") || boxId.equals("minecraft:undyed_shulker_box")) {
                String contentLower = rawContent.toLowerCase();
                if (contentLower.contains("color:\"lime\"") || contentLower.contains("color:lime")) boxId = "minecraft:lime_shulker_box";
                else if (contentLower.contains("color:\"pink\"") || contentLower.contains("color:pink")) boxId = "minecraft:pink_shulker_box";
                else if (contentLower.contains("color:\"green\"") || contentLower.contains("color:green")) boxId = "minecraft:green_shulker_box";
                else if (contentLower.contains("color:\"brown\"") || contentLower.contains("color:brown")) boxId = "minecraft:brown_shulker_box";
                else if (contentLower.contains("color:\"yellow\"") || contentLower.contains("color:yellow")) boxId = "minecraft:yellow_shulker_box";
            }

            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            // 2. CONVERTIR TODO EL CONTENIDO DEL MAPA RAÍZ A UN COMPOUNDTAG COMPLETO
            CompoundTag fullParsedTag = convertMapToCompoundTag(rootMap);
            CompoundTag finalItemTag = new CompoundTag();

            // Extraer la lista 'Items' de forma limpia sin importar su ubicación jerárquica
            if (fullParsedTag.contains("Items")) {
                finalItemTag.putList(fullParsedTag.getList("Items"));
            } else if (fullParsedTag.contains("tag") && fullParsedTag.getCompound("tag").contains("Items")) {
                finalItemTag.putList(fullParsedTag.getCompound("tag").getList("Items"));
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' dentro del archivo.");
                return true;
            }

            // Mapear metadatos cosméticos globales si existen
            if (fullParsedTag.contains("tag")) {
                CompoundTag innerTag = fullParsedTag.getCompound("tag");
                if (innerTag.contains("display")) finalItemTag.putCompound("display", innerTag.getCompound("display"));
                if (innerTag.contains("customColor")) finalItemTag.putInt("customColor", innerTag.getInt("customColor"));
                if (innerTag.contains("RepairCost")) finalItemTag.putInt("RepairCost", innerTag.getInt("RepairCost"));
            } else {
                if (fullParsedTag.contains("display")) finalItemTag.putCompound("display", fullParsedTag.getCompound("display"));
                if (fullParsedTag.contains("customColor")) finalItemTag.putInt("customColor", fullParsedTag.getInt("customColor"));
                if (fullParsedTag.contains("RepairCost")) finalItemTag.putInt("RepairCost", fullParsedTag.getInt("RepairCost"));
            }

            // Aplicar el NBT reconstruído al ítem final
            itemToGive.setNamedTag(finalItemTag);

            // 3. ENTREGA AL JUGADOR
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' entregado correctamente (NBT, Encantamientos y Colores corregidos).");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente en tu inventario.");
            }

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico al procesar este Kit. Revisa la consola.");
            getServer().getLogger().logException(e);
        }

        return true;
    }

    // --- CONVERTIDOR INTEGRAL RECURSIVO NATIVO ---
    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // Interceptamos la lista de encantamientos de Minecraft ("ench")
            if (key.equalsIgnoreCase("ench") && val instanceof List) {
                ListTag<CompoundTag> enchListTag = new ListTag<>("ench");
                for (Object enchObj : (List<?>) val) {
                    if (enchObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> enchMap = (Map<String, Object>) enchObj;
                        int id = 0;
                        int lvl = 1;
                        if (enchMap.containsKey("id")) id = safeParseInt(enchMap.get("id"));
                        if (enchMap.containsKey("lvl")) lvl = safeParseInt(enchMap.get("lvl"));

                        CompoundTag enchEntry = new CompoundTag()
                                .putShort("id", id)
                                .putShort("lvl", lvl);
                        enchListTag.add(enchEntry);
                    }
                }
                compound.putList(enchListTag);
                continue;
            }

            // Mapeo dinámico recursivo para sub-compuestos y listas
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                CompoundTag subCompound = convertMapToCompoundTag((Map<String, Object>) val);
                compound.putCompound(key, subCompound);
            } else if (val instanceof List) {
                ListTag<cn.nukkit.nbt.tag.Tag> listTag = new ListTag<>(key);
                for (Object subVal : (List<?>) val) {
                    if (subVal instanceof Map) {
                        @SuppressWarnings("unchecked")
                        CompoundTag listItem = convertMapToCompoundTag((Map<String, Object>) subVal);
                        listTag.add(listItem);
                    } else if (subVal instanceof String) {
                        listTag.add(new cn.nukkit.nbt.tag.StringTag("", String.valueOf(subVal).replace("\"", "")));
                    } else {
                        // Respaldo para valores enteros o numéricos dentro de listas planas
                        listTag.add(new cn.nukkit.nbt.tag.IntTag("", safeParseInt(subVal)));
                    }
                }
                compound.putList(listTag);
            } else if (val instanceof Integer) {
                compound.putInt(key, (Integer) val);
            } else if (val instanceof Double || val instanceof Float) {
                compound.putDouble(key, ((Number) val).doubleValue());
            } else if (val instanceof Boolean) {
                compound.putBoolean(key, (Boolean) val);
            } else {
                // Conversión limpia para cadenas o primitivos de Minecraft con sufijos erróneos
                String strVal = String.valueOf(val).replace("\"", "");
                if (strVal.matches("-?\\d+")) {
                    compound.putInt(key, Integer.parseInt(strVal));
                } else {
                    compound.putString(key, strVal);
                }
            }
        }
        return compound;
    }

    private static int safeParseInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        String str = String.valueOf(obj).trim().replaceAll("[bslfdBSLFD]", "");
        if (str.isEmpty()) return 0;
        if (str.contains(".")) {
            return (int) Double.parseDouble(str);
        }
        return Integer.parseInt(str);
    }

    private static Object parseMiniSNBT(String s) {
        s = s.trim();
        if (s.startsWith("{")) {
            Map<String, Object> map = new LinkedHashMap<>();
            s = s.substring(1, s.length() - 1).trim();
            int level = 0;
            boolean inQuotes = false;
            StringBuilder current = new StringBuilder();
            List<String> tokens = new ArrayList<>();

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"') inQuotes = !inQuotes;
                if (!inQuotes) {
                    if (c == '{' || c == '[') level++;
                    if (c == '}' || c == ']') level--;
                    if (c == ',' && level == 0) {
                        tokens.add(current.toString().trim());
                        current = new StringBuilder();
                        continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0) tokens.add(current.toString().trim());

            for (String token : tokens) {
                int colonIdx = token.indexOf(':');
                if (colonIdx != -1) {
                    String k = token.substring(0, colonIdx).trim().replace("\"", "");
                    String v = token.substring(colonIdx + 1).trim();
                    map.put(k, parseMiniSNBT(v));
                }
            }
            return map;
        } else if (s.startsWith("[")) {
            List<Object> list = new ArrayList<>();
            s = s.substring(1, s.length() - 1).trim();
            int level = 0;
            boolean inQuotes = false;
            StringBuilder current = new StringBuilder();
            List<String> tokens = new ArrayList<>();

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"') inQuotes = !inQuotes;
                if (!inQuotes) {
                    if (c == '{' || c == '[') level++;
                    if (c == '}' || c == ']') level--;
                    if (c == ',' && level == 0) {
                        tokens.add(current.toString().trim());
                        current = new StringBuilder();
                        continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0) tokens.add(current.toString().trim());

            for (String token : tokens) {
                if (!token.isEmpty()) list.add(parseMiniSNBT(token));
            }
            return list;
        } else {
            if (s.endsWith("b") || s.endsWith("s") || s.endsWith("l") || s.endsWith("f") || s.endsWith("d") ||
                s.endsWith("B") || s.endsWith("S") || s.endsWith("L") || s.endsWith("F") || s.endsWith("D")) {
                String sub = s.substring(0, s.length() - 1);
                try { return sub.contains(".") ? Double.parseDouble(sub) : Integer.parseInt(sub); } catch (Exception ignored) {}
            }
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
            try { return s.contains(".") ? Double.parseDouble(s) : Integer.parseInt(s); } catch (Exception ignored) {}
            return s;
        }
    }
}
