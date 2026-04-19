package com.aau.server.model

import com.aau.saboteur.model.TunnelCard

data class CardDistributionResult(
    val hands: Map<String, List<TunnelCard>>,
    val drawPile: List<TunnelCard>,
    val goalCards: List<TunnelCard>,
    val startCard: TunnelCard
)
