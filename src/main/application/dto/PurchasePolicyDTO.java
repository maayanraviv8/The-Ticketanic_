package application.dto;

import domain.policies.purchase.IPurchaseRule;
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
public class PurchasePolicyDTO {
    public String type;
    public List<PurchasePolicyDTO> children;
    public Integer minTickets;
    public Integer maxTickets;
    public Integer minAge;

    public static PurchasePolicyDTO fromEntity(IPurchaseRule policy) {

        if (policy == null) {
            return null;
        }
        return policy.toDTO();
    }
}
