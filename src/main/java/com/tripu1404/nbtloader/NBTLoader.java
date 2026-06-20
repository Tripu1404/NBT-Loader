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
        }
        getLogger().info(TextFormat.GREEN + "NBTLoader para Kits Avanzados activado (Fix Aire/Shulker Color).");
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

            // 1. SOLUCIÓN AL ERROR DE DAR "AIRE": MAPEO POR META (DAÑO) Y NO POR STRING ID
            int itemId = Item.SHULKER_BOX; // 218 en Nukkit
            int meta = 0; // Color por defecto

            String lowerContent = rawContent.toLowerCase();

            // Detectar si es un cofre o una shulker y aplicar el color vía meta
            if (lowerContent.contains("minecraft:chest") || lowerContent.contains("name:\"chest\"") || lowerContent.contains("name:chest")) {
                itemId = Item.CHEST; // 54 en Nukkit
            } else {
                if (lowerContent.contains("color:\"white\"") || lowerContent.contains("color:white")) meta = 0;
                else if (lowerContent.contains("color:\"orange\"") || lowerContent.contains("color:orange")) meta = 1;
                else if (lowerContent.contains("color:\"magenta\"") || lowerContent.contains("color:magenta")) meta = 2;
                else if (lowerContent.contains("color:\"light_blue\"") || lowerContent.contains("color:light_blue")) meta = 3;
                else if (lowerContent.contains("color:\"yellow\"") || lowerContent.contains("color:yellow")) meta = 4;
                else if (lowerContent.contains("color:\"lime\"") || lowerContent.contains("color:lime")) meta = 5;
                else if (lowerContent.contains("color:\"pink\"") || lowerContent.contains("color:pink")) meta = 6;
                else if (lowerContent.contains("color:\"gray\"") || lowerContent.contains("color:gray")) meta = 7;
                else if (lowerContent.contains("color:\"silver\"") || lowerContent.contains("color:silver") || lowerContent.contains("color:light_gray")) meta = 8;
                else if (lowerContent.contains("color:\"cyan\"") || lowerContent.contains("color:cyan")) meta = 9;
                else if (lowerContent.contains("color:\"purple\"") || lowerContent.contains("color:purple")) meta = 10;
                else if (lowerContent.contains("color:\"blue\"") || lowerContent.contains("color:blue")) meta = 11;
                else if (lowerContent.contains("color:\"brown\"") || lowerContent.contains("color:brown")) meta = 12;
                else if (lowerContent.contains("color:\"green\"") || lowerContent.contains("color:green")) meta = 13;
                else if (lowerContent.contains("color:\"red\"") || lowerContent.contains("color:red")) meta = 14;
                else if (lowerContent.contains("color:\"black\"") || lowerContent.contains("color:black")) meta = 15;
            }

            // Crear el bloque explícito (Evita devolver Aire)
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
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó el inventario ('Items') en este Kit.");
                return true;
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

    // --- CONVERTIDOR DINÁMICO RECURSIVO ---
    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            // Forzar que los encantamientos sean ShortTags
            if (key.equalsIgnoreCase("ench") && val instanceof List) {
                ListTag<CompoundTag> enchListTag = new ListTag<>("ench");
                for (Object enchObj : (List<?>) val) {
                    if (enchObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> enchMap = (Map<String, Object>) enchObj;
                        int id = 0, lvl = 1;
                        if (enchMap.containsKey("id")) id = (int) safeParseLong(enchMap.get("id"));
                        if (enchMap.containsKey("lvl")) lvl = (int) safeParseLong(enchMap.get("lvl"));

                        CompoundTag enchEntry = new CompoundTag()
                                .putShort("id", id)
                                .putShort("lvl", lvl);
                        enchListTag.add(enchEntry);
                    }
                }
                compound.putList(enchListTag);
                continue;
            }

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
                        long rawNum = safeParseLong(subVal);
                        if (rawNum > Integer.MAX_VALUE || rawNum < Integer.MIN_VALUE) {
                            listTag.add(new cn.nukkit.nbt.tag.LongTag("", rawNum));
                        } else {
                            listTag.add(new cn.nukkit.nbt.tag.IntTag("", (int) rawNum));
                        }
                    }
                }
                compound.putList(listTag);
            } else if (val instanceof Long) {
                compound.putLong(key, (Long) val);
            } else if (val instanceof Integer) {
                compound.putInt(key, (Integer) val);
            } else if (val instanceof Double || val instanceof Float) {
                compound.putDouble(key, ((Number) val).doubleValue());
            } else if (val instanceof Boolean) {
                compound.putBoolean(key, (Boolean) val);
            } else {
                String strVal = String.valueOf(val).replace("\"", "");
                if (strVal.matches("-?\\d+")) {
                    try {
                        long parsedLong = Long.parseLong(strVal);
                        if (parsedLong > Integer.MAX_VALUE || parsedLong < Integer.MIN_VALUE) {
                            compound.putLong(key, parsedLong);
                        } else {
                            compound.putInt(key, (int) parsedLong);
                        }
                    } catch (NumberFormatException e) {
                        compound.putString(key, strVal);
                    }
                } else {
                    compound.putString(key, strVal);
                }
            }
        }
        return compound;
    }

    private static long safeParseLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        String str = String.valueOf(obj).trim().replaceAll("[bslfdBSLFD]", "");
        if (str.isEmpty()) return 0L;
        if (str.contains(".")) return (long) Double.parseDouble(str);
        return Long.parseLong(str);
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
                try { return sub.contains(".") ? Double.parseDouble(sub) : Long.parseLong(sub); } catch (Exception ignored) {}
            }
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
            try { return s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s); } catch (Exception ignored) {}
            return s;
        }
    }
}
