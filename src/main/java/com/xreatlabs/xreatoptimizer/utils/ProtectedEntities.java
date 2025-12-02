package com.xreatlabs.xreatoptimizer.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.HashSet;
import java.util.Set;

/**
 * Centralized utility for determining which entities should be protected from
 * optimization actions like removal, hibernation, or spawn blocking.
 * 
 * This class uses version-safe entity type detection to work across
 * Minecraft versions 1.8 through 1.21.10.
 */
public final class ProtectedEntities {
    
    private static final Set<EntityType> PROTECTED_TYPES = new HashSet<>();
    private static final Set<EntityType> PROJECTILE_TYPES = new HashSet<>();
    private static final Set<String> PROTECTED_TYPE_NAMES = new HashSet<>();
    private static final Set<String> PROJECTILE_TYPE_NAMES = new HashSet<>();
    
    static {
        // Initialize protected entity type names (version-independent)
        String[] protectedNames = {
            // Player-placed decorative/functional entities
            "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING", "LEASH_HITCH",
            "LEASH_KNOT", // 1.8 name
            
            // Vehicles
            "BOAT", "MINECART", "CHEST_BOAT",
            "MINECART_CHEST", "CHEST_MINECART",
            "MINECART_HOPPER", "HOPPER_MINECART",
            "MINECART_FURNACE", "FURNACE_MINECART",
            "MINECART_TNT", "TNT_MINECART",
            "MINECART_COMMAND", "COMMAND_BLOCK_MINECART", "MINECART_COMMAND_BLOCK",
            "MINECART_MOB_SPAWNER", "SPAWNER_MINECART",
            
            // Player-created entities
            "IRON_GOLEM", "SNOWMAN", "SNOW_GOLEM", "ENDER_CRYSTAL", "END_CRYSTAL",
            
            // Boss entities
            "WITHER", "ENDER_DRAGON", "ELDER_GUARDIAN", "WARDEN",
            
            // Important gameplay entities
            "VILLAGER", "ZOMBIE_VILLAGER", "WANDERING_TRADER", "TRADER_LLAMA", "ALLAY",
            
            // Tameable/owned entities
            "WOLF", "CAT", "OCELOT", "PARROT", "HORSE", "DONKEY", "MULE", "LLAMA",
            "FOX", "AXOLOTL", "CAMEL", "SNIFFER",
            
            // Farm animals
            "PIG", "COW", "SHEEP", "CHICKEN", "RABBIT",
            "MOOSHROOM", "MUSHROOM_COW",
            "GOAT", "BEE", "TURTLE", "FROG", "TADPOLE", "STRIDER", "PANDA", "POLAR_BEAR",
            
            // Items and XP
            "DROPPED_ITEM", "ITEM", "EXPERIENCE_ORB"
        };
        
        for (String name : protectedNames) {
            PROTECTED_TYPE_NAMES.add(name);
            tryAddEntityType(PROTECTED_TYPES, name);
        }
        
        // Initialize projectile type names
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
        // Utility class - no instantiation
    }
    
    /**
     * Tries to add an entity type by name, silently ignoring if it doesn't exist.
     * This allows compatibility across different Minecraft versions.
     */
    private static void tryAddEntityType(Set<EntityType> set, String name) {
        try {
            EntityType type = EntityType.valueOf(name);
            set.add(type);
        } catch (IllegalArgumentException ignored) {
            // Entity type doesn't exist in this version - that's OK
        }
    }
    
    /**
     * Checks if an entity type is protected from automatic removal/hibernation.
     * 
     * @param type The entity type to check
     * @return true if the entity type should be protected
     */
    public static boolean isProtectedType(EntityType type) {
        if (type == null) return false;
        
        // Check the set first (faster)
        if (PROTECTED_TYPES.contains(type)) {
            return true;
        }
        
        // Fallback to name check for edge cases
        return PROTECTED_TYPE_NAMES.contains(type.name());
    }
    
    /**
     * Checks if an entity type is a projectile that should never be blocked.
     * 
     * @param type The entity type to check
     * @return true if the entity type is a projectile
     */
    public static boolean isProjectile(EntityType type) {
        if (type == null) return false;
        
        // Check the set first (faster)
        if (PROJECTILE_TYPES.contains(type)) {
            return true;
        }
        
        // Fallback to name check
        return PROJECTILE_TYPE_NAMES.contains(type.name());
    }
    
    /**
     * Comprehensive check if a specific entity instance should be protected.
     * This checks:
     * - Entity type
     * - Custom name (player-named entities)
     * - Tamed status
     * - Passengers/vehicles
     * 
     * @param entity The entity to check
     * @return true if the entity should be protected from removal/hibernation
     */
    public static boolean isProtected(Entity entity) {
        if (entity == null) {
            return false;
        }
        
        // Players are always protected
        if (entity instanceof Player) {
            return true;
        }
        
        // Check if the entity type is protected
        if (isProtectedType(entity.getType())) {
            return true;
        }
        
        // Named entities are protected (player-named pets, etc.)
        if (entity.getCustomName() != null) {
            return true;
        }
        
        // Tamed entities are protected
        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return true;
        }
        
        // Entities with passengers are protected (player might be riding)
        try {
            if (!entity.getPassengers().isEmpty()) {
                return true;
            }
        } catch (NoSuchMethodError e) {
            // Pre-1.11 uses getPassenger() instead
            try {
                Entity passenger = (Entity) entity.getClass().getMethod("getPassenger").invoke(entity);
                if (passenger != null) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        
        // Entities being ridden are protected
        if (entity.getVehicle() != null) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if an entity can be safely removed without affecting gameplay.
     * This is the inverse of isProtected() but also allows projectiles
     * that have been in the world for a long time (stuck arrows, etc.)
     * 
     * @param entity The entity to check
     * @return true if the entity can be safely removed
     */
    public static boolean canSafelyRemove(Entity entity) {
        if (entity == null) {
            return false;
        }
        
        // Never remove protected entities
        if (isProtected(entity)) {
            return false;
        }
        
        // Never remove projectiles in flight (only stuck ones after long time)
        if (isProjectile(entity.getType())) {
            String typeName = entity.getType().name();
            // Only arrows can be cleaned up, and only if there are many
            return typeName.equals("ARROW") || typeName.equals("SPECTRAL_ARROW");
        }
        
        return true;
    }
    
    /**
     * Gets the set of protected entity types for the current server version.
     * @return Set of protected entity types
     */
    public static Set<EntityType> getProtectedTypes() {
        return new HashSet<>(PROTECTED_TYPES);
    }
    
    /**
     * Gets the set of projectile entity types for the current server version.
     * @return Set of projectile entity types
     */
    public static Set<EntityType> getProjectileTypes() {
        return new HashSet<>(PROJECTILE_TYPES);
    }
}
