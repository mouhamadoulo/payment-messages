package com.bank.paymentmessages.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(name="payment_messages",
        indexes = {
                @Index(name="idx_message_reference", columnList="reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true)
    private String messageId;

    private String reference;

    private String status;

    @Column(columnDefinition="TEXT")
    private String payload;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;


}