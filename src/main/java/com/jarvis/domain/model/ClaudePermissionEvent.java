package com.jarvis.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "claude_permission_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudePermissionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @Lob
    private String message;

    @Column(length = 100)
    private String hookEventName;

    @Column(length = 200)
    private String sessionId;

    @Column(length = 200)
    private String toolName;

    private boolean acknowledged;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
