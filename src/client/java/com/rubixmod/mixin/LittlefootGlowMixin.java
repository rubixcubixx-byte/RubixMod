package com.rubixmod.mixin;

import com.rubixmod.entity.EntityGlowManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces entities tracked by {@link EntityGlowManager} (Littlefoot + active HUD mobs)
 * into Minecraft's built-in outline framebuffer pass, producing a shape-matched
 * outline visible through terrain.  The outline colour is supplied per-entity by
 * the manager.
 *
 * Works with Sodium — Sodium only replaces chunk/terrain rendering, not the
 * entity outline framebuffer pass.
 */
@Mixin(Entity.class)
public class LittlefootGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void rubix_entityGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (EntityGlowManager.isGlowing(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void rubix_entityGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        Integer color = EntityGlowManager.getColor(self.getId());
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
