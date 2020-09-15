package org.jgn

import net.corda.accounts.flows.GetAccountInfo
import net.corda.accounts.flows.GetAccountsFlow
import net.corda.accounts.flows.ReceiveStateForAccountFlow
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.jgn.flows.IssueAssetFlow
import org.jgn.flows.QueryAssetFlow
import org.jgn.flows.TransferAssetFlow
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * FlowTests class
 *
 */
class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode
    private lateinit var c: StartedMockNode

    /**
    * Initialize FlowTests
    *
    */
    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("org.jgn.contracts"),
                TestCordapp.findCordapp("org.jgn.flows"),
                TestCordapp.findCordapp("net.corda.accounts.service"),
                TestCordapp.findCordapp("net.corda.accounts.contracts"),
                TestCordapp.findCordapp("net.corda.accounts.flows")
        )))
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()
        listOf(a, b, c).forEach {
            it.registerInitiatedFlow(GetAccountInfo::class.java)
            it.registerInitiatedFlow(ReceiveStateForAccountFlow::class.java)
            it.registerInitiatedFlow(TransferAssetFlow.Acceptor::class.java)
        }
        network.startNodes()
    }

    /**
    * Stop Nodes
    *
    */
    @After
    fun tearDown() {
        network.stopNodes()
    }

    /**
    * Fail Flow without proper account
    *
    */
    @Test
    fun `flow failed without account`() {
        val future = a.startFlow(IssueAssetFlow.Initiator(100, "APL"))
        network.runNetwork()
        assertFailsWith<NoSuchElementException> { future.getOrThrow() }
    }

    /**
    * issue STO test
    *
    */
    @Test
    fun `issue STO with given account`() {
        a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount("TESTING_ACCOUNT")
        val future = a.startFlow(IssueAssetFlow.Initiator(100, "APL"))
        network.runNetwork()
        val result = future.getOrThrow()
        Assert.assertEquals(100, result.state.data.amount)
    }

    /**
    * transfer asset test
    *
    */
    @Test
    fun `asset transfer`() {
        val accountServiceA = a.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceB = b.services.cordaService(KeyManagementBackedAccountService::class.java)
        val accountServiceC = c.services.cordaService(KeyManagementBackedAccountService::class.java)
        val future1 = accountServiceA.createAccount("MOCK_TRUST1_ACCOUNT")
        val ta = future1.getOrThrow()
        val future2 = accountServiceB.createAccount("MOCK_JGN_ACCOUNT")
        val ga = future2.getOrThrow()
        var future3 = accountServiceC.createAccount("MOCK_USER1_ACCOUNT")
        val ua1 = future3.getOrThrow()
        future3 = accountServiceC.createAccount("MOCK_USER2_ACCOUNT")
        val ua2 = future3.getOrThrow()
        accountServiceA.shareAccountInfoWithParty(ta.state.data.accountId, ga.state.data.accountHost)
        accountServiceB.shareAccountInfoWithParty(ga.state.data.accountId, ta.state.data.accountHost)
        accountServiceB.shareAccountInfoWithParty(ga.state.data.accountId, ua1.state.data.accountHost)
        accountServiceC.shareAccountInfoWithParty(ua1.state.data.accountId, ga.state.data.accountHost)
        accountServiceC.shareAccountInfoWithParty(ua2.state.data.accountId, ga.state.data.accountHost)
        a.startFlow(IssueAssetFlow.Initiator(100, "APL"))
        network.runNetwork()
        a.startFlow(TransferAssetFlow.Initiator(70, "APL", ta.state.data.accountId.toString(), ga.state.data.accountId.toString()))
        network.runNetwork()
        val future4 = a.startFlow(QueryAssetFlow(ta.state.data.accountId.toString(), "APL"))
        network.runNetwork()
        val future5 = b.startFlow(QueryAssetFlow(ga.state.data.accountId.toString(), "APL"))
        network.runNetwork()
        var balance = future4.getOrThrow().map{it.state.data.amount}.sum()
        Assert.assertEquals(30, balance)
        balance = future5.getOrThrow().map{it.state.data.amount}.sum()
        Assert.assertEquals(70, balance)
    }

    /**
    * Get morethan 200 Accounts
    * fix paging for >200
    */
    @Test
    fun `can get 200 accounts, which larger than default page size`() {
        val accountId = "test_"
        for (i in 1..200) {
            a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount(accountId + i)

        }
        val future = a.startFlow(GetAccountsFlow(true))
        network.runNetwork()
        val result = future.getOrThrow()
        Assert.assertEquals(200, result.size)
    }

    /**
    * Get 200 Accounts by default
    */
    @Test
    fun `can get 200 STOs`() {
        val tokenName = "sto"
        a.services.cordaService(KeyManagementBackedAccountService::class.java).createAccount("TESTING_ACCOUNT")
        for (i in 1..200) {
            a.startFlow(IssueAssetFlow.Initiator(100, tokenName + i))
        }
        network.runNetwork()
        val future = a.startFlow(QueryAssetFlow(null, null))
        network.runNetwork()
        val result = future.getOrThrow()
        Assert.assertEquals(200, result.size)
    }
}
