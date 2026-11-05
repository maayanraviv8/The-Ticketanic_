package application.dto;

import domain.Aggregates.member.Member;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {
    private String username;
    private String email;

    public static MemberDTO fromEntity(Member member) {
        if (member == null) return null;
        return MemberDTO.builder()
                .username(member.getUsername())
                .email(member.getEmail())
                .build();
    }
}
