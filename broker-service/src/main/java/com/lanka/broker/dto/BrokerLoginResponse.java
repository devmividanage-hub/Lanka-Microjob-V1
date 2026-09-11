package com.lanka.broker.dto;

/**
 * Successful broker login. The {@code token} is a JWT with role BROKER, {@code uid} = broker primary
 * key and a {@code brokerId} claim; the broker dashboard endpoints authorise against it.
 */
public record BrokerLoginResponse(String token, Long id, String brokerId, String name, String email, String phone,
                                  String district, String city, String status) {
}
