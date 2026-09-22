package mihon.app.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.metro.AppCoroutineScope
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML

@BindingContainer
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    @AppCoroutineScope
    fun providesAppCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesXML(): XML = XML.v1 {
        policy {
            ignoreUnknownChildren()
            autoPolymorphic = true
        }
        xmlDeclMode = XmlDeclMode.Charset
        xmlVersion = XmlVersion.XML10
        setIndent(2)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesProtoBuf(): ProtoBuf = ProtoBuf
}
