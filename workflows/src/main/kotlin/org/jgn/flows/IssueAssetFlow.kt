package org.jgn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import org.jgn.contracts.AssetContract
import org.jgn.contracts.AssetState
import java.util.*

/**
 * IssueAssetFlow
 *
 */
object IssueAssetFlow {

    /**
    * Initiator Class for IssueAssetFlow
    *
    */
    @StartableByRPC
    class Initiator(
            private val amount: Int,
            private val symbol: String,
            private val uuid: UUID = UUID.randomUUID()
    ) : FlowLogic<StateAndRef<AssetState>>() {
        constructor(amount: Int, symbol: String) : this(amount, symbol, UUID.randomUUID())

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction for STO issuance.")
//            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying asset contract constraints")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            /**
             *
             */
            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
//                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): StateAndRef<AssetState> {
            progressTracker.currentStep = GENERATING_TRANSACTION
            val txBuilder = TransactionBuilder()
            val myAccount = AccountUtils.getMyAccounts(serviceHub).first().state.data
            txBuilder.notary = serviceHub.networkMapCache.notaryIdentities.first()
            txBuilder.addCommand(AssetContract.Commands.Issue(), serviceHub.myInfo.legalIdentities.first().owningKey)
            txBuilder.addOutputState(AssetState(amount, symbol, myAccount.signingKey, uuid))

//            progressTracker.currentStep = VERIFYING_TRANSACTION
//            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val signedTxLocally = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = FINALISING_TRANSACTION
            val finalizedTx = subFlow(FinalityFlow(signedTxLocally, listOf(), FINALISING_TRANSACTION.childProgressTracker()))
            return finalizedTx.coreTransaction.outRefsOfType(AssetState::class.java).single()
        }
    }
}
