package domain.policies.purchase;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.*;

/**
 * IPurchaseRule - Composite interface for purchase policies
 * Returns a Boolean: whether the purchase is allowed
 */
@Entity
@Table(name = "purchase_rules")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "rule_type", discriminatorType = DiscriminatorType.STRING)
public abstract class IPurchaseRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * Evaluates whether the purchase is allowed based on this rule
     * @param context The evaluation context containing purchase details
     * @return true if purchase is allowed, false otherwise
     */
    public abstract boolean evaluate(CustomerDetails context)throws Exception;

    /**
     * Returns a human-readable description of this rule
     */
    public abstract String getDescription();

    public abstract PurchasePolicyDTO toDTO();
}

