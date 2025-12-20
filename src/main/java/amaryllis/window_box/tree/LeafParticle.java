package amaryllis.window_box.tree;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CherryParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LeafParticle extends CherryParticle {
    public LeafParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
        super(level, x, y, z, spriteSet);
    }

    public static ParticleProvider<SimpleParticleType> Factory(SpriteSet spriteSet) {
        return (options, level, x, y, z, speedX, speedY, speedZ)
                -> new LeafParticle(level, x, y, z, spriteSet);
    }
}
