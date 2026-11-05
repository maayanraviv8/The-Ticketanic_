package application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDTO {
    public String managerId;
    public String productionCompanyId;
    public int totalTicketsSold;
    public double totalRevenue;
    public List<String> includedManagerIds;
}
