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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            // 1. LEER EL CONTENIDO PLANO Y SANEARLO DE SINTAXIS SNBT
            String rawContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8).trim();
            
            // Reparar errores de formato comunes en kits ilegales (comas dobles o llaves mal cerradas)
            if (rawContent.contains(",,")) rawContent = rawContent.replace(",,", ",");
            if (rawContent.contains(",}")) rawContent = rawContent.replace(",}", "}");

            // 2. PARSEAR MEDIANTE UN MOTOR MINI-SNBT PERSONALIZADO (Evita Gson por completo)
            Object parsedStructure = parseMiniSNBT(rawContent);
            if (!(parsedStructure instanceof Map)) {
                player.sendMessage(TextFormat.RED + "Error: La raíz del archivo NBT debe ser un objeto compuesto {}.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsedStructure;

            // 3. DETERMINAR EL ID DE LA CAJA SHULKER DINÁMICAMENTE
            String boxId = "minecraft:shulker_box"; 
            if (rootMap.containsKey("Name")) {
                boxId = String.valueOf(rootMap.get("Name"));
            } else if (rootMap.containsKey("Block") && rootMap.get("Block") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> blockMap = (Map<String, Object>) rootMap.get("Block");
                if (blockMap.containsKey("name")) {
                    boxId = String.valueOf(blockMap.get("name"));
                }
            } else if (rootMap.containsKey("states") && rootMap.get("states") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> statesMap = (Map<String, Object>) rootMap.get("states");
                if (statesMap.containsKey("color")) {
                    boxId = "minecraft:" + String.valueOf(statesMap.get("color")).replace("\"", "") + "_shulker_box";
                }
            }

            // Instanciar ítem base
            Item itemToGive = Item.fromString(boxId);
            itemToGive.setCount(1);

            // 4. GENERACIÓN RECURSIVA REAL DE NBT (Corrige el inventario vacío)
            CompoundTag finalItemTag = new CompoundTag();

            // Buscar la lista "Items" en la raíz o dentro del nodo "tag"
            List<?> itemsRawList = null;
            Map<String, Object> tagSection = null;

            if (rootMap.containsKey("Items") && rootMap.get("Items") instanceof List) {
                itemsRawList = (List<?>) rootMap.get("Items");
            } else if (rootMap.containsKey("tag") && rootMap.get("tag") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedTag = (Map<String, Object>) rootMap.get("tag");
                tagSection = parsedTag;
                if (parsedTag.containsKey("Items") && parsedTag.get("Items") instanceof List) {
                    itemsRawList = (List<?>) parsedTag.get("Items");
                }
            }

            if (itemsRawList != null) {
                // Construimos la lista real usando tags genuinos de Nukkit
                ListTag<CompoundTag> itemsListTag = new ListTag<>("Items");
                for (Object element : itemsRawList) {
                    if (element instanceof Map) {
                        @SuppressWarnings("unchecked")
                        CompoundTag subItemTag = convertMapToCompoundTag((Map<String, Object>) element);
                        itemsListTag.add(subItemTag);
                    }
                }
                finalItemTag.putList(itemsListTag);
            } else {
                player.sendMessage(TextFormat.RED + "Error: No se localizó la lista 'Items' en el archivo.");
                return true;
            }

            // 5. SECCIÓN COSMÉTICA INTEGRADA SIN EXPLOSIONES DECIMALES
            if (tagSection != null) {
                if (tagSection.containsKey("display") && tagSection.get("display") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    CompoundTag displayTag = convertMapToCompoundTag((Map<String, Object>) tagSection.get("display"));
                    finalItemTag.putCompound("display", displayTag);
                }
                // Solución al NumberFormatException usando un lector de números seguro
                if (tagSection.containsKey("customColor")) {
                    finalItemTag.putInt("customColor", safeParseInt(tagSection.get("customColor")));
                }
                if (tagSection.containsKey("RepairCost")) {
                    finalItemTag.putInt("RepairCost", safeParseInt(tagSection.get("RepairCost")));
                }
            }

            // Guardar NBT en el ítem
            itemToGive.setNamedTag(finalItemTag);

            // 6. ENTREGA
            if (player.getInventory().canAddItem(itemToGive)) {
                player.getInventory().addItem(itemToGive);
                player.sendMessage(TextFormat.GREEN + "» Kit '" + nbtFile.getName() + "' procesado por completo y con inventario lleno.");
            } else {
                player.sendMessage(TextFormat.RED + "No tienes espacio suficiente en tu inventario.");
            }

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error crítico al procesar este Kit. Revisa la consola.");
            getServer().getLogger().logException(e);
        }

        return true;
    }

    // --- UTILIDADES DE CONVERSIÓN RECURSIVA ---

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
                ListTag<cn.nukkit.nbt.tag.Tag> listTag = new ListTag<>(key);
                for (Object subVal : (List<?>) val) {
                    if (subVal instanceof Map) {
                        @SuppressWarnings("unchecked")
                        CompoundTag listItem = convertMapToCompoundTag((Map<String, Object>) subVal);
                        listTag.add(listItem);
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
                compound.putString(key, String.valueOf(val).replace("\"", ""));
            }
        }
        return compound;
    }

    private static int safeParseInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        String str = String.valueOf(obj).trim();
        if (str.contains(".")) {
            return (int) Double.parseDouble(str);
        }
        return Integer.parseInt(str);
    }

    // Parser manual ultra dinámico que procesa SNBT y JSON con/sin comillas o sufijos numéricos
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
            // Limpieza de sufijos numéricos de Minecraft (ej: 64b -> 64, 0s -> 0)
            if (s.endsWith("b") || s.endsWith("s") || s.endsWith("l") || s.endsWith("f") || s.endsWith("d")) {
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
