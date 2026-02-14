package com.xreatlabs.xreatoptimizer.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.HashSet;
import java.util.Set;

public final class ProtectedEntities {
    
    private static final Set<EntityType> PROTECTED_TYPES = new HashSet<>();
    private static final Set<EntityType> PROJECTILE_TYPES = new HashSet<>();
    private static final Set<String> PROTECTED_TYPE_NAMES = new HashSet<>();
    private static final Set<String> PROJECTILE_TYPE_NAMES = new HashSet<>();
    
    static {
        String[] protectedNames = {
            "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING", "LEASH_HITCH",
            "LEASH_KNOT",
            
            "BOAT", "MINECART", "CHEST_BOAT",
            "MINECART_CHEST", "CHEST_MINECART",
            "MINECART_HOPPER", "HOPPER_MINECART",
            "MINECART_FURNACE", "FURNACE_MINECART",
            "MINECART_TNT", "TNT_MINECART",
            "MINECART_COMMAND", "COMMAND_BLOCK_MINECART", "MINECART_COMMAND_BLOCK",
            "MINECART_MOB_SPAWNER", "SPAWNER_MINECART",
            
            "IRON_GOLEM", "SNOWMAN", "SNOW_GOLEM", "ENDER_CRYSTAL", "END_CRYSTAL",
            
            "WITHER", "ENDER_DRAGON", "ELDER_GUARDIAN", "WARDEN",
            
            "VILLAGER", "ZOMBIE_VILLAGER", "WANDERING_TRADER", "TRADER_LLAMA", "ALLAY",
            
            "WOLF", "CAT", "OCELOT", "PARROT", "HORSE", "DONKEY", "MULE", "LLAMA",
            "FOX", "AXOLOTL", "CAMEL", "SNIFFER",
            
            "PIG", "COW", "SHEEP", "CHICKEN", "RABBIT",
            "MOOSHROOM", "MUSHROOM_COW",
            "GOAT", "BEE", "TURTLE", "FROG", "TADPOLE", "STRIDER", "PANDA", "POLAR_BEAR",
            
            "DROPPED_ITEM", "ITEM", "EXPERIENCE_ORB"
        };
        
        for (String name : protectedNames) {
            PROTECTED_TYPE_NAMES.add(name);
            tryAddEntityType(PROTECTED_TYPES, name);
        }
        
        String[] projectileNames = {
            "ARROW", "SPECTRAL_ARROW", "TRIDENT", "SNOWBALL", "EGG", "ENDER_PEARL",
            "FIREBALL", "SMALL_FIREBALL", "DRAGON_FIREBALL", "WITHER_SKULL",
            "FIREWORK", "FIREWORK_ROCKET",
            "FISHING_HOOK", "FISHING_BOBBER",
            "LLAMA_SPIT", "SHULKER_BULLET", "WIND_CHARGE",
            "THROWN_EXP_BOTTLE", "EXPERIENCE_BOTTLE", "EXP_BOTTLE",
            "SPLASH_POTION", "POTION", "THROWN_POTION",
            "LINGERING_POTION",
            "EYE_OF_ENDER", "ENDER_SIGNAL"
        };
        
        for (String name : projectileNames) {
            PROJECTILE_TYPE_NAMES.add(name);
            tryAddEntityType(PROJECTILE_TYPES, name);
        }
    }
    
    private ProtectedEntities() {
    }
    
    private static void tryAddEntityType(Set<EntityType> set, String name) {
        try {
            EntityType type = EntityType.valueOf(name);
            set.add(type);
        } catch (IllegalArgumentException ignored) {
        }
    }
    
    public static boolean isProtectedType(EntityType type) {
        if (type == null) return false;
        
        if (PROTECTED_TYPES.contains(type)) {
            return true;
        }
        
        return PROTECTED_TYPE_NAMES.contains(type.name());
    }
    
    public static boolean isProjectile(EntityType type) {
        if (type == null) return false;
        
        if (PROJECTILE_TYPES.contains(type)) {
            return true;
        }
        
        return PROJECTILE_TYPE_NAMES.contains(type.name());
    }
    
    /** Check if entity should be protected */
    public static boolean isProtected(Entity entity) {
        if (entity == null) {
            return false;
        }
        
        if (entity instanceof Player) {
            return true;
        }
        
        if (isProtectedType(entity.getType())) {
            return true;
        }
        
        if (entity.getCustomName() != null) {
            return true;
        }
        
        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return true;
        }
        
        try {
            if (!entity.getPassengers().isEmpty()) {
                return true;
            }
        } catch (NoSuchMethodError e) {
            try {
                Entity passenger = (Entity) entity.getClass().getMethod("getPassenger").invoke(entity);
                if (passenger != null) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        
        if (entity.getVehicle() != null) {
            return true;
        }
        
        return false;
    }
    
    /** Check if entity can be safely removed */
    public static boolean canSafelyRemove(Entity entity) {
        if (entity == null) {
            return false;
        }
        
        if (isProtected(entity)) {
            return false;
        }
        
        if (isProjectile(entity.getType())) {
            String typeName = entity.getType().name();
            return typeName.equals("ARROW") || typeName.equals("SPECTRAL_ARROW");
        }
        
        return true;
    }
    
    public static Set<EntityType> getProtectedTypes() {
        return new HashSet<>(PROTECTED_TYPES);
    }
    
    public static Set<EntityType> getProjectileTypes() {
        return new HashSet<>(PROJECTILE_TYPES);
    }
}
