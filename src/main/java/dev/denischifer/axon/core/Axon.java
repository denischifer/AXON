package dev.denischifer.axon.core;

import dev.denischifer.axon.data.SpatialGrid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class Axon implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("========================================");
        System.out.println("   AXON ENGINE STARTING...");
        System.out.println("========================================");

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.world != null) {
                SpatialGrid.createSnapshot();
            }
        });
    }
}