package net.roguelogix.biggerreactors.registries;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.roguelogix.biggerreactors.BiggerReactors;
import net.roguelogix.biggerreactors.Config;
import net.roguelogix.phosphophyllite.config.ConfigValue;
import net.roguelogix.phosphophyllite.data.DatapackLoader;
import net.roguelogix.phosphophyllite.networking.SimplePhosChannel;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.robn.ROBNObject;
import net.roguelogix.phosphophyllite.serialization.PhosphophylliteCompound;
import org.apache.commons.lang3.NotImplementedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;


public class ReactorModeratorRegistry {
    
    public interface IModeratorProperties extends ROBNObject {
        double absorption();
        
        double heatEfficiency();
        
        double moderation();
        
        double heatConductivity();
        
        @Override
        default Map<String, Object> toROBNMap() {
            final Map<String, Object> map = new HashMap<>();
            map.put("absorption", absorption());
            map.put("heatEfficiency", heatEfficiency());
            map.put("moderation", moderation());
            map.put("heatConductivity", heatConductivity());
            return map;
        }
        
        @Override
        default void fromROBNMap(Map<String, Object> map) {
            throw new NotImplementedException("");
        }
    }
    
    public static class ModeratorProperties implements IModeratorProperties, ROBNObject {
        
        public static final ModeratorProperties EMPTY_MODERATOR = new ModeratorProperties(0, 0, 1, 0);
        
        public final double absorption;
        public final double heatEfficiency;
        public final double moderation;
        public final double heatConductivity;
        
        public ModeratorProperties(double absorption, double heatEfficiency, double moderation, double heatConductivity) {
            this.absorption = absorption;
            this.heatEfficiency = heatEfficiency;
            this.moderation = moderation;
            this.heatConductivity = heatConductivity;
        }
        
        public ModeratorProperties(IModeratorProperties properties) {
            this(properties.absorption(), properties.heatEfficiency(), properties.moderation(), properties.heatConductivity());
        }
        
        @Override
        public double absorption() {
            return absorption;
        }
        
        @Override
        public double heatEfficiency() {
            return heatEfficiency;
        }
        
        @Override
        public double moderation() {
            return moderation;
        }
        
        @Override
        public double heatConductivity() {
            return heatConductivity;
        }
        
        
    }
    
    private final static HashMap<Block, ModeratorProperties> registry = new HashMap<>();
    
    public static boolean isBlockAllowed(Block block) {
        return registry.containsKey(block);
    }
    
    public static ModeratorProperties blockModeratorProperties(Block block) {
        return registry.get(block);
    }
    
    // TODO: unify these names across all registries
    private enum RegistryType {
        tag,
        registry,
        fluidtag,
        fluid
    }
    
    private static class ReactorModeratorJsonData {
    
        @ConfigValue
        RegistryType type = RegistryType.tag;
    
        @ConfigValue
        ResourceLocation location = new ResourceLocation("dirt");
    
        @ConfigValue(range = "[0, 1]")
        double absorption;
    
        @ConfigValue(range = "[0, 1]")
        double efficiency;
    
        @ConfigValue(range = "[1,)")
        double moderation;
    
        @ConfigValue(range = "[0,)")
        double conductivity;
    }
    
    private static final DatapackLoader<ReactorModeratorJsonData> dataLoader = new DatapackLoader<>(ReactorModeratorJsonData::new);
    
    public static void loadRegistry() {
        BiggerReactors.LOGGER.info("Loading reactor moderators");
        registry.clear();
        
        List<ReactorModeratorJsonData> data = dataLoader.loadAll(new ResourceLocation("biggerreactors:ebcr/moderators"));
        BiggerReactors.LOGGER.info("Loaded " + data.size() + " moderator data entries");
        
        for (ReactorModeratorJsonData moderatorData : data) {
            
            ModeratorProperties properties = new ModeratorProperties(moderatorData.absorption, moderatorData.efficiency, moderatorData.moderation, moderatorData.conductivity);
    
            switch (moderatorData.type) {
                case tag -> {
                    var blockTagOptional = BuiltInRegistries.BLOCK.getTag(TagKey.create(BuiltInRegistries.BLOCK.key(), moderatorData.location));
                    blockTagOptional.ifPresent(holders -> holders.forEach(blockHolder -> {
                        var element = blockHolder.value();
                        registry.put(element, properties);
                        BiggerReactors.LOGGER.debug("Loaded moderator " + ForgeRegistries.BLOCKS.getKey(element));
                    }));
                }
                case registry -> {
                    // cant check against air, because air is a valid thing to load
                    if (ForgeRegistries.BLOCKS.containsKey(moderatorData.location)) {
                        registry.put(ForgeRegistries.BLOCKS.getValue(moderatorData.location), properties);
                        BiggerReactors.LOGGER.debug("Loaded moderator " + moderatorData.location);
                    }
                }
                case fluidtag -> {
                    var fluidTagOptional = BuiltInRegistries.FLUID.getTag(TagKey.create(BuiltInRegistries.FLUID.key(), moderatorData.location));
                    fluidTagOptional.ifPresent(holders -> holders.forEach(fluidHolder -> {
                        var element = fluidHolder.value();
                        Block elementBlock = element.defaultFluidState().createLegacyBlock().getBlock();
                        registry.put(elementBlock, properties);
                        BiggerReactors.LOGGER.debug("Loaded moderator " + ForgeRegistries.FLUIDS.getKey(element));
                    }));
                }
                case fluid -> {
                    // cant check against air, because air is a valid thing to load
                    if (ForgeRegistries.FLUIDS.containsKey(moderatorData.location)) {
                        Fluid fluid = ForgeRegistries.FLUIDS.getValue(moderatorData.location);
                        assert fluid != null;
                        Block block = fluid.defaultFluidState().createLegacyBlock().getBlock();
                        registry.put(block, properties);
                        BiggerReactors.LOGGER.debug("Loaded moderator " + moderatorData.location);
                    }
                }
            }
        }
        BiggerReactors.LOGGER.info("Loaded " + registry.size() + " moderator entries");
    }
    
