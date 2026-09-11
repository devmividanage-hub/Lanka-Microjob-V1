package com.lanka.matching.dto;

import java.util.List;

public record BatchMatchResponse(int scored, List<MatchResponse> results) {
}
