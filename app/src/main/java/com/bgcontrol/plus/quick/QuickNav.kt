package com.bgcontrol.plus.quick

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pedido de navegação vindo de fora da interface — hoje, do toque longo na
 * bolha flutuante.
 *
 * A bolha vive em um serviço e não conhece a árvore de telas do Compose. Em vez
 * de um sistema de rotas só para isso, ela liga esta bandeira e a tela de
 * Configurações a consome ao abrir, indo direto para as opções da bolha.
 */
object QuickNav {

    /** True quando alguém pediu para abrir as configurações da bolha. */
    val abrirConfiguracoesBolha = MutableStateFlow(false)

    fun pedirConfiguracoesBolha() {
        abrirConfiguracoesBolha.value = true
    }

    fun consumir() {
        abrirConfiguracoesBolha.value = false
    }

    /** Extra usado no Intent que acorda a MainActivity a partir da bolha. */
    const val EXTRA_ABRIR_BOLHA = "com.bgcontrol.plus.ABRIR_CONFIG_BOLHA"
}
