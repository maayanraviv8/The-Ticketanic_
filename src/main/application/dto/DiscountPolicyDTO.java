package application.dto;

import domain.policies.discount.IDiscountRule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountPolicyDTO {
    public String type;
    public double percentage;
    public List<DiscountPolicyDTO> children;
    public String couponCode;
    public LocalDateTime validFrom;
    public LocalDateTime validUntil;
    public Integer minTickets;
    public Integer maxTickets;


    public static DiscountPolicyDTO fromEntity(IDiscountRule policy) {
        if (policy == null) {
            return null;
        }
        return policy.toDTO();
    }
}
