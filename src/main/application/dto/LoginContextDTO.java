package application.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Post-login context returned from {@code MemberService.login}: the
 * authenticated member profile and JWT token. Company roles and admin status
 * are resolved by the presenter (via {@code RoleService}/{@code AdminService})
 * onto {@code CurrentUser}, not carried here.
 */
@Value
@Builder
public class LoginContextDTO {
    MemberDTO member;
    String token;
}
