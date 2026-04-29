package com.rubixmod.mixin;

import com.rubixmod.entity.EntityGlowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires EntityGlowManager.onEntityAdded the instant any entity is added to the
 * client world — well before the next game tick — so tracked mobs get their
 * outline applied without a tick-delay.
 */
@Mixin(ClientLevel.class)
public class ClientLevelEntityLoadMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void rubix$onEntityAdded(Entity entity, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        EntityGlowManager.onEntityAdded(client, entity, (ClientLevel) (Object) this);
    }
}
