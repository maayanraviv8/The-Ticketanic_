package domain.Aggregates.order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDetails {
    @Column(name = "customer_name")
    private String name;

    @Column(name = "customer_email")
    private String email;

    @Column(name = "customer_phone")
    private String phone;

    @Column(name = "customer_age")
    private int age;

    @Column(name = "ticket_quantity")
    private int ticketQuantity;

    @Column(name = "customer_purchase_time")
    private LocalDateTime purchaseTime;

    @Column(name = "coupon_code")
    private String couponCode;
}

