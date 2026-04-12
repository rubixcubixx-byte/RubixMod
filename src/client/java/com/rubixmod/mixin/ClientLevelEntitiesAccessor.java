package com.rubixmod.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the protected {@code getEntities()} method on ClientLevel so
 * LittlefootTracker can iterate ALL loaded entities, not just the
 * frustum-culled set returned by {@code entitiesForRendering()}.
 */
@Mixin(ClientLevel.class)
public interface ClientLevelEntitiesAccessor {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> rubix_getEntityGetter();
}
