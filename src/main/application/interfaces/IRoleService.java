package application.interfaces;

import application.dto.RoleDTO;

import java.util.Map;

public interface IRoleService {
    void acceptAppointment(String token, String companyID, boolean accepted) throws Exception;
    Map<String, RoleDTO> getUserRoles(String memberID);
}
