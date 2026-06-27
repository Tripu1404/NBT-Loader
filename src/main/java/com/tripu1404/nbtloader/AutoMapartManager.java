package com.tripu1404.litematicamanager;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AutoMapartManager extends PluginBase {

    private File mapartFolder;
    // Mapa estático cacheado con colores promedio de bloques comunes para Mapart (Formato Bedrock/Nukkit)
    private final Map<Block, Color> blockColors = new HashMap<>();

    @Override
    public void onEnable() {
        this.mapartFolder = new File(this.getDataFolder(), "maparts");
        if (!this.mapartFolder.exists()) {
            this.mapartFolder.mkdirs();
        }
        
        // Inicializamos una paleta básica de bloques con sus colores correspondientes de mapa
        registerBlockColor(BlockID.WOOL, 0, new Color(221, 221, 221));       // Blanco
        registerBlockColor(BlockID.WOOL, 1, new Color(219, 125, 62));        // Naranja
        registerBlockColor(BlockID.WOOL, 2, new Color(179, 80, 188));        // Magenta
        registerBlockColor(BlockID.WOOL, 3, new Color(107, 138, 201));       // Celeste
        registerBlockColor(BlockID.WOOL, 4, new Color(177, 166, 39));        // Amarillo
        registerBlockColor(BlockID.WOOL, 5, new Color(65, 174, 56));         // Verde Lima
        registerBlockColor(BlockID.WOOL, 6, new Color(208, 132, 153));       // Rosa
        registerBlockColor(BlockID.WOOL, 7, new Color(64, 64, 64));          // Gris Oscuro
        registerBlockColor(BlockID.WOOL, 8, new Color(154, 161, 161));       // Gris Claro
        registerBlockColor(BlockID.WOOL, 9, new Color(46, 110, 137));        // Cian
        registerBlockColor(BlockID.WOOL, 10, new Color(126, 61, 181));       // Morado
        registerBlockColor(BlockID.WOOL, 11, new Color(46, 56, 141));        // Azul
        registerBlockColor(BlockID.WOOL, 12, new Color(79, 50, 31));         // Marrón
        registerBlockColor(BlockID.WOOL, 13, new Color(53, 70, 27));         // Verde
        registerBlockColor(BlockID.WOOL, 14, new Color(150, 52, 48));        // Rojo
        registerBlockColor(BlockID.WOOL, 15, new Color(25, 22, 22));         // Negro
        registerBlockColor(BlockID.CONCRETE, 0, new Color(207, 213, 214));   // Concreto Blanco
        registerBlockColor(BlockID.CONCRETE, 14, new Color(142, 32, 32));    // Concreto Rojo
        registerBlockColor(BlockID.GOLD_BLOCK, 0, new Color(244, 230, 76));  // Oro
        registerBlockColor(BlockID.LAPIS_BLOCK, 0, new Color(30, 67, 140));  // Lapis

        this.getLogger().info(TextFormat.GREEN + "AutoMapartManager cargado. Sube tus .png a /plugins/LitematicaManager/maparts/");
    }

    private void registerBlockColor(int id, int meta, Color color) {
        blockColors.put(Block.get(id, meta), color);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("amap")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormat.RED + "Comando exclusivo para jugadores.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(TextFormat.RED + "Uso: /amap <nombre_imagen.png> [usar_inventario: true/false]");
                return true;
            }

            Player player = (Player) sender;
            String fileName = args[0].endsWith(".png") ? args[0] : args[0] + ".png";
            File file = new File(mapartFolder, fileName);

            if (!file.exists()) {
                player.sendMessage(TextFormat.RED + "No se encontró la imagen: " + fileName);
                return true;
            }

            boolean useOnlyInventory = args.length > 1 && Boolean.parseBoolean(args[1]);

            player.sendMessage(TextFormat.YELLOW + "Procesando imagen y calculando similitudes de color...");

            try {
                BufferedImage image = ImageIO.read(file);
                int width = image.getWidth();
                int height = image.getHeight();

                // Límite de seguridad para evitar congelar el hilo principal del servidor (128x128 es 1 mapa completo)
                if (width > 256 || height > 256) {
                    player.sendMessage(TextFormat.RED + "Imagen demasiado grande. Máximo sugerido: 256x256 píxeles.");
                    return true;
                }

                Vector3 basePos = player.getPosition().floor();
                Level level = player.getLevel();
                PlayerInventory inv = player.getInventory();
                int placedCount = 0;

                // Recorremos la imagen tal como lo hace tu bucle z/x en C++
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < height; z++) {
                        int rgb = image.getRGB(x, z);
                        
                        // Ignorar píxeles completamente transparentes (Alfa = 0)
                        if (((rgb >> 24) & 0xff) == 0) continue;

                        Color pixelColor = new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);

                        // Encontrar el bloque con el color más cercano
                        Block bestBlock = getClosestBlock(pixelColor, useOnlyInventory, inv);

                        if (bestBlock != null) {
                            // Construcción en el plano XZ (horizontal en el suelo)
                            Vector3 targetPos = basePos.add(x, 0, z);
                            level.setBlock(targetPos, bestBlock, true, true);
                            placedCount++;
                        }
                    }
                }

                player.sendMessage(TextFormat.GREEN + "¡Mapart generado con éxito! Se colocaron " + placedCount + " bloques.");

            } catch (IOException e) {
                player.sendMessage(TextFormat.RED + "Error al leer el archivo de imagen.");
                this.getLogger().error("Excepción en AutoMapartManager: ", e);
            }
            return true;
        }
        return false;
    }

    // Algoritmo de distancia Euclidiana tridimensional (RGB) idéntico a tu getDifference en C++
    private double getDifference(Color c1, Color c2) {
        int dR = c1.getRed() - c2.getRed();
        int dG = c1.getGreen() - c2.getGreen();
        int dB = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dR * dR + dG * dG + dB * dB);
    }

    private Block getClosestBlock(Color pixelColor, boolean useInventory, PlayerInventory inv) {
        Block bestBlock = null;
        double closestColor = 999.0;

        for (Map.Entry<Block, Color> entry : blockColors.entrySet()) {
            Block block = entry.getKey();
            
            // Si el modo estricto de inventario está activo, verifica si el jugador lleva el bloque
            if (useInventory) {
                Item itemEquivalent = Item.get(block.getId(), block.getDamage());
                if (!inv.contains(itemEquivalent)) {
                    continue;
                }
            }

            double difference = getDifference(pixelColor, entry.getValue());
            if (difference < closestColor) {
                closestColor = difference;
                bestBlock = block;
            }
        }
        return bestBlock;
    }
}
