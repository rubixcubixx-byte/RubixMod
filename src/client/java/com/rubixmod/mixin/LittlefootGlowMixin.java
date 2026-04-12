package com.rubixmod.mixin;

import com.rubixmod.config.RubixConfig;
import com.rubixmod.mining.LittlefootTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces tracked Littlefoot entities to glow so Minecraft renders them with
 * the built-in outline shader (shape-matched, visible through terrain),
 * and overrides the outline colour to orange.
 */
@Mixin(Entity.class)
public class LittlefootGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void rubix_littlefootGlow(CallbackInfoReturnable<Boolean> cir) {
        if (!RubixConfig.get().littlefootTrackerEnabled) return;
        Entity self = (Entity) (Object) this;
        if (LittlefootTracker.isTracked(self)) {
            cir.setReturnValue(true);
        }
    }

    /** Returns orange (0xFF8C00) as the team/outline colour for tracked entities. */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void rubix_littlefootOutlineColor(CallbackInfoReturnable<Integer> cir) {
        if (!RubixConfig.get().littlefootTrackerEnabled) return;
        Entity self = (Entity) (Object) this;
        if (LittlefootTracker.isTracked(self)) {
            cir.setReturnValue(0xFF8C00); // orange
        }
    }
}
