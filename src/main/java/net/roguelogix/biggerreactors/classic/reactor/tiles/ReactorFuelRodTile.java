package net.roguelogix.biggerreactors.classic.reactor.tiles;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.roguelogix.phosphophyllite.registry.RegisterTileEntity;
import net.roguelogix.phosphophyllite.registry.TileSupplier;

import javax.annotation.Nonnull;

@RegisterTileEntity(name = "reactor_fuel_rod")
public class ReactorFuelRodTile extends ReactorBaseTile {
    
    @RegisterTileEntity.Type
    public static TileEntityType<?> TYPE;
    
    @RegisterTileEntity.Supplier
    public static final TileSupplier SUPPLIER = ReactorFuelRodTile::new;
    
    public ReactorFuelRodTile() {
        super(TYPE);
    }
    
    public long lastCheckedTick = 0;
    
    public long fuel = 0;
    public long waste = 0;
    
    @Override
    protected void readNBT(@Nonnull CompoundNBT compound) {
        super.readNBT(compound);
        fuel = compound.getLong("fuel");
        waste = compound.getLong("waste");
    }
    
    @Override
    @Nonnull
    protected CompoundNBT writeNBT() {
        CompoundNBT compound = super.writeNBT();
        compound.putLong("fuel", fuel);
        compound.putLong("waste", waste);
        return compound;
    }
}
