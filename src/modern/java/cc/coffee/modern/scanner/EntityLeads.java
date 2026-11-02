package cc.coffee.modern.scanner;

import cc.coffee.modern.StorageIndex;
import java.util.*;

public final class EntityLeads {
    public record Observation(UUID id, StorageIndex.Position position, String reason) {}
    public record Lead(Observation observation, boolean live, long seenAt) {}
    private final Map<UUID, Lead> leads = new HashMap<>();
    public List<Lead> update(Collection<Observation> observations, Set<UUID> loaded, long now, boolean remember) {
        Map<UUID, Observation> matched = new HashMap<>();
        observations.forEach(observation -> matched.put(observation.id, observation));
        leads.entrySet().removeIf(entry -> !matched.containsKey(entry.getKey()) && (loaded.contains(entry.getKey()) || !remember));
        leads.replaceAll((id, old) -> !matched.containsKey(id) && old.live ? new Lead(old.observation, false, now) : old);
        List<Lead> fresh = new ArrayList<>();
        matched.forEach((id, observation) -> {
            var lead = new Lead(observation, true, now);
            if (leads.put(id, lead) == null) fresh.add(lead);
        });
        return fresh;
    }
    public void expire(long now, long lifetime, boolean remember) {
        leads.values().removeIf(lead -> !lead.live && (!remember || now - lead.seenAt >= lifetime));
    }
    public List<Lead> nearest(double x, double y, double z) {
        return leads.values().stream().sorted(Comparator.comparingDouble(lead -> lead.observation.position.distanceSquared(x, y, z))).toList();
    }
    public void clear() { leads.clear(); }
}
