package lab8.port;

public interface InventoryPort {
    boolean reserve(String sku, int qty);

    void release(String sku, int qty);
}
