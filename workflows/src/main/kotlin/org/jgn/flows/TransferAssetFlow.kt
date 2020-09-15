package org.jgn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.accounts.service.KeyManagementBackedAccountService
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import org.jgn.contracts.AssetContract
import org.jgn.contracts.AssetState
import java.util.*


/**
 * TransferAssetFlow
 *
 */
object TransferAssetFlow {

    /**
    * TransferAssetFlow
    *
    * @param amount The value of amount
    * @param symbol The symbol of the currency
    * @param senderId The senderID
    * @param recipientId The recipientId
    */
    @StartableByRPC
    @StartableByService
    @InitiatingFlow
    class Initiator(
            private val amount: Int,
            private val symbol: String,
            private val senderId: String,
            private val recipientId: String
            ) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction to transfer asset.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            /**
             *
             *
             *
            */
            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val sender = AccountUtils.getAccountByUUID(serviceHub, senderId)
            val recipient = AccountUtils.getAccountByUUID(serviceHub, recipientId)
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val accountService = serviceHub.cordaService(KeyManagementBackedAccountService::class.java)
            val states = accountService.ownedByAccountVaultQuery(
                    sender.accountId,
                    QueryCriteria.VaultQueryCriteria(
                            status = Vault.StateStatus.UNCONSUMED,
                            contractStateTypes = setOf(AssetState::class.java)
                    )
            ) as List<StateAndRef<AssetState>>
            val inputStateAndRefs = states.filter { it.state.data.symbol == symbol && it.state.data.ownerKey.equals(sender.signingKey)}
            val currentAmount = inputStateAndRefs.map { it.state.data.amount }.sum()
            val residueState = AssetState(currentAmount - this.amount, this.symbol, sender.signingKey, UUID.randomUUID())
            val transferredState = AssetState(this.amount, this.symbol, recipient.signingKey, UUID.randomUUID())

            // JGN -> TRUSTEE
            progressTracker.currentStep = GENERATING_TRANSACTION
            val txCommand = Command(AssetContract.Commands.Transfer(), listOf(sender.accountHost.owningKey, recipient.accountHost.owningKey))
            val txBuilder = TransactionBuilder(notary)
            inputStateAndRefs.forEach { txBuilder.addInputState(it) }
            txBuilder.addOutputState(residueState)
                .addOutputState(transferredState)
                .addCommand(txCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val locallySignedTx = serviceHub.signInitialTransaction(txBuilder)

            if (sender.accountHost.equals(recipient.accountHost)) {
                progressTracker.currentStep = FINALISING_TRANSACTION
                return subFlow(FinalityFlow(locallySignedTx, listOf(), FINALISING_TRANSACTION.childProgressTracker()))
            }
            progressTracker.currentStep = GATHERING_SIGS
            val otherSession = initiateFlow(recipient.accountHost)
            val fullySignedTx = subFlow(CollectSignaturesFlow(locallySignedTx, setOf(otherSession), GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, setOf(otherSession), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    /**
     * Acceptor class
     * Verifies the transaction for validity
     *
    */
    @InitiatedBy(Initiator::class)
    class Acceptor(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val outputs = stx.tx.outputs
                    val output1 = outputs.first().data as AssetState
                    val output2 = outputs.last().data as AssetState
                    "This must be have 2 output." using (outputs.size == 2)
                    "Balance of source account must not be negative" using (output1.amount > 0)
                    "Balance of destination account must not be negative" using (output2.amount > 0)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSession, expectedTxId = txId))
        }
    }
}
