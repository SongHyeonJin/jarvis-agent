package com.jarvis.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hellos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hello {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String message = "Hello, World!";
}
