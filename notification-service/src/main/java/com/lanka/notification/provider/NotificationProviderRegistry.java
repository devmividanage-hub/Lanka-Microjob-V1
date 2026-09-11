package com.lanka.notification.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Picks the provider for a channel, preferring a real gateway over a simulated one. */
@Component
public class NotificationProviderRegistry {
    private final Map<String, List<NotificationProvider>> byChannel;
    private final NotificationProvider fallback;

    public NotificationProviderRegistry(List<NotificationProvider> providers) {
        this.byChannel = providers.stream()
                .collect(Collectors.groupingBy(provider -> provider.channel().toUpperCase(Locale.ROOT)));
        this.fallback = providers.stream()
                .filter(provider -> "EMAIL".equals(provider.channel()))
                .findFirst()
                .orElse(providers.isEmpty() ? null : providers.get(0));
    }

    public NotificationProvider forChannel(String channel) {
        String key = channel == null || channel.isBlank() ? "EMAIL" : channel.trim().toUpperCase(Locale.ROOT);
        List<NotificationProvider> candidates = byChannel.getOrDefault(key, List.of());
        return candidates.stream()
                .filter(NotificationProvider::isRealDelivery)
                .findFirst()
                .orElseGet(() -> candidates.isEmpty() ? fallback : candidates.get(0));
    }

    public Map<String, String> describe() {
        return byChannel.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(NotificationProvider::name)
                                .collect(Collectors.joining(", "))));
    }
}
