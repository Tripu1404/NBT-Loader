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
import java.util.*;

public class NBTLoader extends PluginBase {

    @Override
    public void onEnable() {
        File nbtFolder = new File(getDataFolder(), "nbts");
        if (!nbtFolder.exists()) nbtFolder.mkdirs();
        getLogger().info(TextFormat.GREEN + "NBTLoader cargado. Sistema robusto activo.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !command.getName().equalsIgnoreCase("givekit")) return false;
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(TextFormat.YELLOW + "Uso: /givekit <nombre>");
            return true;
        }

        String fileName = String.join(" ", args).trim();
        if (!fileName.endsWith(".json")) fileName += ".json";
        
        File nbtFile = new File(new File(getDataFolder(), "nbts"), fileName);
        if (!nbtFile.exists()) {
            player.sendMessage(TextFormat.RED + "Archivo no encontrado: " + fileName);
            return true;
        }

        try {
            // Leer contenido y limpiar formato extraño de archivos exportados
            String rawContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8).trim();
            
            Object parsed = parseMiniSNBT(rawContent);
            if (!(parsed instanceof Map)) {
                player.sendMessage(TextFormat.RED + "Error: Estructura de archivo no válida.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsed;
            
            // 1. Detección precisa de bloque leyendo el objeto "Block"
            int itemId = Item.SHULKER_BOX; // Default
            int meta = 0;
            
            if (rootMap.containsKey("Block")) {
                Object blockObj = rootMap.get("Block");
                if (blockObj instanceof Map) {
                    Map<String, Object> blockMap = (Map<String, Object>) blockObj;
                    String blockName = String.valueOf(blockMap.getOrDefault("name", ""));
                    
                    if (blockName.contains("chest")) {
                        itemId = Item.CHEST;
                    } else if (blockName.contains("shulker")) {
                        itemId = Item.SHULKER_BOX;
                        // Extraer color si es un shulker
                        String[] colors = {"white","orange","magenta","light_blue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
                        for(int i=0; i<colors.length; i++) if(blockName.contains(colors[i])) meta = i;
                    }
                }
            }

            // 2. Construcción del Item y su NBT completo
            Item item = Item.get(itemId, meta, 1);
            
            if (rootMap.containsKey("tag")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tagMap = (Map<String, Object>) rootMap.get("tag");
                CompoundTag fullTag = convertMapToCompoundTag(tagMap);
                
                // Asegurar que el BlockEntityTag contenga los Items correctamente
                CompoundTag finalTag = new CompoundTag();
                if (fullTag.contains("Items")) {
                    finalTag.putCompound("BlockEntityTag", new CompoundTag().putList(fullTag.getList("Items")));
                }
                
                // Mapear el resto de propiedades NBT (Lore, nombre, encantamientos, etc)
                if (fullTag.contains("display")) finalTag.putCompound("display", fullTag.getCompound("display"));
                if (fullTag.contains("RepairCost")) finalTag.putInt("RepairCost", fullTag.getInt("RepairCost"));
                if (fullTag.contains("ench")) finalTag.putList(fullTag.getList("ench"));
                
                item.setNamedTag(finalTag);
            }

            player.getInventory().addItem(item);
            player.sendMessage(TextFormat.GREEN + "Kit '" + fileName + "' entregado.");

        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(TextFormat.RED + "Error procesando el kit. Revisa la consola.");
        }
        return true;
    }

    // --- MÉTODOS ROBUSTOS DE CONVERSIÓN ---

    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            
            if (val instanceof Map) {
                compound.putCompound(key, convertMapToCompoundTag((Map<String, Object>) val));
            } else if (val instanceof List) {
                ListTag<cn.nukkit.nbt.tag.Tag> listTag = new ListTag<>(key);
                for (Object subVal : (List<?>) val) {
                    if (subVal instanceof Map) listTag.add(convertMapToCompoundTag((Map<String, Object>) subVal));
                    else if (subVal instanceof String) listTag.add(new cn.nukkit.nbt.tag.StringTag("", String.valueOf(subVal).replace("\"", "")));
                    else {
                        long n = safeParseLong(subVal);
                        if (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) listTag.add(new cn.nukkit.nbt.tag.LongTag("", n));
                        else listTag.add(new cn.nukkit.nbt.tag.IntTag("", (int) n));
                    }
                }
                compound.putList(listTag);
            } else if (val instanceof Long) compound.putLong(key, (Long) val);
            else if (val instanceof Integer) compound.putInt(key, (Integer) val);
            else {
                String strVal = String.valueOf(val).replace("\"", "");
                // Intentar detectar si es un número disfrazado de string (como los "1b")
                try {
                    long n = safeParseLong(strVal);
                    if (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) compound.putLong(key, n);
                    else compound.putInt(key, (int) n);
                } catch (Exception e) {
                    compound.putString(key, strVal);
                }
            }
        }
        return compound;
    }

    // Método vital: elimina los sufijos 'b', 's', 'l' de los archivos como ivo.json
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
            String[] items = s.split(",");
            for(String item : items) if(!item.trim().isEmpty()) list.add(parseMiniSNBT(item.trim()));
            return list;
        }
        return s.replace("\"", "");
    }
}
