# 위젯 RemoteViews는 리플렉션으로 접근하므로 보존
-keep class com.stockwidget.app.StockWidgetProvider { *; }
-keep class com.stockwidget.app.StockWidgetRemoteViewsService { *; }
-keep class com.stockwidget.app.StockQuote { *; }
-keep class com.stockwidget.app.StockItem { *; }

# JSON 파싱 필드명 보존
-keepclassmembers class com.stockwidget.app.** {
    public *;
}

# Android 기본 규칙
-keepattributes *Annotation*
-dontwarn android.support.**
