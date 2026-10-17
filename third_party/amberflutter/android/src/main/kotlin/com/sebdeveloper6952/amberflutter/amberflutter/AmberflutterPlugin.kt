package com.sebdeveloper6952.amberflutter.amberflutter

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sebdeveloper6952.amberflutter.amberflutter.models.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** AmberflutterPlugin */
class AmberflutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var _channel : MethodChannel
  private lateinit var _context : Context
  private var _activity: Activity? = null
  private lateinit var _result: MethodChannel.Result

  private val _intentRequestCode = 0

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    _channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.sebdeveloper6952.amberflutter")
    _channel.setMethodCallHandler(this)
    _context = flutterPluginBinding.applicationContext
  }

  fun isPackageInstalled(context: Context, target: String): Boolean {
    return context.packageManager.getInstalledApplications(0).find { info -> info.packageName == target } != null
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == nostrsignerUri) {
      _result = MethodResultWrapper(result)

      var paramsMap: HashMap<*, *>? = null
      if (call.arguments != null && call.arguments is HashMap<*, *>) {
        paramsMap = call.arguments as HashMap<*, *>
      }

      if (paramsMap == null) {
        Log.d("onMethodCall", "paramsMap is null")
        return
      }

      val requestType = paramsMap[intentExtraKeyType] as? String ?: ""
      val currentUser = paramsMap[intentExtraKeyCurrentUser] as? String ?: ""
      val pubKey = paramsMap[intentExtraKeyPubKey] as? String ?: ""
      val id = paramsMap[intentExtraKeyId] as? String ?: ""
      val uriData = paramsMap[intentExtraKeyUriData] as? String ?: ""
      val permissions = paramsMap[intentExtraKeyPermissions] as? String ?: ""

     val data = getDataFromContentResolver(
       requestType.uppercase(),
       arrayOf(uriData, pubKey, currentUser),
       _context.contentResolver,
     )
     if (!data.isNullOrEmpty()) {
       Log.d("onMethodCall", "content resolver got data")
       _result.success(data)
       return
     }

      val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
          "$nostrsignerUri:$uriData"
        )
      )

      intent.putExtra(intentExtraKeyType, requestType)
      intent.putExtra(intentExtraKeyCurrentUser, currentUser)
      intent.putExtra(intentExtraKeyPubKey, pubKey)
      intent.putExtra(intentExtraKeyId, id)
      intent.putExtra(intentExtraKeyPermissions, permissions)
      // intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

      _activity?.startActivityForResult(
        intent,
        _intentRequestCode
      )
    } else if (call.method == "isAppInstalled") {
        var paramsMap: HashMap<*, *>? = null
        if (call.arguments != null && call.arguments is HashMap<*, *>) {
            paramsMap = call.arguments as HashMap<*, *>
        }
        if (paramsMap == null) {
            return
        }
        var packageName: String? = paramsMap["packageName"] as? String ?: return
        val isInstalled: Boolean = isPackageInstalled(_context, packageName!!)
        result.success(isInstalled);
    } else {
      result.notImplemented()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
    if (requestCode == _intentRequestCode) {
      if (resultCode == Activity.RESULT_OK && intent != null) {
        val dataMap: HashMap<String, String?> = HashMap()
        if (intent.hasExtra(intentExtraKeySignature)) {
          val signature = intent.getStringExtra(intentExtraKeySignature)
          dataMap[intentExtraKeySignature] = signature
        }
        if (intent.hasExtra(intentExtraKeyId)) {
          val id = intent.getStringExtra(intentExtraKeyId)
          dataMap[intentExtraKeyId] = id
        }
        if (intent.hasExtra(intentExtraKeyEvent)) {
          val event = intent.getStringExtra(intentExtraKeyEvent)
          dataMap[intentExtraKeyEvent] = event
        }

        _result.success(dataMap)

        return true
      }
    }

    return false
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    _channel.setMethodCallHandler(null)
  }

  override fun onDetachedFromActivity() {
    _activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    _activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    _activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    _activity = null
  }

  /*
    Code taken from: https://github.com/0xchat-app/nostr-dart/blob/main/android/src/main/kotlin/com/oxchat/nostrcore/ChatcorePlugin.kt
   */
  private fun getDataFromContentResolver(
    type: String,
    uriData: Array<out String>,
    resolver: ContentResolver,
  ): HashMap<String, String?>? {
    try {
      resolver.query(
        Uri.parse("content://${amberPackageName}.$type"),
        uriData,
        null,
        null,
        null
      ).use {
        if (it == null) {
          Log.d("getDataFromResolver", "resolver query is NULL")
          return null
        }
        if (it.moveToFirst()) {
          val dataMap: HashMap<String, String?> = HashMap()
          val index = it.getColumnIndex("signature")
          if (index < 0) {
            Log.d("getDataFromResolver", "column 'signature' not found")
          } else {
            val signature = it.getString(index)
            dataMap["signature"] = signature
          }
          val indexJson = it.getColumnIndex("event")
          if (indexJson < 0) {
            Log.d("getDataFromResolver", "column 'event' not found")
          } else {
            val eventJson = it.getString(indexJson)
            dataMap["event"] = eventJson
          }

          return dataMap
        }
      }
    } catch (e: Exception) {
      Log.d("contentResolver", e.message ?: "unknown error")
      return null
    }
    return null
  }
}


private class MethodResultWrapper internal constructor(result: MethodChannel.Result) :
  MethodChannel.Result {
  private val methodResult: MethodChannel.Result
  private val handler: Handler

  init {
    methodResult = result
    handler = Handler(Looper.getMainLooper())
  }

  override fun success(result: Any?) {
    handler.post { methodResult.success(result) }
  }

  override fun error(
    errorCode: String, errorMessage: String?, errorDetails: Any?
  ) {
    handler.post { methodResult.error(errorCode, errorMessage, errorDetails) }
  }

  override fun notImplemented() {
    handler.post { methodResult.notImplemented() }
  }
}
