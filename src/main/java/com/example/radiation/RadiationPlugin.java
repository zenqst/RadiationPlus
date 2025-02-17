package com.example.radiation;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.ChatColor;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class RadiationPlugin extends JavaPlugin {

    // Конфигурационные параметры (будут загружены из config.yml)
    public double accumulationRate;
    public double decayRate;
    public double level2Threshold;
    public double level3Threshold;
    public double level4Threshold;
    public double level5Threshold;
    public double baseDamage;
    private boolean hideRecipe;
    private ItemStack radiationSensor;

    // Хранение зон радиации (название -> зона)
    private Map<String, RadiationZone> zones = new HashMap<>();
    // Хранение уровня радиации у игроков (UUID -> значение)
    private Map<UUID, Double> radiationLevels = new HashMap<>();
    // Флаг глобальной активации радиации
    private boolean radiationActive = true;

    // Файл и конфигурация для зон
    private File zonesFile;
    private FileConfiguration zonesConfig;

    @Override
    public void onEnable() {
        // Сохранить config.yml по умолчанию (если не существует)
        saveDefaultConfig();
        loadConfigValues();

        // Создать и загрузить zones.yml
        createZonesFile();
        loadZones();

        // Загрузка конфига
        saveDefaultConfig();
        reloadConfig();
        hideRecipe = getConfig().getBoolean("hide-recipe", true);

        // Создание кастомного предмета
        createRadiationSensor();

        // Регистрация рецепта (результат – наш кастомный датчик)
        registerRecipe();

        // Если опция скрытия рецепта включена, скрываем рецепт у всех онлайн-игроков
        if (hideRecipe) {
            NamespacedKey recipeKey = new NamespacedKey(this, "radiation_sensor");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.undiscoverRecipe(recipeKey);
            }
        }

        // Регистрируем слушатель для скрытия рецепта при заходе новых игроков
        Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                NamespacedKey recipeKey = new NamespacedKey(RadiationPlugin.this, "radiation_sensor");
                event.getPlayer().undiscoverRecipe(recipeKey);
            }
        }, this);

        // Регистрация обработчика событий для датчика (ПКМ)
        getServer().getPluginManager().registerEvents(new SensorListener(), this);

        // Регистрируем команды
        if (getCommand("radzone") != null) {
            getCommand("radzone").setExecutor(new RadiationCommand(this));
            getCommand("radzone").setTabCompleter(new RadiationTabCompleter(this));
        }
        if (getCommand("test-r") != null) {
            getCommand("test-r").setExecutor(new TestRCommand());
        }
        
        // Регистрируем слушатель для очистки уровня радиации при выходе игрока
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                radiationLevels.remove(event.getPlayer().getUniqueId());
            }
        }, this);
        
        // Периодическая задача (каждую секунду) для обновления уровня радиации
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    double currentRadiation = radiationLevels.getOrDefault(uuid, 0.0);
                    
                    // Если игрок носит полный комплект кольчужной брони, радиация не накапливается
                    boolean fullChainmail = false;
                    ItemStack helmet = player.getInventory().getHelmet();
                    ItemStack chestplate = player.getInventory().getChestplate();
                    ItemStack leggings = player.getInventory().getLeggings();
                    ItemStack boots = player.getInventory().getBoots();
                    if (helmet != null && helmet.getType() == Material.CHAINMAIL_HELMET &&
                        chestplate != null && chestplate.getType() == Material.CHAINMAIL_CHESTPLATE &&
                        leggings != null && leggings.getType() == Material.CHAINMAIL_LEGGINGS &&
                        boots != null && boots.getType() == Material.CHAINMAIL_BOOTS) {
                        fullChainmail = true;
                    }
                    
                    if (fullChainmail) {
                        currentRadiation = 0.0;
                        radiationLevels.put(uuid, currentRadiation);
                        continue;
                    }
                    
                    // Проверяем, находится ли игрок в радиационной зоне
                    boolean inZone = false;
                    if (radiationActive) {
                        Location loc = player.getLocation();
                        for (RadiationZone zone : zones.values()) {
                            if (zone.getWorld().equals(loc.getWorld().getName()) && zone.contains(loc)) {
                                inZone = true;
                                break;
                            }
                        }
                    }
                    
                    // Обновляем уровень радиации
                    if (inZone) {
                        currentRadiation += accumulationRate;
                    } else {
                        currentRadiation -= decayRate;
                        if (currentRadiation < 0) currentRadiation = 0;
                    }
                    
                    // Определяем дискретный уровень радиации
                    int effectLevel;
                    if (currentRadiation >= level5Threshold) {
                        effectLevel = 5;
                    } else if (currentRadiation >= level4Threshold) {
                        effectLevel = 4;
                    } else if (currentRadiation >= level3Threshold) {
                        effectLevel = 3;
                    } else if (currentRadiation >= level2Threshold) {
                        effectLevel = 2;
                    } else {
                        effectLevel = 1;
                    }
                    
                    // Применяем эффекты:
                    // Уровень 2: замедление I
                    // Уровень 3: замедление II
                    // Уровень 4: наносится урон x1 (с замедлением I)
                    // Уровень 5: наносится урон x2 и замедление (амплификация 2)
                    int duration = 40; // 2 секунды
                    if (effectLevel == 2) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, false, false, true));
                    } else if (effectLevel == 3) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, false, true));
                    } else if (effectLevel == 4) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, false, true));
                        applyBypassArmorDamage(player, baseDamage);
                    } else if (effectLevel == 5) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, true));
                        applyBypassArmorDamage(player, baseDamage * 2);
                    } else {
                        // Уровень 1: убираем эффект замедления, если он есть
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    
                    // Спавним партиклы: используем Particle.ITEM_SLIME (частицы, похожие на слизь)
                    int particleCount = (int) (currentRadiation * 5); // масштабируем число частиц
                    if (particleCount > 0) {
                        player.getWorld().spawnParticle(
                            Particle.ITEM_SLIME,
                            player.getLocation().add(0, 1, 0),
                            particleCount,
                            0.5, 0.5, 0.5,
                            0
                        );
                    }
                    
                    radiationLevels.put(uuid, currentRadiation);
                }
            }
        }.runTaskTimer(this, 20L, 20L);
        
        getLogger().info("RadiationPlugin включён!");
    }

    @Override
    public void onDisable() {
        saveZones();
        getLogger().info("RadiationPlugin выключён!");
    }
    
    // Геттер для зон
    public Map<String, RadiationZone> getZones() {
        return zones;
    }
    
    public boolean isRadiationActive() {
        return radiationActive;
    }
    
    public void setRadiationActive(boolean active) {
        this.radiationActive = active;
    }
    
    private void applyBypassArmorDamage(Player player, double damage) {
        try {
            String version = getServerVersion();
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object damageSource;
    
            // Для версий 1.18.2+
            if (version.startsWith("v1_18") || version.startsWith("v1_19") || version.startsWith("v1_20")) {
                Class<?> damageSourcesClass = Class.forName("net.minecraft.world.damagesource.DamageSources");
                Method getDamageSources = craftPlayer.getClass().getMethod("dk"); // getDamageSources()
                Object damageSources = getDamageSources.invoke(craftPlayer);
                Method starveMethod = damageSourcesClass.getMethod("m_269291_"); // starve()
                damageSource = starveMethod.invoke(damageSources);
            }
            // Для версий 1.17-1.18.1
            else if (version.startsWith("v1_17")) {
                Class<?> damageSourcesClass = Class.forName("net.minecraft.world.damagesource.DamageSources");
                Method getDamageSources = craftPlayer.getClass().getMethod("getDamageSources");
                Object damageSources = getDamageSources.invoke(craftPlayer);
                Method starveMethod = damageSourcesClass.getMethod("starve");
                damageSource = starveMethod.invoke(damageSources);
            }
            // Для версий 1.13-1.16
            else {
                Class<?> damageSourceClass = Class.forName("net.minecraft.server." + version + ".DamageSource");
                damageSource = damageSourceClass.getField("STARVE").get(null);
            }
    
            // Вызываем метод damage
            Method damageMethod = craftPlayer.getClass().getMethod("damage", damageSource.getClass(), float.class);
            damageMethod.invoke(craftPlayer, damageSource, (float) damage);
    
        } catch (Exception e) {
            // Резервный метод с улучшенной обработкой
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        player.damage(damage);
                        player.setHealth(Math.max(player.getHealth() - damage, 0.0));
                        player.playEffect(EntityEffect.HURT);
                    } catch (Exception ex) {
                        getLogger().severe("Failed to apply backup damage: " + ex.getMessage());
                    }
                }
            }.runTaskLater(this, 1L);
            getLogger().warning("Radiation damage error: " + e.getMessage());
        }
    }
    
    // Вспомогательный метод для получения версии сервера
    private String getServerVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }
    
    // Загрузка значений из config.yml
    private void loadConfigValues() {
        accumulationRate = getConfig().getDouble("accumulationRate", 0.5);
        decayRate = getConfig().getDouble("decayRate", 0.5);
        level2Threshold = getConfig().getDouble("level2Threshold", 6.0);
        level3Threshold = getConfig().getDouble("level3Threshold", 9.0);
        level4Threshold = getConfig().getDouble("level4Threshold", 15.0);
        level5Threshold = getConfig().getDouble("level5Threshold", 25.0);
        baseDamage = getConfig().getDouble("baseDamage", 2.0);
    }
    
    // Создание файла zones.yml, если его нет
    private void createZonesFile() {
        zonesFile = new File(getDataFolder(), "zones.yml");
        if (!zonesFile.exists()) {
            zonesFile.getParentFile().mkdirs();
            try {
                zonesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
    }

    private void createRadiationSensor() {
        radiationSensor = new ItemStack(Material.COMPASS);
        ItemMeta meta = radiationSensor.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Радиационный компас");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "ПКМ - проверить уровень радиации"
        ));
        
        // Добавляем эффект свечения: используем, например, INFINITY (любое скрытое зачарование)
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        
        radiationSensor.setItemMeta(meta);
    }    

    private void registerRecipe() {
        try {
            // Создаем уникальный ключ рецепта
            NamespacedKey key = new NamespacedKey(this, "radiation_sensor");
            // Результат рецепта – кастомный датчик (клонируем, чтобы избежать изменений оригинала)
            ItemStack result = radiationSensor.clone();
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            
            // Загружаем форму рецепта из конфигурации (если задана)
            List<String> shape = getConfig().getStringList("radiation-sensor.recipe.shape");
            if (shape.isEmpty()) {
                shape = Arrays.asList("GLG", "LIL", "GLG");
            }
            recipe.shape(shape.toArray(new String[0]));
            
            // Загружаем ингредиенты из конфигурации
            if (getConfig().isConfigurationSection("radiation-sensor.recipe.ingredients")) {
                for (String keyChar : getConfig().getConfigurationSection("radiation-sensor.recipe.ingredients").getKeys(false)) {
                    String materialName = getConfig().getString("radiation-sensor.recipe.ingredients." + keyChar);
                    Material mat = Material.getMaterial(materialName);
                    if (mat != null) {
                        recipe.setIngredient(keyChar.charAt(0), mat);
                    } else {
                        getLogger().warning("Не найден материал для ключа '" + keyChar + "': " + materialName);
                    }
                }
            } else {
                // Дефолтные ингредиенты
                recipe.setIngredient('G', Material.GLOWSTONE_DUST);
                recipe.setIngredient('L', Material.IRON_INGOT);
                recipe.setIngredient('I', Material.DAYLIGHT_DETECTOR);
            }
            
            // Для скрытия рецепта из книги рецептов задаем группу, которая не отображается
            recipe.setGroup("hidden");
            
            // Регистрируем рецепт
            Bukkit.addRecipe(recipe);
            getLogger().info("Рецепт датчика (с кастомным компасом) зарегистрирован.");
        } catch (Exception e) {
            getLogger().severe("Ошибка при регистрации рецепта: " + e.getMessage());
        }
    }
    
    // Слушатель для взаимодействия с датчиком (ПКМ)
    private class SensorListener implements Listener {
        @EventHandler
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || 
                event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    
                ItemStack item = event.getItem();
                if (item != null && item.isSimilar(radiationSensor)) {
                    Player player = event.getPlayer();
                    double level = radiationLevels.getOrDefault(
                        player.getUniqueId(), 0.0
                    );

                    ChatColor color;
                    if (level >= level5Threshold) {
                        color = ChatColor.DARK_RED;
                    } else if (level >= level4Threshold) {
                        color = ChatColor.RED;
                    } else if (level >= level3Threshold) {
                        color = ChatColor.YELLOW;
                    } else if (level >= level2Threshold) {
                        color = ChatColor.GREEN;
                    } else {
                        color = ChatColor.GREEN;
                    }
                    
                    String message = color + "☢ " + 
                        String.format("%.1f", level);
                    
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(message)
                    );
                }
            }
        }
    }
    
    // Загрузка зон из файла zones.yml
    public void loadZones() {
        if (zonesConfig.contains("zones")) {
            for (String zoneName : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
                String world = zonesConfig.getString("zones." + zoneName + ".world");
                double x1 = zonesConfig.getDouble("zones." + zoneName + ".x1");
                double y1 = zonesConfig.getDouble("zones." + zoneName + ".y1");
                double z1 = zonesConfig.getDouble("zones." + zoneName + ".z1");
                double x2 = zonesConfig.getDouble("zones." + zoneName + ".x2");
                double y2 = zonesConfig.getDouble("zones." + zoneName + ".y2");
                double z2 = zonesConfig.getDouble("zones." + zoneName + ".z2");
                RadiationZone zone = new RadiationZone(zoneName, world, x1, y1, z1, x2, y2, z2);
                zones.put(zoneName, zone);
            }
        }
    }
    
    // Сохранение зон в файл zones.yml
    public void saveZones() {
        for (String zoneName : zones.keySet()) {
            RadiationZone zone = zones.get(zoneName);
            zonesConfig.set("zones." + zoneName + ".world", zone.getWorld());
            zonesConfig.set("zones." + zoneName + ".x1", zone.getX1());
            zonesConfig.set("zones." + zoneName + ".y1", zone.getY1());
            zonesConfig.set("zones." + zoneName + ".z1", zone.getZ1());
            zonesConfig.set("zones." + zoneName + ".x2", zone.getX2());
            zonesConfig.set("zones." + zoneName + ".y2", zone.getY2());
            zonesConfig.set("zones." + zoneName + ".z2", zone.getZ2());
        }
        try {
            zonesConfig.save(zonesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Геттер для кастомного датчика
    public ItemStack getRadiationSensor() {
        return radiationSensor;
    }
}
