package domain.Aggregates.productioncompany;

public enum Permissions {
    EVENT_EDITING, // includes inventory management and hall layout management
    CHANGE_PURCHASE_POLICY, CHANGE_DISCOUNT_POLICY,
    VIEW_PURCHASE_HISTORY,
    GENERATE_SALES_REPORT,
    APPOINT_OWNERS, APPOINT_MANAGERS,
    VIEW_MANAGEMENT_HIERARCHY,
    EDIT_VENUE_LAYOUT
}
