# kotlinx.serialization：R8 会把生成的 $$serializer 当作无用代码删掉，
# 结果是运行期 SerializationException。以下是官方推荐的保留规则。
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 数据模型全部保留：它们只经由反射/生成代码访问
-keep class com.weatherwatch.core.model.** { *; }
