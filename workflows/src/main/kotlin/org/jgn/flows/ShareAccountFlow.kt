package org.jgn.flows

import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

/**
 * ShareAccountFlow class
 *
 */
@StartableByRPC
class ShareAccountFlow(
        private val accountUUID: String,
        private val node: Party) : FlowLogic<Unit>() {
    override fun call() {
        val accountId = AccountUtils.getAccountByUUID(serviceHub, accountUUID).accountId
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        accountService.shareAccountInfoWithParty(accountId, node)
    }
}
