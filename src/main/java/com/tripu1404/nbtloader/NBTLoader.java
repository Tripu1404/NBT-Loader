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
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        getLogger().info(TextFormat.GREEN + "NBTLoader Optimizado activado.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !command.getName().equalsIgnoreCase("givekit")) return false;
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(TextFormat.YELLOW + "Uso: /givekit <nombre>");
            return true;
        }

        File nbtFile = new File(new File(getDataFolder(), "nbts"), args[0].endsWith(".txt") ? args[0] : args[0] + ".txt");
        if (!nbtFile.exists()) {
            player.sendMessage(TextFormat.RED + "Archivo no encontrado.");
            return true;
        }

        try {
            String rawContent = new String(Files.readAllBytes(nbtFile.toPath()), StandardCharsets.UTF_8);
            Object parsed = parseMiniSNBT(rawContent);
            if (!(parsed instanceof Map)) {
                player.sendMessage(TextFormat.RED + "Error: Formato de archivo inválido.");
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) parsed;
            CompoundTag fullTag = convertMapToCompoundTag(rootMap);

            int itemId = Item.SHULKER_BOX;
            int meta = 0;
            String content = rawContent.toLowerCase();
            
            // Detección inteligente de contenedores y colores
            if (content.contains("chest")) itemId = Item.CHEST;
            String[] colors = {"white","orange","magenta","light_blue","yellow","lime","pink","gray","silver","cyan","purple","blue","brown","green","red","black"};
            for(int i=0; i<colors.length; i++) if(content.contains(colors[i])) meta = i;

            Item item = Item.get(itemId, meta, 1);

            // Inyección en BlockEntityTag para persistencia del inventario
            CompoundTag blockEntityTag = new CompoundTag();
            if (fullTag.contains("Items")) blockEntityTag.putList(fullTag.getList("Items"));
            else if (fullTag.contains("tag") && fullTag.getCompound("tag").contains("Items")) blockEntityTag.putList(fullTag.getCompound("tag").getList("Items"));

            item.setNamedTag(new CompoundTag()
                .putCompound("BlockEntityTag", blockEntityTag)
                .putString("display", "{\"Name\":\"§r" + args[0] + "\"}")
            );

            player.getInventory().addItem(item);
            player.sendMessage(TextFormat.GREEN + "Kit '" + args[0] + "' entregado correctamente.");

        } catch (Exception e) {
            player.sendMessage(TextFormat.RED + "Error al procesar el kit.");
            e.printStackTrace();
        }
        return true;
    }

    private static CompoundTag convertMapToCompoundTag(Map<String, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            String key = entry.getKey();
            if (val instanceof Map) compound.putCompound(key, convertMapToCompoundTag((Map<String, Object>) val));
            else if (val instanceof List) {
                ListTag<cn.nukkit.nbt.tag.Tag> list = new ListTag<>(key);
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) list.add(convertMapToCompoundTag((Map<String, Object>) item));
                    else if (item instanceof String) list.add(new cn.nukkit.nbt.tag.StringTag("", (String) item));
                    else if (item instanceof Number) {
                        long n = ((Number) item).longValue();
                        if (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE) list.add(new cn.nukkit.nbt.tag.LongTag("", n));
                        else list.add(new cn.nukkit.nbt.tag.IntTag("", (int) n));
                    }
                }
                compound.putList(list);
            } else if (val instanceof Long) compound.putLong(key, (Long) val);
            else if (val instanceof Integer) compound.putInt(key, (Integer) val);
            else compound.putString(key, String.valueOf(val).replace("\"", ""));
        }
        return compound;
    }

    // Aquí está el parser completo que faltaba en la versión reducida
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
            // Lógica simplificada de tokens para listas
            String[] items = s.split(",");
            for(String item : items) if(!item.trim().isEmpty()) list.add(parseMiniSNBT(item.trim()));
            return list;
        }
        return s.replace("\"", "");
    }
}
