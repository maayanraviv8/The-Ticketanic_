package domain.policies.discount;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.*;



/**
 * IDiscountRule - Composite interface for discount policies
 * Returns a Double: the discount percentage (0-100)
 */


@Entity
@Table(name = "discount_rules")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discount_type", discriminatorType = DiscriminatorType.STRING)
public abstract class IDiscountRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * Evaluates and returns the discount percentage
     * @param context The evaluation context
     * @return Discount percentage (0-100). Returns 0 if discount doesn't apply
     */
    public abstract double evaluate(CustomerDetails context);

    /**
     * Returns a human-readable description of this rule
     */
    public abstract String getDescription();

    public abstract DiscountPolicyDTO toDTO();
}

