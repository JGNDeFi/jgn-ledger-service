package org.jgn.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.util.*

/**
 * AssetState Class
 *
 */
@BelongsToContract(AssetContract::class)
data class AssetState(
        val amount: Int,
        val symbol: String,
        val ownerKey: PublicKey,
        val uuid: UUID
) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOfNotNull(ownerKey).map { AnonymousParty(it) }
}

/**
 * AssetContract Class
 *
 */
class AssetContract : Contract {
    companion object {
        @JvmStatic
        val ID = "org.jgn.contracts.AssetContract"
    }
    /**
    * Commands interface for Issue and Transfer
    *
    */
    interface Commands : CommandData {
        /** Issue Class */
        class Issue : Commands
        /** Transfer Class */
        class Transfer : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.Issue -> verifyIssue(tx, setOfSigners)
            is Commands.Transfer -> verifyTransfer(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyIssue(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val output = tx.outputsOfType<AssetState>().single()
        "No inputs should be consumed when issuing an AssetContract." using tx.inputStates.isEmpty()
//        "All of the participants must be signers." using (signers.containsAll(output.participants.map { it.owningKey }))
        "Amount must be non-negative." using (output.amount> 0)
    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val inputs  = tx.inputsOfType<AssetState>()
        val outputs = tx.outputsOfType<AssetState>()
        "1 or more inputs should be consumed when issuing an AssetContract." using inputs.isNotEmpty()
        outputs.forEach {
            "Amount must be non-negative." using (it.amount > 0)
        }
    }
}
