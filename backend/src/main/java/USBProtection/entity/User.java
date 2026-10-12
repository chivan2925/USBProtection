package USBProtection.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "User")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// id, username, passwordHash, createdAt.

public class User {
    @colume(name = "id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @colume(name = "username")
    private String username;

    @colume(name = "passwordHash")
    private String passwordHash;
    
    @colume(name = "createdAt")
    private java.time.LocalDateTime createdAt;
}
