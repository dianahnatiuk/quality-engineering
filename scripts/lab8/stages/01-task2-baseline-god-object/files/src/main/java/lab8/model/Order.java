package lab8.model;

import java.util.List;

public record Order(String id, String email, String phone, Region region, boolean business,
                    List<OrderLine> lines, String coupon, String paymentToken) {

    public int itemCount() {
        int n = 0;
        for (OrderLine line : lines) n += line.qty();
        return n;
    }
}
