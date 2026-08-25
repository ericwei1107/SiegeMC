package woo.siegePlugin.state;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Versioned, durable representation of every slot in a player's inventory.
 */
public final class PlayerInventorySnapshot {

    private static final int MAGIC = 0x53474956; // SGIV
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_SECTION_BYTES = 16 * 1024 * 1024;

    private final byte[] data;

    private PlayerInventorySnapshot(byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
    }

    /**
     * Must be called on the server thread because it reads Bukkit inventory state.
     */
    public static PlayerInventorySnapshot capture(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");

        byte[] storage = ItemStack.serializeItemsAsBytes(inventory.getStorageContents());
        byte[] armor = ItemStack.serializeItemsAsBytes(inventory.getArmorContents());
        byte[] extra = ItemStack.serializeItemsAsBytes(inventory.getExtraContents());

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(inventory.getHeldItemSlot());
            writeSection(output, storage);
            writeSection(output, armor);
            writeSection(output, extra);
            output.flush();
            return new PlayerInventorySnapshot(bytes.toByteArray());
        } catch (IOException exception) {
            // ByteArrayOutputStream does not perform external I/O.
            throw new IllegalStateException("Could not encode player inventory", exception);
        }
    }

    public static PlayerInventorySnapshot fromBytes(byte[] data) {
        return new PlayerInventorySnapshot(Objects.requireNonNull(data, "data"));
    }

    public byte[] toBytes() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Must be called on the server thread because it changes Bukkit inventory state.
     */
    public void restore(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        DecodedInventory decoded = decode();

        ItemStack[] storage = ItemStack.deserializeItemsFromBytes(decoded.storage());
        ItemStack[] armor = ItemStack.deserializeItemsFromBytes(decoded.armor());
        ItemStack[] extra = ItemStack.deserializeItemsFromBytes(decoded.extra());

        requireSlotCount("storage", storage.length, inventory.getStorageContents().length);
        requireSlotCount("armor", armor.length, inventory.getArmorContents().length);
        requireSlotCount("extra", extra.length, inventory.getExtraContents().length);
        if (decoded.heldItemSlot() < 0 || decoded.heldItemSlot() > 8) {
            throw new IllegalArgumentException("Stored held-item slot is outside the hotbar");
        }

        clear(inventory);
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        inventory.setExtraContents(extra);
        inventory.setHeldItemSlot(decoded.heldItemSlot());
    }

    public static void clear(PlayerInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
        inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        inventory.setHeldItemSlot(0);
    }

    private DecodedInventory decode() {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Stored inventory has an invalid header");
            }
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported stored inventory format: " + version);
            }

            int heldItemSlot = input.readInt();
            byte[] storage = readSection(input);
            byte[] armor = readSection(input);
            byte[] extra = readSection(input);
            if (input.available() != 0) {
                throw new IllegalArgumentException("Stored inventory contains trailing data");
            }
            return new DecodedInventory(heldItemSlot, storage, armor, extra);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stored inventory is truncated", exception);
        }
    }

    private static void writeSection(DataOutputStream output, byte[] section) throws IOException {
        output.writeInt(section.length);
        output.write(section);
    }

    private static byte[] readSection(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_SECTION_BYTES || length > input.available()) {
            throw new IllegalArgumentException("Stored inventory section has an invalid size");
        }
        return input.readNBytes(length);
    }

    private static void requireSlotCount(String section, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Stored " + section + " inventory has " + actual
                            + " slots, but this server expects " + expected
            );
        }
    }

    private record DecodedInventory(
            int heldItemSlot,
            byte[] storage,
            byte[] armor,
            byte[] extra
    ) {
    }
}
