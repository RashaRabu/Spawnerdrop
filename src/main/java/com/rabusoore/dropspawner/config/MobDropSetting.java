package com.rabusoore.dropspawner.config;

import org.bukkit.entity.EntityType;

/**
 * Record immutable untuk caching pengaturan drop per mob.
 */
public record MobDropSetting(boolean enabled, double chance, EntityType dropType) {}
