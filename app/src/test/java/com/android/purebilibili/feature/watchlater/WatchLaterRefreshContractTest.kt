package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.core.refresh.WatchLaterRefreshBus
import kotlin.test.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class WatchLaterRefreshContractTest {
    @Test
    fun watchLaterRefreshSignal_emitsForActiveCollectors() = runTest {
        val events = Channel<Unit>(capacity = 1)
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            WatchLaterRefreshBus.changes.collect {
                events.send(Unit)
                cancel()
            }
        }

        WatchLaterRefreshBus.notifyChanged()

        events.receive()
        collector.cancel()
    }

}
