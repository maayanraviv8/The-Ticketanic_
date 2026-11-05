package domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.*;
import jakarta.persistence.GenerationType;
import lombok.*;

/**
 * IDiscountCondition - Defines conditions under which a discount applies
 */
@Entity
@Table(name = "discount_conditions")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "condition_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
public abstract class IDiscountCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * Evaluates whether the condition is met
     */
    public abstract boolean isMet(CustomerDetails context);

    /**
     * Returns a human-readable description of the condition
     */
    public abstract String getDescription();

    public abstract void enrichDTO(DiscountPolicyDTO.DiscountPolicyDTOBuilder builder);
}

