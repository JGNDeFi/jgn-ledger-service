package org.jgn.flows

import net.corda.accounts.states.AccountInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC

/**
 * Gets Account by UUID
 *
 * @param accountId UUID of account.
 */
@StartableByRPC
class AccountQueryByIdFlow(private val accountId: String) : FlowLogic<AccountInfo>() {
    override fun call(): AccountInfo {
        return AccountUtils.getAccountByUUID(serviceHub, accountId)
    }
}
