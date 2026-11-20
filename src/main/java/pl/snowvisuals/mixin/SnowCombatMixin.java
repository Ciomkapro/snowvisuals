package pl.snowvisuals.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.snowvisuals.SnowQol;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class SnowCombatMixin {
    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void snow$recordDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        SnowQol.damaged(packet);
    }
}
