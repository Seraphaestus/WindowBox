package amaryllis.window_box;

import amaryllis.window_box.flower.functional.WitchPupil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel INSTANCE;

    private static int id = 0;

    public static void register() {
        INSTANCE = NetworkRegistry.ChannelBuilder.named(WindowBox.RL("main"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION).clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals).simpleChannel();

        // Client -> Server Packets
        register(WitchPupil.ControlsPacket.class, WitchPupil.ControlsPacket::encode, WitchPupil.ControlsPacket::decode, WitchPupil.ControlsPacket::handle);
        // Server -> Client Packets
        //TODO(remove): register(WitchPupil.ReloadChunksPacket.class, WitchPupil.ReloadChunksPacket::encode, WitchPupil.ReloadChunksPacket::decode, WitchPupil.ReloadChunksPacket::handle);
    }

    private static <M> void register(Class<M> messageType, BiConsumer<M, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, M> decoder,
                                     BiConsumer<M, Supplier<NetworkEvent.Context>> messageConsumer) {
        INSTANCE.registerMessage(id++, messageType, encoder, decoder, messageConsumer);
    }

}
