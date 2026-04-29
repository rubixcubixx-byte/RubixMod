package com.rubixmod.mixin;

import com.rubixmod.entity.RubixEntityGlowAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors RFU's EntityMixin exactly:
 *  - targets net.minecraft.class_1297 (intermediary Entity name) so Loom doesn't remap it
 *  - injects into method_5851 (isCurrentlyGlowing) and method_22861 (getTeamColor) using remap=false
 *  - stores glow state as @Unique instance fields on every Entity
 */
@Mixin(targets = "net.minecraft.class_1297")
public abstract class LittlefootGlowMixin implements RubixEntityGlowAccess {

    @Unique private boolean rubix$glowing = false;
    @Unique private int rubix$color = 0xFFFFFF;
    @Unique private boolean rubix$throughWalls = false;

    @Override
    public boolean rubix$isGlowing() { return rubix$glowing; }

    @Override
    public int rubix$getGlowColor() { return rubix$color; }

    @Override
    public boolean rubix$isThroughWalls() { return rubix$throughWalls; }

    @Override
    public void rubix$setGlowing(boolean glowing, int color, boolean throughWalls) {
        this.rubix$glowing = glowing;
        this.rubix$color = color;
        this.rubix$throughWalls = throughWalls;
    }

    // Mirrors rfu$isCurrentlyGlowing — method_5851 = isCurrentlyGlowing()
    // Only fires for through-walls entities (Littlefoots); regular HUD mobs skip this.
    @Inject(method = "method_5851", at = @At("HEAD"), cancellable = true, remap = false)
    private void rubix_isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (rubix$glowing && rubix$throughWalls) {
            cir.setReturnValue(true);
        }
    }

    // Mirrors rfu$getTeamColor — method_22861 = getTeamColor()
    @Inject(method = "method_22861", at = @At("HEAD"), cancellable = true, remap = false)
    private void rubix_getTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (rubix$glowing && rubix$throughWalls) {
            cir.setReturnValue(rubix$color & 0xFFFFFF);
        }
    }
}
