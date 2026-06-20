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
        getLogger().info(TextFormat.GREEN + "NBTLoader para Kits Avanzados activado.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("givekit")) return false;

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
        if (!fileName.endsWith(".json") && !fileName.endsWith(".txt")) fileName += ".txt";

        File nbtFile = new File(new File(getDataFolder(), "nbts"), fileName);
        if (!nbtFile.exists()) {
            String altName = fileName.endsWith(".txt") ? fileName.replace(".txt", ".json") : fileName.replace(".json", ".txt");
            nbtFile = new File(new File(getDataFolder(), "nbts"), altName);
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
                player.sendMessage(TextFormat.RED + "Error: La raíz del archivo NBT debe ser un objeto compuesto {}.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsedStructure;

            int itemId = Item.SHULKER_BOX;
            int meta = 0;
            String lowerContent = rawContent.toLowerCase();

            if (lowerContent.contains("minecraft:chest") || lowerContent.contains("name:\"chest\"") || lowerContent.contains("name:chest")) {
                itemId = Item.CHEST;
            } else {
                String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
                for (int i = 0; i < colors.length; i++) {
                    if (lowerContent.contains("color:\"" + colors[i] + "\"") || lowerContent.contains("color:" + colors[i])) {
                        meta = i;
                        break;
                    }
                }
            }

            Item itemToGive = Item.get(itemId, meta, 1);
            CompoundTag fullParsedTag = convertMapToCompoundTag(rootMap);
            CompoundTag itemTag = new CompoundTag();

            // 1. Configurar BlockEntityTag (Contenido)
            if (fullParsedTag.contains("Items")) {
                itemTag.putCompound("BlockEntityTag", new CompoundTag().putList(fullParsedTag.getList("Items")));
            } else if (fullParsedTag.contains("tag") && fullParsedTag.getCompound("tag").contains("Items")) {
                itemTag.putCompound("BlockEntityTag", new CompoundTag().putList(fullParsedTag.getCompound("tag").getList("Items")));
            }

            // 2. Configurar Cosméticos (Display, etc)
            if (fullParsedTag.contains("tag")) {
                CompoundTag innerTag = fullParsedTag.getCompound("tag");
                if (innerTag.contains("display")) itemTag.putCompound("display", innerTag.getCompound("display"));
                if (innerTag.contains("customColor")) itemTag.putInt("customColor", innerTag.getInt("customColor"));
                if (innerTag.contains("RepairCost")) itemTag.putInt("RepairCost", innerTag.getInt("RepairCost"));
            }
            
            // Si no tiene nombre en el display, le ponemos uno por defecto
            if (!itemTag.contains("display")) {
                itemTag.putCompound("display", new CompoundTag().putString("Name", "§r" + fileName));
            }

            itemToGive.setNamedTag(itemTag);

            // 3. ENTREGAR
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + fileName + "' entregado con éxito.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente.");
            }

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico al procesar el kit.");
            getServer().getLogger().logException(e);
        }

        return true;
    }

    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (key.equalsIgnoreCase("ench") && val instanceof List) {
                ListTag<CompoundTag> enchListTag = new ListTag<>("ench");
                for (Object enchObj : (List<?>) val) {
                    if (enchObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> enchMap = (Map<String, Object>) enchObj;
                        int id = 0, lvl = 1;
                        if (enchMap.containsKey("id")) id = (int) safeParseLong(enchMap.get("id"));
                        if (enchMap.containsKey("lvl")) lvl = (int) safeParseLong(enchMap.get("lvl"));
                        enchListTag.add(new CompoundTag().putShort("id", id).putShort("lvl", lvl));
                    }
                }
                compound.putList(enchListTag);
            } else if (val instanceof Map) {
                compound.putCompound(key, convertMapToCompoundTag((Map<String, Object>) val));
            } else if (val instanceof List) {
                ListTag<cn.nukkit.nbt.tag.Tag> listTag = new ListTag<>(key);
                for (Object subVal : (List<?>) val) {
                    if (subVal instanceof Map) {
                        listTag.add(convertMapToCompoundTag((Map<String, Object>) subVal));
                    } else if (subVal instanceof String) {
                        listTag.add(new cn.nukkit.nbt.tag.StringTag("", String.valueOf(subVal).replace("\"", "")));
                    } else {
                        long rawNum = safeParseLong(subVal);
                        if (rawNum > Integer.MAX_VALUE || rawNum < Integer.MIN_VALUE) listTag.add(new cn.nukkit.nbt.tag.LongTag("", rawNum));
                        else listTag.add(new cn.nukkit.nbt.tag.IntTag("", (int) rawNum));
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
                        if (parsedLong > Integer.MAX_VALUE || parsedLong < Integer.MIN_VALUE) compound.putLong(key, parsedLong);
                        else compound.putInt(key, (int) parsedLong);
                    } catch (NumberFormatException e) { compound.putString(key, strVal); }
                } else { compound.putString(key, strVal); }
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
            int level = 0; boolean inQuotes = false;
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
                        current = new StringBuilder(); continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0) tokens.add(current.toString().trim());
            for (String token : tokens) {
                int colonIdx = token.indexOf(':');
                if (colonIdx != -1) map.put(token.substring(0, colonIdx).trim().replace("\"", ""), parseMiniSNBT(token.substring(colonIdx + 1).trim()));
            }
            return map;
        } else if (s.startsWith("[")) {
            List<Object> list = new ArrayList<>();
            s = s.substring(1, s.length() - 1).trim();
            int level = 0; boolean inQuotes = false;
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
                        current = new StringBuilder(); continue;
                    }
                }
                current.append(c);
            }
            if (current.length() > 0) tokens.add(current.toString().trim());
            for (String token : tokens) if (!token.isEmpty()) list.add(parseMiniSNBT(token));
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
