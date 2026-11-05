package domain.Aggregates.admin;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "email")
public class Admin {

    @Id
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Version
    private long version = 0L;

    public Admin(String email) {
        this.email = email;
    }

}
