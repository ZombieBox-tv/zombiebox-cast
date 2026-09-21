package io.github.diegog0477.zombiebox.cast

data class Receiver(val id: String,val name: String)
data class CastGrant(val id: String,val host: String,val port: Int,val path: String,val user: String,val token: String)
data class CastState(val receivers: List<Receiver> = emptyList(),val selected: String="",val busy: Boolean=false,val failed: Boolean=false,val grant: CastGrant?=null)
interface CastRepository {
    fun pair(address: String,code: String)
    fun receivers(): List<Receiver>
    fun create(receiver: String): CastGrant
    fun stop(id: String)
}
class CastViewModel(private val repository: CastRepository,private val execute: (() -> Unit)->Unit,private val deliver:(()->Unit)->Unit) {
    var state=CastState();private set
    var observer:((CastState)->Unit)?=null
    private val gate=Any()
    private var closed=false
    private var pending:CastGrant?=null
    fun select(id: String) { if(!state.busy && !closed) { state=state.copy(selected=id);observer?.invoke(state) } }
    fun connect(address: String,code: String) = work { previous -> repository.pair(address,code);previous.copy(receivers=repository.receivers(),selected="") }
    fun refresh()=work { previous -> val receivers=repository.receivers();previous.copy(receivers=receivers,selected=previous.selected.takeIf { id -> receivers.any { it.id==id } } ?: "") }
    fun start() { if(state.selected.isNotEmpty()) work { previous -> previous.copy(grant=repository.create(previous.selected)) } }
    fun consumeGrant() { synchronized(gate) { pending=null;state=state.copy(grant=null) } }
    private fun discard(grant:CastGrant?) { grant?.let { try { repository.stop(it.id) } catch(_:Exception){} } }
    private fun work(task:(CastState)->CastState) {
        if(closed || state.busy)return
        val previous=state
        state=state.copy(busy=true,failed=false);observer?.invoke(state)
        execute {
            val next=try { task(previous).copy(busy=false,failed=false) } catch(_:Exception) { previous.copy(busy=false,failed=true) }
            val abandoned=synchronized(gate) {
                if(closed) true else {
                    pending=next.grant
                    deliver { synchronized(gate) { if(!closed) { state=next;observer?.invoke(state) } } }
                    false
                }
            }
            if(abandoned)discard(next.grant)
        }
    }
    fun close() {
        val abandoned=synchronized(gate) { closed=true;observer=null;pending.also{pending=null} }
        if(abandoned!=null)execute { discard(abandoned) }
    }
}
