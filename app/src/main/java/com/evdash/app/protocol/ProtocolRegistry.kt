package com.evdash.app.protocol

/**
 * 协议注册表。所有支持的控制器在此集中注册。
 *
 * 新增控制器 = 新增一个 ControllerProtocol 实现类 + 在此列表中加一行。
 */
object ProtocolRegistry {

    val all: List<ControllerProtocol> = listOf(
        EvSimProtocol(),
        AptProtocol(),
        VotolProtocol(),
        BafangProtocol(),
        VescProtocol(),
        M365Protocol(),
    )

    fun find(id: String): ControllerProtocol? = all.find { it.id == id }
}
