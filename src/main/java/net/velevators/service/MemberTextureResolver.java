package net.velevators.service;

import net.velevators.model.MemberCandidateView;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface MemberTextureResolver {
    CompletableFuture<List<MemberCandidateView>> resolveCandidates(List<MemberCandidateView> candidates);
}
