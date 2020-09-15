package org.jgn.flows

import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.accounts.states.AccountInfo
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub

/**
 * Account Utils
 *
 * Gets all Accounts and Account by UUID
 *
 */
object AccountUtils {
    fun getMyAccounts(serviceHub: ServiceHub): List<StateAndRef<AccountInfo>> {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        return accountService.myAccounts()
    }

    fun getAccountByUUID(serviceHub: ServiceHub, uuid: String): AccountInfo {
        val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
        val accounts = accountService.allAccounts().map{it.state.data}
        return accounts.filter { it.accountId.toString() == uuid }.single()
    }
}