    public static class Client {
        
        private static final SimplePhosChannel CHANNEL = new SimplePhosChannel(new ResourceLocation(BiggerReactors.modid, "moderator_sync_channel"), "0", Client::readSync);
        private static final ObjectOpenHashSet<Block> moderatorBlocks = new ObjectOpenHashSet<>();
        private static final Object2ObjectOpenHashMap<Block, ModeratorProperties> moderatorProperties = new Object2ObjectOpenHashMap<>();
        
        @OnModLoad
        private static void onModLoad() {
            MinecraftForge.EVENT_BUS.addListener(Client::datapackEvent);
            if (FMLEnvironment.dist.isClient()) {
                MinecraftForge.EVENT_BUS.addListener(Client::toolTipEvent);
            }
        }
        
        public static void datapackEvent(OnDatapackSyncEvent e) {
            final var player = e.getPlayer();
            if (player == null) {
                return;
            }
            
            if (BiggerReactors.LOG_DEBUG) {
                BiggerReactors.LOGGER.debug("Sending moderator list to player: " + player);
            }
            CHANNEL.sendToPlayer(player, writeSync());
        }
        
        private static PhosphophylliteCompound writeSync() {
            final var list = new ObjectArrayList<String>();
            final var propertiesList = new ObjectArrayList<DoubleArrayList>();
            for (final var value : registry.entrySet()) {
                final var location = ForgeRegistries.BLOCKS.getKey(value.getKey());
                if (location == null) {
                    continue;
                }
                list.add(location.toString());
                
                var properties = new DoubleArrayList();
                properties.add(value.getValue().absorption);
                properties.add(value.getValue().heatEfficiency);
                properties.add(value.getValue().moderation);
                properties.add(value.getValue().heatConductivity);
                propertiesList.add(properties);
            }
            final var compound = new PhosphophylliteCompound();
            compound.put("list", list);
            compound.put("propertiesList", propertiesList);
            return compound;
        }
        
        private static void readSync(PhosphophylliteCompound compound) {
            moderatorBlocks.clear();
            //noinspection unchecked
            final var list = (List<String>) compound.getList("list");
            //noinspection unchecked
            final var propertiesList = (List<DoubleArrayList>) compound.getList("propertiesList");
            if (BiggerReactors.LOG_DEBUG) {
                BiggerReactors.LOGGER.debug("Received moderator list from server with length of " + list.size());
            }
            for (int i = 0; i < list.size(); i++) {
                var blockLocation = list.get(i);
                var properties = propertiesList.get(i);
                final var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockLocation));
                if (block == null) {
                    return;
                }
                if (BiggerReactors.LOG_DEBUG) {
                    BiggerReactors.LOGGER.debug("Block " + block + " added as moderator on client");
                }
                moderatorBlocks.add(block);
                moderatorProperties.put(block, new ModeratorProperties(properties.getDouble(0), properties.getDouble(1), properties.getDouble(2), properties.getDouble(3)));
            }
        }
        
        public static void toolTipEvent(ItemTooltipEvent event) {
            final var item = event.getItemStack().getItem();
            if (item instanceof BlockItem blockItem) {
                if (!moderatorBlocks.contains(blockItem.getBlock())) {
                    return;
                }
            } else if (item instanceof BucketItem bucketItem) {
                final var fluidBlock = bucketItem.getFluid().defaultFluidState().createLegacyBlock().getBlock();
                if (fluidBlock.defaultBlockState().isAir() || !moderatorBlocks.contains(fluidBlock)) {
                    return;
                }
            } else {
                return;
            }
            if (Minecraft.getInstance().options.advancedItemTooltips || Config.CONFIG.AlwaysShowTooltips) {
                event.getToolTip().add(Component.translatable("tooltip.biggerreactors.is_a_moderator"));
            }
        }
        
        public static void forEach(BiConsumer<Block, IModeratorProperties> consumer) {
            moderatorProperties.forEach(consumer);
        }
    }
}
