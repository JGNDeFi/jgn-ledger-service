package org.jgn.flows

import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import org.jgn.contracts.AssetState

/**
 * Gets the unconsumed states of currency for the account
 *
 * @param accountId The UUID of account.
 * @param symbol The currency Symbol.
*/
@StartableByRPC
class QueryAssetFlow(
        private val accountId: String? = null,
        private val symbol: String? = null
) : FlowLogic<List<StateAndRef<AssetState>>>() {

    override fun call(): List<StateAndRef<AssetState>> {
        val states = mutableListOf<StateAndRef<AssetState>>()
        var pageNumber = DEFAULT_PAGE_NUM
        return if (accountId == null) {
            do {
                val results = serviceHub.vaultService.queryBy(AssetState::class.java, PageSpecification(pageNumber, DEFAULT_PAGE_SIZE))
                states.addAll(results.states)
                pageNumber++
            } while((pageNumber - 1)*DEFAULT_PAGE_SIZE <= results.totalStatesAvailable)
            states.toList()
        } else {
            val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
            val accountId = AccountUtils.getAccountByUUID(serviceHub, accountId).accountId
            val stos = accountService.ownedByAccountVaultQuery(accountId,
                        QueryCriteria.VaultQueryCriteria(
                                status = Vault.StateStatus.UNCONSUMED,
                                contractStateTypes = setOf(AssetState::class.java)
                        )
                ) as List<StateAndRef<AssetState>>
            if (symbol == null) {
                stos
            } else {
                stos.filter { it.state.data.symbol.equals(symbol) }
            }
        }
    }
}
