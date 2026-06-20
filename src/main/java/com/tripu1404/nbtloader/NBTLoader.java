package com.tripu1404.nbtloader;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
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
        }
        getLogger().info(TextFormat.GREEN + "NBTLoader activado. SNBT Parser optimizado.");
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
            player.sendMessage(TextFormat.RED + "No tienes permisos.");
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
            String altName = fileName.endsWith(".txt") ? fileName.replace(".txt", ".json") : fileName.replace(".json", ".txt");
            nbtFile = new File(new File(getDataFolder(), "nbts"), altName);
        }

        if (!nbtFile.exists()) {
            player.sendMessage(TextFormat.RED + "El archivo '" + args[0] + "' no existe en la carpeta nbts.");
            return true;
        }

        try {
            String rawContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8).trim();
            
            if (rawContent.contains(",,")) rawContent = rawContent.replace(",,", ",");
            if (rawContent.contains(",}")) rawContent = rawContent.replace(",}", "}");

            Object parsedStructure = parseMiniSNBT(rawContent);
            if (!(parsedStructure instanceof Map)) {
                player.sendMessage(TextFormat.RED + "Error: La raíz del archivo NBT debe ser un objeto compuesto {}.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsedStructure;

            // 1. SOLUCIÓN AL FALSO POSITIVO DEL COFRE: Evaluar solo el identificador principal
            int itemId = Item.SHULKER_BOX; 
            int meta = 0; 
            String rootId = "";

            if (rootMap.containsKey("id")) {
                rootId = String.valueOf(rootMap.get("id")).toLowerCase();
            } else if (rootMap.containsKey("Block")) {
                Object blockObj = rootMap.get("Block");
                if (blockObj instanceof Map) {
                    Map<?, ?> blockMap = (Map<?, ?>) blockObj;
                    if (blockMap.containsKey("name")) {
                        rootId = String.valueOf(blockMap.get("name")).toLowerCase();
                    }
                    if (blockMap.containsKey("states")) {
                        Object statesObj = blockMap.get("states");
                        if (statesObj instanceof Map) {
                            Map<?, ?> statesMap = (Map<?, ?>) statesObj;
                            if (statesMap.containsKey("color")) {
                                String colorStr = String.valueOf(statesMap.get("color")).replace("\"", "").toLowerCase();
                                meta = parseColorMeta(colorStr);
                            }
                        }
                    }
                }
            }

            // Aplicar el ID definitivo evaluando solo la raíz
            if (rootId.contains("chest") && !rootId.contains("ender")) {
                itemId = Item.CHEST;
                meta = 0;
            } else if (rootId.contains("ender_chest") || rootId.contains("enderchest")) {
                itemId = Item.ENDER_CHEST;
                meta = 0;
            } else if (rootId.contains("shulker")) {
                itemId = Item.SHULKER_BOX;
            }

            Item itemToGive = Item.get(itemId, meta, 1);

            if (itemToGive.getId() == 0) {
                player.sendMessage(TextFormat.RED + "Error del servidor: No se pudo generar el bloque base.");
                return true;
            }

            // 2. EXTRAER NBT Y ARMAR EL COMPUESTO
            CompoundTag fullParsedTag = convertMapToCompoundTag(rootMap);
            CompoundTag finalItemTag = new CompoundTag();

            if (fullParsedTag.contains("Items")) {
                finalItemTag.putList(fullParsedTag.getList("Items"));
            } else if (fullParsedTag.contains("tag") && fullParsedTag.getCompound("tag").contains("Items")) {
                finalItemTag.putList(fullParsedTag.getCompound("tag").getList("Items"));
            }

            // 3. INYECTAR COSMÉTICOS Y GUARDAR
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

            itemToGive.setNamedTag(finalItemTag);

            // 4. ENTREGAR
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' procesado y entregado con éxito.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente.");
            }

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico. Revisa la consola.");
            getServer().getLogger().logException(e);
        }

        return true;
    }

    // Traduce los strings de colores a las metas de Shulker Box
    private static int parseColorMeta(String color) {
        switch (color) {
            case "white": return 0;
            case "orange": return 1;
            case "magenta": return 2;
            case "light_blue": return 3;
            case "yellow": return 4;
            case "lime": return 5;
            case "pink": return 6;
            case "gray": return 7;
            case "silver":
            case "light_gray": return 8;
            case "cyan": return 9;
            case "purple": return 10;
            case "blue": return 11;
            case "brown": return 12;
            case "green": return 13;
            case "red": return 14;
            case "black": return 15;
            default: return 0;
        }
    }

    // --- CONVERTIDOR DINÁMICO QUE RESPETA TIPOS ESTRICTOS DE NUKKIT ---
    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                CompoundTag subCompound = convertMapToCompoundTag((Map<String, Object>) val);
                compound.putCompound(key, subCompound);
            } else if (val instanceof List) {
                ListTag<Tag> listTag = new ListTag<>(key);
                for (Object subVal : (List<?>) val) {
                    listTag.add(convertObjectToTag("", subVal));
                }
                compound.putList(listTag);
            } else {
                compound.put(key, convertObjectToTag(key, val));
            }
        }
        return compound;
    }

    // Mapea Strings crudos hacia los Tags exactos basándose en sus sufijos (Ej: "1b" -> ByteTag)
    @SuppressWarnings("unchecked")
    private static Tag convertObjectToTag(String name, Object valObj) {
        if (valObj instanceof Map) {
            CompoundTag c = convertMapToCompoundTag((Map<String, Object>) valObj);
            return c.setName(name);
        }
        if (valObj instanceof List) {
            ListTag<Tag> listTag = new ListTag<>(name);
            for (Object subVal : (List<?>) valObj) {
                listTag.add(convertObjectToTag("", subVal));
            }
            return listTag;
        }

        String str = String.valueOf(valObj).trim();

        // 1. Strings envueltos en comillas puras
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return new cn.nukkit.nbt.tag.StringTag(name, str.substring(1, str.length() - 1));
        }

        // 2. Booleanos nativos
        if (str.equalsIgnoreCase("true")) return new cn.nukkit.nbt.tag.ByteTag(name, 1);
        if (str.equalsIgnoreCase("false")) return new cn.nukkit.nbt.tag.ByteTag(name, 0);

        // 3. Sufijos estrictos SNBT (CRÍTICO para Shulker Boxes en Nukkit)
        if (str.matches("-?\\d+[bB]")) {
            return new cn.nukkit.nbt.tag.ByteTag(name, Byte.parseByte(str.substring(0, str.length() - 1)));
        }
        if (str.matches("-?\\d+[sS]")) {
            return new cn.nukkit.nbt.tag.ShortTag(name, Short.parseShort(str.substring(0, str.length() - 1)));
        }
        if (str.matches("-?\\d+[lL]")) {
            return new cn.nukkit.nbt.tag.LongTag(name, Long.parseLong(str.substring(0, str.length() - 1)));
        }
        if (str.matches("-?\\d*\\.?\\d+[fF]")) {
            return new cn.nukkit.nbt.tag.FloatTag(name, Float.parseFloat(str.substring(0, str.length() - 1)));
        }
        if (str.matches("-?\\d*\\.?\\d+[dD]")) {
            return new cn.nukkit.nbt.tag.DoubleTag(name, Double.parseDouble(str.substring(0, str.length() - 1)));
        }

        // 4. Fallback de números brutos
        if (str.matches("-?\\d+")) {
            long l = Long.parseLong(str);
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
                return new cn.nukkit.nbt.tag.LongTag(name, l);
            } else {
                return new cn.nukkit.nbt.tag.IntTag(name, (int) l);
            }
        }
        if (str.matches("-?\\d+\\.\\d+")) {
            return new cn.nukkit.nbt.tag.DoubleTag(name, Double.parseDouble(str));
        }

        // 5. Todo lo demás es un String normal (Ej: minecraft:stone)
        return new cn.nukkit.nbt.tag.StringTag(name, str);
    }

    // --- ANALIZADOR SNBT SEGURO (MANTIENE STRINGS CRUDOS) ---
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
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inQuotes = !inQuotes;
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
                if (token.isEmpty()) continue;
                
                // Buscar los dos puntos ":" que no estén envueltos en comillas (CRÍTICO)
                int colonIdx = -1;
                boolean inKeyQuotes = false;
                for (int j = 0; j < token.length(); j++) {
                    if (token.charAt(j) == '"') inKeyQuotes = !inKeyQuotes;
                    if (!inKeyQuotes && token.charAt(j) == ':') {
                        colonIdx = j;
                        break;
                    }
                }

                if (colonIdx != -1) {
                    String k = token.substring(0, colonIdx).trim();
                    if (k.startsWith("\"") && k.endsWith("\"")) {
                        k = k.substring(1, k.length() - 1);
                    }
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
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inQuotes = !inQuotes;
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
                if (!token.trim().isEmpty()) {
                    list.add(parseMiniSNBT(token));
                }
            }
            return list;
        } else {
            // Ya no manipulamos los números aquí, retornamos el String crudo para preservar los sufijos.
            return s;
        }
    }
}
