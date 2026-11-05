package application.dto;

import domain.Aggregates.productioncompany.ProductionCompany;
public class ProductionCompanyDTO {
    public final String name;
    public final boolean isActive;

    public ProductionCompanyDTO(String name, boolean isActive) {
        this.name = name;
        this.isActive = isActive;
    }

    public static ProductionCompanyDTO fromEntity(ProductionCompany entity) {
        if (entity == null) return null;
        return new ProductionCompanyDTO(entity.getName(), entity.isActive());
    }
}
