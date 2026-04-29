package com.rubixmod.mixin;

import com.rubixmod.entity.EntityGlowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

/**
 * Hooks Entity.setCustomName() so that the moment an ArmorStand receives its
 * name — whether at spawn or via a delayed metadata packet — we can immediately
 * identify and glow any nearby mob entities without waiting for the next tick.
 *
 * On Hypixel, custom mob ArmorStand nametags often arrive 3-4 seconds after
 * the mob entity itself, which is why ticking and entity-load hooks alone are
 * not fast enough.
 */
@Mixin(Entity.class)
public class EntityNameUpdateMixin {

    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void rubix$onCustomNameSet(@Nullable Component name, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof ArmorStand)) return;
        if (name == null || name.getString().isBlank()) return;

        Minecraft client = Minecraft.getInstance();
        if (!(client.level instanceof ClientLevel level)) return;
        if (client.player == null) return;

        EntityGlowManager.onArmorStandNameSet(client, self, level);
    }
}
